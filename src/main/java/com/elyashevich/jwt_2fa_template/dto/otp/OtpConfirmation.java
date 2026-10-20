package com.elyashevich.jwt_2fa_template.dto.otp;

import java.util.UUID;

public record OtpConfirmation(
    Long userId,
    OtpType type,
    String target
) {

}
