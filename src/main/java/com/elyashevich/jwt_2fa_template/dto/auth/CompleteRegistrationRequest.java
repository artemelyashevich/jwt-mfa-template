package com.elyashevich.jwt_2fa_template.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteRegistrationRequest extends AbstractOtpRequest {

    @NotBlank(message = "Пароль не должен быть пустым")
    @Size(min = 8, message = "Пароль должен быть не короче 8 символов")
    private String password;

    @NotBlank(message = "Отображаемое имя не должно быть пустым")
    @Size(max = 100, message = "Отображаемое имя не должно превышать 100 символов")
    private String name;
}
