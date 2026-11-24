package com.elyashevich.jwt_2fa_template.dto.cache;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmation;
import com.elyashevich.jwt_2fa_template.entity.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

@Getter
@RequiredArgsConstructor
public enum CacheType {

    ROLE(new TypeReference<Role>() {
    }),
    USER_SESSION(new TypeReference<UserSessionCache>() {
    }),
    USER_ACTIVE_TOKEN(new TypeReference<String>() {
    }),
    OTP_CACHE(new TypeReference<OtpCache>() {
    }),
    OTP_ACTIVE_REQUEST(new TypeReference<String>() {
    }),
    OTP_RESEND_COOLDOWN(new TypeReference<String>() {
    }),
    OTP_ATTEMPTS(new TypeReference<Long>() {
    }),
    OTP_DAILY_REQUESTS(new TypeReference<Long>() {
    }),
    OTP_DAILY_FAILURES(new TypeReference<Long>() {
    }),
    OTP_CONFIRMATION(new TypeReference<OtpConfirmation>() {
    }),
    CHECKERS_GAME_SNAPSHOT(new TypeReference<CheckersGameSnapshot>() {
    });

    private final TypeReference<?> type;

}
