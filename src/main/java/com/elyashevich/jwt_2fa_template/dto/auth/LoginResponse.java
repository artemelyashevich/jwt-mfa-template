package com.elyashevich.jwt_2fa_template.dto.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken
) {

}
