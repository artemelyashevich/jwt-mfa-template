package com.elyashevich.jwt_2fa_template.api.controller;

import com.elyashevich.jwt_2fa_template.configuration.security.PublicUrls;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmationResponse;
import com.elyashevich.jwt_2fa_template.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PublicUrls.AUTH_TAG + "/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/confirm")
    public OtpConfirmationResponse confirm(@RequestBody @Valid CheckOtpRequest request) {
        return otpService.confirm(request);
    }
}
