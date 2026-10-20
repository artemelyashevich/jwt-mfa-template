package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.cache.OtpCache;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmation;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmationResponse;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;

public interface OtpService {
    OtpResponse prepare(Long userId, OtpType type);

    OtpCache verify(CheckOtpRequest checkOtpRequest, OtpType otpType);

    OtpConfirmationResponse confirm(CheckOtpRequest checkOtpRequest);

    OtpConfirmation consumeConfirmation(String confirmationToken, OtpType type);
}
