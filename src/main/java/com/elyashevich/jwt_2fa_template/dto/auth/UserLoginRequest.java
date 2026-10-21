package com.elyashevich.jwt_2fa_template.dto.auth;

import com.elyashevich.jwt_2fa_template.annotation.validation.MaxUtf8Bytes;
import com.elyashevich.jwt_2fa_template.util.EmailUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(

    @NotBlank(message = "Email не должен быть пустым")
    @Email(message = "Некорректный формат email")
    String email,

    @NotBlank(message = "Пароль не должен быть пустым")
    @MaxUtf8Bytes(value = 72, message = "Пароль не должен превышать 72 байта")
    String password
) {

    public UserLoginRequest {
        email = EmailUtil.normalize(email);
    }
}