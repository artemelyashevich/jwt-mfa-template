package com.elyashevich.jwt_2fa_template.dto.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckOtpRequest(

    @NotBlank(message = "requestId не должен быть пустым")
    String requestId,

    @NotBlank(message = "Код не должен быть пустым")
    @Pattern(regexp = "\\d{6}", message = "Код должен состоять из 6 цифр")
    String code
) {

}