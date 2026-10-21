package com.elyashevich.jwt_2fa_template.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elyashevich.jwt_2fa_template.api.controller.exception.GlobalExceptionController;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RegisterPreparationRequest;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.AuthenticationService;
import com.elyashevich.jwt_2fa_template.service.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private I18nService i18nService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authenticationService))
            .setControllerAdvice(new GlobalExceptionController(i18nService))
            .build();
    }

    @Test
    void loginRejectsInvalidBodyWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details.email").exists())
            .andExpect(jsonPath("$.details.password").exists());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void loginRejectsPasswordLongerThan72Bytes() throws Exception {
        String password = "пароль".repeat(7);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void loginMapsAppExceptionToItsStatus() throws Exception {
        when(authenticationService.login(any())).thenThrow(new AppException(AppExceptionCode.EX401_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"StrongPass123\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.details.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registrationUsesLocaleFromAcceptLanguageAndNormalizesEmail() throws Exception {
        when(authenticationService.prepareRegister(any(), eq(UserLocale.BE))).thenReturn(new OtpResponse("request", 60, 60));

        mockMvc.perform(post("/api/v1/auth/registration")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "be-BY,be;q=0.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" User@Example.COM \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("request"));
        verify(authenticationService).prepareRegister(new RegisterPreparationRequest("user@example.com"), UserLocale.BE);
    }
}
