package com.elyashevich.jwt_2fa_template.repositroy;

import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import java.util.UUID;

public interface EventChannelProjection {

    UUID getEventId();

    NotificationChannel getChannel();
}
