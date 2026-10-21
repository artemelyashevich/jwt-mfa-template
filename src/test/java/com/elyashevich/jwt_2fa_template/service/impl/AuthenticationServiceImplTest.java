package com.elyashevich.jwt_2fa_template.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elyashevich.jwt_2fa_template.dto.auth.CompleteRegistrationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.dto.auth.LoginResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RefreshTokenRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.UserLoginRequest;
import com.elyashevich.jwt_2fa_template.dto.cache.OtpCache;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmation;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.entity.UserStatus;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.OtpService;
import com.elyashevich.jwt_2fa_template.service.RoleService;
import com.elyashevich.jwt_2fa_template.service.UserService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import com.elyashevich.jwt_2fa_template.service.notification.NotificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";

    @Mock
    private UserService userService;
    @Mock
    private RoleService roleService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;
    @Mock
    private JwtService jwtService;
    @Mock
    private OtpService otpService;
    @Mock
    private UserSessionService userSessionService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void loginRejectsUnknownEmailAfterCheckingPasswordAgainstDummyHash() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "StrongPass123")),
            AppExceptionCode.EX401_INVALID_CREDENTIALS);
        verify(passwordEncoder).matches(eq("StrongPass123"), any());
    }

    @Test
    void loginRejectsWrongPassword() {
        UserProfile user = user(UserStatus.ACTIVE);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "wrong")),
            AppExceptionCode.EX401_INVALID_CREDENTIALS);
        verify(jwtService, never()).generate(any(), any());
    }

    @Test
    void loginRejectsUnverifiedUserAsInvalidCredentials() {
        UserProfile user = user(UserStatus.VERIFICATION_REQUIRE);
        user.setPasswordHash(null);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "StrongPass123")),
            AppExceptionCode.EX401_INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsBlockedUserWithWrongPasswordAsInvalidCredentials() {
        UserProfile user = user(UserStatus.BLOCKED);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "wrong")),
            AppExceptionCode.EX401_INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsBlockedUserWithCorrectPassword() {
        UserProfile user = user(UserStatus.BLOCKED);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123", user.getPasswordHash())).thenReturn(true);

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "StrongPass123")),
            AppExceptionCode.EX403_USER_BLOCKED);
        verify(jwtService, never()).generate(any(), any());
    }

    @Test
    void loginSendsCodeWithoutIssuingTokens() {
        UserProfile user = user(UserStatus.ACTIVE);
        OtpResponse otp = new OtpResponse("request", 60, 60);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123", user.getPasswordHash())).thenReturn(true);
        when(otpService.prepare(USER_ID, OtpType.LOGIN)).thenReturn(otp);

        OtpResponse response = authenticationService.login(new UserLoginRequest(EMAIL, "StrongPass123"));

        assertThat(response).isSameAs(otp);
        verify(jwtService, never()).generate(any(), any());
        verify(userService, never()).recordLogin(any(), anyString(), any());
    }

    @Test
    void loginDoesNotSendCodeForWrongPassword() {
        UserProfile user = user(UserStatus.ACTIVE);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertAppException(() -> authenticationService.login(new UserLoginRequest(EMAIL, "wrong")),
            AppExceptionCode.EX401_INVALID_CREDENTIALS);
        verify(otpService, never()).prepare(any(), any());
    }

    @Test
    void confirmLoginIssuesTokensAndRecordsRefreshTokenHash() {
        UserProfile user = user(UserStatus.ACTIVE);
        CheckOtpRequest request = new CheckOtpRequest("request", "123456");
        when(otpService.verify(request, OtpType.LOGIN)).thenReturn(otpCache());
        when(userService.findById(USER_ID)).thenReturn(user);
        stubTokens(user, "access", "refresh");

        LoginResponse response = authenticationService.confirmLogin(request);

        assertThat(response).isEqualTo(new LoginResponse("access", "refresh"));
        verify(userService).recordLogin(eq(USER_ID), eq(sha256("refresh")), any(Instant.class));
    }

    @Test
    void confirmLoginRejectsInvalidCodeWithoutIssuingTokens() {
        CheckOtpRequest request = new CheckOtpRequest("request", "000000");
        when(otpService.verify(request, OtpType.LOGIN)).thenThrow(new AppException(AppExceptionCode.EX401_OTP_INVALID));

        assertAppException(() -> authenticationService.confirmLogin(request), AppExceptionCode.EX401_OTP_INVALID);
        verify(jwtService, never()).generate(any(), any());
    }

    @Test
    void confirmLoginRejectsUserBlockedWhileCodeWasPending() {
        CheckOtpRequest request = new CheckOtpRequest("request", "123456");
        when(otpService.verify(request, OtpType.LOGIN)).thenReturn(otpCache());
        when(userService.findById(USER_ID)).thenReturn(user(UserStatus.BLOCKED));

        assertAppException(() -> authenticationService.confirmLogin(request), AppExceptionCode.EX403_USER_BLOCKED);
        verify(jwtService, never()).generate(any(), any());
    }

    @Test
    void refreshRejectsTokenThatIsNotTheStoredOne() {
        UserProfile user = user(UserStatus.ACTIVE);
        user.setRefreshTokenHash(sha256("newer-refresh"));
        when(jwtService.extractUserId("old-refresh", JwtType.REFRESH)).thenReturn(USER_ID);
        stubUpdate(user);

        assertAppException(() -> authenticationService.refresh(new RefreshTokenRequest("old-refresh")),
            AppExceptionCode.EX401_REFRESH_TOKEN_REVOKED);
        verify(userService, never()).recordLogin(any(), anyString(), any());
    }

    @Test
    void refreshRejectsRevokedToken() {
        UserProfile user = user(UserStatus.ACTIVE);
        user.setRefreshTokenHash(null);
        when(jwtService.extractUserId("refresh", JwtType.REFRESH)).thenReturn(USER_ID);
        stubUpdate(user);

        assertAppException(() -> authenticationService.refresh(new RefreshTokenRequest("refresh")),
            AppExceptionCode.EX401_REFRESH_TOKEN_REVOKED);
    }

    @Test
    void refreshRotatesTokens() {
        UserProfile user = user(UserStatus.ACTIVE);
        user.setRefreshTokenHash(sha256("refresh"));
        when(jwtService.extractUserId("refresh", JwtType.REFRESH)).thenReturn(USER_ID);
        stubUpdate(user);
        stubTokens(user, "access-2", "refresh-2");

        LoginResponse response = authenticationService.refresh(new RefreshTokenRequest("refresh"));

        assertThat(response).isEqualTo(new LoginResponse("access-2", "refresh-2"));
        verify(userService).recordLogin(eq(USER_ID), eq(sha256("refresh-2")), any(Instant.class));
    }

    @Test
    void refreshRejectsBlockedUser() {
        UserProfile user = user(UserStatus.BLOCKED);
        user.setRefreshTokenHash(sha256("refresh"));
        when(jwtService.extractUserId("refresh", JwtType.REFRESH)).thenReturn(USER_ID);
        stubUpdate(user);

        assertAppException(() -> authenticationService.refresh(new RefreshTokenRequest("refresh")),
            AppExceptionCode.EX403_USER_BLOCKED);
    }

    @Test
    void completeRegistrationRejectsAlreadyVerifiedEmail() {
        UserProfile user = user(UserStatus.ACTIVE);
        CompleteRegistrationRequest request = new CompleteRegistrationRequest("StrongPass123", "Ivan");
        request.setConfirmationToken("confirmation");
        when(otpService.consumeConfirmation("confirmation", OtpType.EMAIL_VERIFICATION))
            .thenReturn(new OtpConfirmation(USER_ID, OtpType.EMAIL_VERIFICATION, null));
        stubUpdate(user);

        assertAppException(() -> authenticationService.completeRegistration(request), AppExceptionCode.EX400_EMAIL_ALREADY_VERIFIED);
        verify(jwtService, never()).generate(any(), any());
    }

    private static UserProfile user(UserStatus status) {
        return UserProfile.builder()
            .id(USER_ID)
            .email(EMAIL)
            .name("Ivan")
            .passwordHash("hash")
            .status(status)
            .emailVerifiedAt(status == UserStatus.VERIFICATION_REQUIRE ? null : Instant.now())
            .build();
    }

    private static OtpCache otpCache() {
        return OtpCache.builder().userId(USER_ID).type(OtpType.LOGIN).requestId("request").otp("123456").build();
    }

    @SuppressWarnings("unchecked")
    private void stubUpdate(UserProfile user) {
        when(userService.update(eq(USER_ID), any())).thenAnswer(invocation -> {
            invocation.getArgument(1, Consumer.class).accept(user);
            return user;
        });
    }

    private void stubTokens(UserProfile user, String accessToken, String refreshToken) {
        when(jwtService.generate(user, JwtType.ACCESS)).thenReturn(accessToken);
        when(jwtService.generate(user, JwtType.REFRESH)).thenReturn(refreshToken);
    }

    private static void assertAppException(Runnable action, AppExceptionCode code) {
        assertThatThrownBy(action::run)
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getAppExceptionCode())
            .isEqualTo(code);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
