package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.auth.CompleteRegistrationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.LoginResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RefreshTokenRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.RegisterPreparationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.UserLoginRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import lombok.NonNull;

public interface AuthenticationService {

    OtpResponse prepareRegister(RegisterPreparationRequest userRegistrationRequest, @NonNull UserLocale locale);

    LoginResponse completeRegistration(CompleteRegistrationRequest completeRegistrationRequest);

    /**
     * First login factor: checks the password and account status, then emails a one-time login code.
     * No tokens are issued until the code is confirmed via {@link #confirmLogin(CheckOtpRequest)}.
     */
    OtpResponse login(UserLoginRequest loginRequest);

    /**
     * Second login factor: verifies the emailed code issued by {@link #login(UserLoginRequest)} and issues tokens.
     * The account status is re-checked, since it may have changed while the code was pending.
     */
    LoginResponse confirmLogin(CheckOtpRequest checkOtpRequest);

    LoginResponse refresh(RefreshTokenRequest refreshTokenRequest);

    void logout();
}
