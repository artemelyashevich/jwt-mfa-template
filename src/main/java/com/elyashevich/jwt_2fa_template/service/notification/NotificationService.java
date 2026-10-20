package com.elyashevich.jwt_2fa_template.service.notification;

import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;

public interface NotificationService {

    void send(NotificationDto dto);
}
