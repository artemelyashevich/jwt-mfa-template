package com.elyashevich.jwt_2fa_template.dto.auth;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractOtpRequest {

    @NotNull
    private String confirmationToken;
}
