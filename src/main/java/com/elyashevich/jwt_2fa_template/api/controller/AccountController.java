package com.elyashevich.jwt_2fa_template.api.controller;

import com.elyashevich.jwt_2fa_template.configuration.security.PublicUrls;
import com.elyashevich.jwt_2fa_template.dto.user.CurrentUserResponse;
import com.elyashevich.jwt_2fa_template.service.AuthenticationService;
import com.elyashevich.jwt_2fa_template.service.UserService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PublicUrls.ACCOUNT_TAG)
@RequiredArgsConstructor
public class AccountController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final UserSessionService userSessionService;

    @GetMapping("/me")
    public CurrentUserResponse findCurrentUser() {
        return CurrentUserResponse.from(userService.findById(userSessionService.getCurrentUser().getUserId()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authenticationService.logout();
    }
}
