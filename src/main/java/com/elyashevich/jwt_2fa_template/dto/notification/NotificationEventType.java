package com.elyashevich.jwt_2fa_template.dto.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationEventType {

    EMAIL_VERIFICATION(NotificationScope.MANDATORY),
    LOGIN_CODE(NotificationScope.MANDATORY),
    WELCOME(NotificationScope.ACCOUNT);

    private final NotificationScope scope;

    public boolean isMandatory() {
        return scope == NotificationScope.MANDATORY;
    }
}