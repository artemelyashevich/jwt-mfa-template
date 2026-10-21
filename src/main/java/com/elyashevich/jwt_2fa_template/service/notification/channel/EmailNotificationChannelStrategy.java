package com.elyashevich.jwt_2fa_template.service.notification.channel;

import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.repository.NotificationDeliveryRepository;
import com.elyashevich.jwt_2fa_template.service.notification.template.NotificationTemplateRenderer;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationChannelStrategy implements NotificationChannelStrategy {

    private final NotificationTemplateRenderer renderer;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String resolveRecipient(NotificationDto dto, UserProfile user) {
        if (dto.getReceiver() != null) {
            return dto.getReceiver();
        }
        return user == null ? null : user.getEmail();
    }

    @Override
    public String renderBody(String templateName, String subject, Map<String, Object> variables, Locale locale) {
        return renderer.renderEmail(templateName, subject, variables, locale);
    }

    @Override
    public void save(NotificationDelivery delivery) {
        notificationDeliveryRepository.save(delivery);
    }
}
