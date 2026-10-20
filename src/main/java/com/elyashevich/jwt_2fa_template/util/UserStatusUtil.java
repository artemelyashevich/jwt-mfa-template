package com.elyashevich.jwt_2fa_template.util;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class UserStatusUtil {

    public static void ensureCanLogin(UserProfile user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case VERIFICATION_REQUIRE -> {
                log.warn("Login rejected for user {}: email is not verified", user.getId());
                throw new AppException(AppExceptionCode.EX403_EMAIL_NOT_VERIFIED);
            }
            case BLOCKED -> {
                log.warn("Login rejected for user {}: status {}", user.getId(), user.getStatus());
                throw new AppException(AppExceptionCode.EX403_USER_BLOCKED);
            }
        }
    }
}
