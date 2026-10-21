package com.elyashevich.jwt_2fa_template.service.notification.dispatch;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.configuration.AsyncConfiguration;
import com.elyashevich.jwt_2fa_template.dto.notification.DeliveryStatus;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import com.elyashevich.jwt_2fa_template.repository.NotificationDeliveryRepository;
import com.elyashevich.jwt_2fa_template.service.notification.sender.NotificationSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class NotificationDispatcher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final NotificationDeliveryRepository deliveryRepository;
    private final ApplicationProperties.Notification properties;
    private final TransactionTemplate transactionTemplate;
    private final Map<NotificationChannel, NotificationSender> senders;
    private final Executor sendExecutor;

    public NotificationDispatcher(NotificationDeliveryRepository deliveryRepository,
        ApplicationProperties applicationProperties,
        TransactionTemplate transactionTemplate,
        List<NotificationSender> senders,
        @Qualifier(AsyncConfiguration.NOTIFICATION_SEND_EXECUTOR) Executor sendExecutor) {
        this.deliveryRepository = deliveryRepository;
        this.properties = applicationProperties.getNotification();
        this.transactionTemplate = transactionTemplate;
        this.senders = senders.stream()
            .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
        this.sendExecutor = sendExecutor;
    }

    private static String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public void dispatch() {
        List<NotificationDelivery> deliveries;
        do {
            deliveries = claimDueDeliveries();
            CompletableFuture.allOf(deliveries.stream()
                .map(delivery -> CompletableFuture.runAsync(() -> {
                    deliver(delivery);
                    saveResult(delivery);
                }, sendExecutor))
                .toArray(CompletableFuture[]::new)).join();
        } while (deliveries.size() == properties.getBatchSize());
    }

    private List<NotificationDelivery> claimDueDeliveries() {
        List<NotificationDelivery> claimed = transactionTemplate.execute(status -> {
            List<NotificationDelivery> due = deliveryRepository.lockDueDeliveries(properties.getBatchSize());
            Instant leaseUntil = Instant.now().plus(properties.getLeaseDuration());
            due.forEach(delivery -> {
                delivery.setAttempts(delivery.getAttempts() + 1);
                delivery.setNextAttemptAt(leaseUntil);
            });
            return due;
        });
        return claimed == null ? List.of() : claimed;
    }

    private void saveResult(NotificationDelivery delivery) {
        transactionTemplate.execute(status -> deliveryRepository.updateResult(
            delivery.getId(),
            delivery.getStatus(),
            delivery.getNextAttemptAt(),
            delivery.getLastError(),
            delivery.getSentAt()));
    }

    public void cleanup() {
        Instant before = Instant.now().minus(properties.getRetention());
        Integer deleted = transactionTemplate.execute(status -> deliveryRepository.deleteProcessedBefore(before));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} processed notification deliveries older than {}", deleted, before);
        }
    }

    private void deliver(NotificationDelivery delivery) {
        try {
            NotificationSender sender = senders.get(delivery.getChannel());
            if (sender == null) {
                throw new IllegalStateException("No sender for channel " + delivery.getChannel());
            }
            sender.send(delivery);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setLastError(null);
            log.debug("Notification delivery {} sent", delivery);
        } catch (Exception e) {
            delivery.setLastError(truncate(String.valueOf(e)));
            if (delivery.getAttempts() >= properties.getMaxAttempts()) {
                delivery.setStatus(DeliveryStatus.FAILED);
                log.error("Notification delivery {} failed after {} attempts", delivery, delivery.getAttempts(), e);
            } else {
                delivery.setNextAttemptAt(Instant.now().plus(retryDelay(delivery.getAttempts())));
                log.warn("Notification delivery {} failed, will retry: {}", delivery, e.toString());
            }
        }
    }

    private Duration retryDelay(int attempts) {
        return properties.getRetryDelay().multipliedBy(1L << Math.min(attempts - 1, 10));
    }

}
