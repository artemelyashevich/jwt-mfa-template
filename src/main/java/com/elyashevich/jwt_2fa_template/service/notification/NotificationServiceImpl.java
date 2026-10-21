package com.elyashevich.jwt_2fa_template.service.notification;

import com.elyashevich.jwt_2fa_template.dto.notification.DeliveryStatus;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationEventType;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.repository.NotificationDeliveryRepository;
import com.elyashevich.jwt_2fa_template.repository.UserProfileRepository;
import com.elyashevich.jwt_2fa_template.service.notification.channel.NotificationChannelStrategy;
import com.elyashevich.jwt_2fa_template.service.notification.template.NotificationTemplateRegistry;
import com.elyashevich.jwt_2fa_template.service.notification.template.NotificationTemplateRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationTemplateRegistry templateRegistry;
    private final NotificationDeliveryRepository deliveryRepository;
    private final UserProfileRepository userRepository;
    private final NotificationTemplateRenderer renderer;
    private final Map<NotificationChannel, NotificationChannelStrategy> strategies;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate writeTransactionTemplate;

    public NotificationServiceImpl(NotificationTemplateRegistry templateRegistry, NotificationDeliveryRepository deliveryRepository, UserProfileRepository userRepository,
        NotificationTemplateRenderer renderer, List<NotificationChannelStrategy> strategies, PlatformTransactionManager transactionManager) {
        this.templateRegistry = templateRegistry;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.renderer = renderer;
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(NotificationChannelStrategy::getChannel, Function.identity()));
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
    }


    @Override
    public void send(@NonNull NotificationDto dto) {
        DeliveryPlan plan = readTransactionTemplate.execute(status -> plan(dto, new SingleLookups()));
        if (plan == null || plan.deliveries().isEmpty()) {
            return;
        }
        renderAll(dto, plan);
        writeTransactionTemplate.executeWithoutResult(status -> save(plan));
    }

    private void renderAll(NotificationDto dto, DeliveryPlan plan) {
        Map<String, Object> variables = templateVariables(dto, plan.user(), plan.locale());
        plan.deliveries().forEach(planned -> render(dto, planned, variables, plan.locale()));
    }

    private void save(DeliveryPlan plan) {
        plan.deliveries().forEach(planned -> planned.strategy().save(planned.delivery()));
    }

    private DeliveryPlan plan(NotificationDto dto, DeliveryLookups lookups) {
        NotificationEventType eventType = dto.getEventType();
        UserProfile user = dto.getUserId() == null ? null : lookups.findUser(dto.getUserId());
        UserLocale locale = user == null ? UserLocale.RU : user.getLocale();
        List<PlannedDelivery> deliveries = new ArrayList<>();

        Set<NotificationChannel> channels = templateRegistry.findChannels(eventType).stream()
            .filter(channel -> dto.getChannels() == null || dto.getChannels().contains(channel))
            .collect(Collectors.toSet());
        if (channels.isEmpty()) {
            log.warn("No notification templates for event {}", eventType);
            return new DeliveryPlan(user, locale, deliveries);
        }

        channels.forEach(channel -> {
            NotificationChannelStrategy strategy = strategies.get(channel);
            if (strategy == null) {
                log.warn("No strategy for notification channel {}", channel);
                return;
            }
            if (lookups.isQueued(dto.getId(), channel)) {
                log.debug("Notification event {} via {} already queued", dto.getId(), channel);
                return;
            }
            String recipient = strategy.resolveRecipient(dto, user);
            if (recipient == null) {
                log.warn("No recipient for notification {} via {}, user {}", eventType, channel, dto.getUserId());
                return;
            }
            String templateName = templateRegistry.findTemplateName(eventType, channel, locale).orElseThrow();
            NotificationDelivery delivery = createDelivery(dto, strategy, recipient);
            deliveries.add(new PlannedDelivery(strategy, delivery, templateName));
        });
        return new DeliveryPlan(user, locale, deliveries);
    }

    private NotificationDelivery createDelivery(NotificationDto dto, NotificationChannelStrategy strategy, String recipient) {
        return NotificationDelivery.builder()
            .event(dto.getEventType())
            .eventId(dto.getId())
            .channel(strategy.getChannel())
            .userId(dto.getUserId())
            .recipient(recipient)
            .build();
    }

    private void render(NotificationDto dto, PlannedDelivery planned, Map<String, Object> variables, UserLocale locale) {
        NotificationDelivery delivery = planned.delivery();
        try {
            String subject = renderer.renderFragment(
                planned.templateName(), NotificationTemplateRenderer.SUBJECT_FRAGMENT, variables, locale.toLocale());
            delivery.setSubject(subject);
            delivery.setBody(planned.strategy().renderBody(planned.templateName(), subject, variables, locale.toLocale()));
        } catch (Exception e) {
            log.error("Failed to render template {} for event {}", planned.templateName(), dto.getId(), e);
            delivery.setBody("");
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setLastError("Template rendering failed: " + e.getMessage());
        }
    }

    private Map<String, Object> templateVariables(NotificationDto dto, UserProfile user, UserLocale locale) {
        Map<String, Object> variables = new HashMap<>(Optional.ofNullable(dto.getParams()).orElse(Map.of()));
        if (user != null) {
            Map<String, Object> userVariables = new HashMap<>();
            userVariables.put("id", user.getId());
            userVariables.put("email", user.getEmail());
            userVariables.put("displayName", user.getName());
            variables.put("user", userVariables);
        }
        variables.put("locale", locale.getCode());
        return variables;
    }

    private interface DeliveryLookups {

        UserProfile findUser(Long userId);

        boolean isQueued(UUID eventId, NotificationChannel channel);
    }

    private class SingleLookups implements DeliveryLookups {

        @Override
        public UserProfile findUser(Long userId) {
            return userRepository.findById(userId).orElse(null);
        }

        @Override
        public boolean isQueued(UUID eventId, NotificationChannel channel) {
            return deliveryRepository.existsByEventIdAndChannel(eventId, channel);
        }
    }

    private record DeliveryPlan(UserProfile user, UserLocale locale, List<PlannedDelivery> deliveries) {

    }

    private record PlannedDelivery(NotificationChannelStrategy strategy, NotificationDelivery delivery, String templateName) {

    }
}
