package com.elyashevich.jwt_2fa_template.service.notification.sender;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final ApplicationProperties applicationProperties;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationDelivery notificationDelivery) {
        try {
            String subject = notificationDelivery.getSubject() == null ? "" : notificationDelivery.getSubject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(applicationProperties.getNotification().getEmailFrom());
            helper.setTo(notificationDelivery.getRecipient());
            helper.setSubject(subject);
            helper.setText(notificationDelivery.getBody(), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new AppException(AppExceptionCode.EX500);
        }
    }
}
