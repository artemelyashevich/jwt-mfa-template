package com.elyashevich.jwt_2fa_template.dto.otp;


public record OtpConfirmation(
    Long userId,
    OtpType type,
    String target
) {

}
