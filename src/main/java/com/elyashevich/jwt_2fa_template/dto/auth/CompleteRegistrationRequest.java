package com.elyashevich.jwt_2fa_template.dto.auth;

import com.elyashevich.jwt_2fa_template.annotation.validation.MaxUtf8Bytes;
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
    @MaxUtf8Bytes(value = 72, message = "Пароль не должен превышать 72 байта (кириллический символ занимает 2 байта)")
    private String password;

    @NotBlank(message = "Отображаемое имя не должно быть пустым")
    @Size(max = 100, message = "Отображаемое имя не должно превышать 100 символов")
    private String name;
}
