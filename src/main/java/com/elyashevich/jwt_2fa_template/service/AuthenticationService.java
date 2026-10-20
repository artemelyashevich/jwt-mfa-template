package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.auth.CompleteRegistrationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.LoginResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RefreshTokenRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.RegisterPreparationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.UserLoginRequest;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import lombok.NonNull;

public interface AuthenticationService {

    OtpResponse prepareRegister(RegisterPreparationRequest userRegistrationRequest, @NonNull UserLocale locale);

    LoginResponse completeRegistration(CompleteRegistrationRequest completeRegistrationRequest);

    LoginResponse login(UserLoginRequest loginRequest);

    LoginResponse refresh(RefreshTokenRequest refreshTokenRequest);

    void logout();
}
