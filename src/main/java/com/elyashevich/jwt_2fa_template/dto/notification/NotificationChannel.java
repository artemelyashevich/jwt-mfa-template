package com.elyashevich.jwt_2fa_template.dto.notification;

public enum NotificationChannel {
    EMAIL;

    public String getTitleKey() {
        return "NOTIFICATION_CHANNEL_" + name();
    }
}
