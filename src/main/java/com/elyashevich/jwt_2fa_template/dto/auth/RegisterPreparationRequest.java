package com.elyashevich.jwt_2fa_template.dto.auth;

import com.elyashevich.jwt_2fa_template.util.EmailUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPreparationRequest(
    @NotBlank(message = "Email не должен быть пустым")
    @Email(message = "Некорректный формат email")
    @Size(max = 320, message = "Email не должен превышать 320 символов")
    String email
) {
    public RegisterPreparationRequest {
        email = EmailUtil.normalize(email);
    }
}
