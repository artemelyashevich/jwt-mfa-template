package com.elyashevich.jwt_2fa_template.dto.cache;

import com.elyashevich.jwt_2fa_template.dto.otp.OtpProvider;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;
import lombok.Builder;

@Builder
public record OtpCache(
    Long userId,
    OtpType type,
    String requestId,
    String otp,
    OtpProvider otpProvider,
    String target
) {

}