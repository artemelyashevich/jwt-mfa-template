package com.elyashevich.jwt_2fa_template.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {

    private String requestId;
    private long expiresInSeconds;
    private long resendAvailableInSeconds;
}
