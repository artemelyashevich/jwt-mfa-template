package com.elyashevich.jwt_2fa_template.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(

    @NotBlank(message = "Refresh-токен не должен быть пустым")
    String refreshToken
) {

}