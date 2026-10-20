package com.elyashevich.jwt_2fa_template.dto.otp;

public record OtpConfirmationResponse(

    String confirmationToken,

    OtpType type,

    long expiresInSeconds
) {

}