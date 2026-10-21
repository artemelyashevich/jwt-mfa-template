package com.elyashevich.jwt_2fa_template.api.controller;

import com.elyashevich.jwt_2fa_template.configuration.security.PublicUrls;
import com.elyashevich.jwt_2fa_template.dto.auth.CompleteRegistrationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.LoginResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RefreshTokenRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.RegisterPreparationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.UserLoginRequest;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.service.AuthenticationService;
import com.elyashevich.jwt_2fa_template.util.HttpHeaderUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PublicUrls.AUTH_TAG)
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/registration")
    public OtpResponse register(
        @RequestBody @Valid RegisterPreparationRequest request,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        UserLocale locale = UserLocale.fromCode(HttpHeaderUtil.getLocale(acceptLanguage).getLanguage());
        return authenticationService.prepareRegister(request, locale);
    }

    @PostMapping("/registration/complete")
    public LoginResponse completeRegistration(@RequestBody @Valid CompleteRegistrationRequest request) {
        return authenticationService.completeRegistration(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid UserLoginRequest request) {
        return authenticationService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authenticationService.refresh(request);
    }
}
