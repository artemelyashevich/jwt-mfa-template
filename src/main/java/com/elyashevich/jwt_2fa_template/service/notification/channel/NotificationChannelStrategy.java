package com.elyashevich.jwt_2fa_template.service.notification.channel;

import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.util.Locale;
import java.util.Map;

public interface NotificationChannelStrategy {

    NotificationChannel getChannel();

    String resolveRecipient(NotificationDto dto, UserProfile user);

    String renderBody(String templateName, String subject, Map<String, Object> variables, Locale locale);

    void save(NotificationDelivery delivery);
}
