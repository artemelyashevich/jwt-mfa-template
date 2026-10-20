package com.elyashevich.jwt_2fa_template.service.notification.sender;

import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;

public interface NotificationSender {
    NotificationChannel getChannel();

    void send(NotificationDelivery notificationDelivery);
}
