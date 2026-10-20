package com.elyashevich.jwt_2fa_template.dto.otp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OtpType {
    EMAIL_VERIFICATION(true);

    private final boolean confirmable;
}