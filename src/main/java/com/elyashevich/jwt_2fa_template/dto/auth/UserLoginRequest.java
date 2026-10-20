package com.elyashevich.jwt_2fa_template.dto.auth;

import com.elyashevich.jwt_2fa_template.util.EmailUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(

    @NotBlank(message = "Email не должен быть пустым")
    @Email(message = "Некорректный формат email")
    String email,

    @NotBlank(message = "Пароль не должен быть пустым")
    String password
) {

    public UserLoginRequest {
        email = EmailUtil.normalize(email);
    }
}