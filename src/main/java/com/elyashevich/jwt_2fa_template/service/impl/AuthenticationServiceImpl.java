package com.elyashevich.jwt_2fa_template.service.impl;

import static com.elyashevich.jwt_2fa_template.util.UserStatusUtil.ensureCanLogin;

import com.elyashevich.jwt_2fa_template.annotation.BusinessLock;
import com.elyashevich.jwt_2fa_template.dto.auth.CompleteRegistrationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.dto.auth.LoginResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.auth.RefreshTokenRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.RegisterPreparationRequest;
import com.elyashevich.jwt_2fa_template.dto.auth.UserLoginRequest;
import com.elyashevich.jwt_2fa_template.dto.cache.LockerType;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationEventType;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.entity.UserStatus;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.AuthenticationService;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.OtpService;
import com.elyashevich.jwt_2fa_template.service.RoleService;
import com.elyashevich.jwt_2fa_template.service.UserService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import com.elyashevich.jwt_2fa_template.service.notification.NotificationService;
import com.elyashevich.jwt_2fa_template.util.TransactionUtil;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final UserSessionService userSessionService;

    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @BusinessLock(type = LockerType.REGISTER, key = "#userRegistrationRequest.email")
    public OtpResponse prepareRegister(RegisterPreparationRequest userRegistrationRequest, @NonNull UserLocale locale) {
        log.debug("Registration requested for email: {}", userRegistrationRequest.email());
        Optional<UserProfile> existingUser = userService.findByEmail(userRegistrationRequest.email());
        UserProfile user;
        if (existingUser.isPresent()) {
            user = userService.update(existingUser.get().getId(), u -> {
                if (u.isEmailVerified()) {
                    log.warn("Registration rejected: user with this email already exists and email is verified");
                    throw new AppException(AppExceptionCode.EX409_USER_ALREADY_EXISTS);
                }
                u.setLocale(locale);
            });
        } else {
            user = userService.save(UserProfile.builder()
                .email(userRegistrationRequest.email())
                .status(UserStatus.VERIFICATION_REQUIRE)
                .locale(locale)
                .build());
        }
        OtpResponse otp = otpService.prepare(user.getId(), OtpType.EMAIL_VERIFICATION);
        log.debug("Email verification code sent to user {}", user.getId());
        return otp;
    }

    @Override
    @Transactional
    public LoginResponse completeRegistration(CompleteRegistrationRequest completeRegistrationRequest) {
        Long userId = otpService.consumeConfirmation(completeRegistrationRequest.getConfirmationToken(), OtpType.EMAIL_VERIFICATION)
            .userId();
        String passwordHash = passwordEncoder.encode(completeRegistrationRequest.getPassword());
        UserProfile user = userService.update(userId, u -> {
            if (u.isEmailVerified()) {
                log.warn("Registration completion rejected for user {}: email already verified", userId);
                throw new AppException(AppExceptionCode.EX400_EMAIL_ALREADY_VERIFIED);
            }
            u.setPasswordHash(passwordHash);
            u.setName(completeRegistrationRequest.getName());
            u.setStatus(UserStatus.ACTIVE);
            u.addRole(roleService.findByRoleCode(RoleCode.USER));
            u.setEmailVerifiedAt(Instant.now());
        });
        LoginResponse loginResponse = issueTokens(user);
        notificationService.send(NotificationDto.builder()
            .eventType(NotificationEventType.WELCOME)
            .userId(user.getId())
            .build());
        log.debug("Registration completed for user {}", userId);
        return loginResponse;
    }

    @Override
    @Transactional
    @BusinessLock(type = LockerType.LOGIN, key = "#loginRequest.email")
    public OtpResponse login(UserLoginRequest loginRequest) {
        log.debug("Login attempt for user with email: {}", loginRequest.email());
        Optional<UserProfile> found = userService.findByEmail(loginRequest.email());
        String passwordHash = found.map(UserProfile::getPasswordHash).orElse(null);
        if (!passwordMatches(loginRequest.password(), passwordHash)) {
            log.warn("Login attempt failed for email {}: unknown user, registration not completed or password mismatch",
                loginRequest.email());
            throw new AppException(AppExceptionCode.EX401_INVALID_CREDENTIALS);
        }
        UserProfile user = found.orElseThrow();
        ensureCanLogin(user);
        OtpResponse otp = otpService.prepare(user.getId(), OtpType.LOGIN);
        log.debug("Login code sent to user {}", user.getId());
        return otp;
    }

    @Override
    @Transactional
    public LoginResponse confirmLogin(CheckOtpRequest checkOtpRequest) {
        Long userId = otpService.verify(checkOtpRequest, OtpType.LOGIN).userId();
        UserProfile user = userService.findById(userId);
        ensureCanLogin(user);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.refreshToken();
        Long userId = jwtService.extractUserId(refreshToken, JwtType.REFRESH);
        UserProfile user = userService.update(userId, u -> {
            if (u.getRefreshTokenHash() == null || !refreshTokenMatches(refreshToken, u.getRefreshTokenHash())) {
                log.warn("Refresh attempt failed for user {}: token is stale, revoked or does not match", userId);
                throw new AppException(AppExceptionCode.EX401_REFRESH_TOKEN_REVOKED);
            }
            ensureCanLogin(u);
        });
        LoginResponse loginResponse = issueTokens(user);
        log.debug("Tokens refreshed for user {}", userId);
        return loginResponse;
    }

    @Override
    @Transactional
    @BusinessLock(type = LockerType.ACCOUNT, key = BusinessLock.CURRENT_USER_KEY)
    public void logout() {
        Long userId = userSessionService.getCurrentUser().getUserId();
        userService.update(userId, u -> u.setRefreshTokenHash(null));
        userSessionService.invalidateSession(userId);
        SecurityContextHolder.clearContext();
        log.debug("User {} logged out", userId);
    }

    private LoginResponse issueTokens(UserProfile user) {
        LoginResponse loginResponse = login(user);
        userService.recordLogin(user.getId(), hashRefreshToken(loginResponse.refreshToken()), Instant.now());
        createSession(loginResponse, user);
        return loginResponse;
    }

    private void createSession(LoginResponse login, UserProfile user) {
        TransactionUtil.afterCommit(() -> {
            userSessionService.create(login.accessToken(), user);
            log.debug("User {} logged in", user.getEmail());
        });
    }

    private LoginResponse login(UserProfile user) {
        String accessToken = jwtService.generate(user, JwtType.ACCESS);
        String refreshToken = jwtService.generate(user, JwtType.REFRESH);
        return new LoginResponse(accessToken, refreshToken);
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        boolean matches = passwordEncoder.matches(rawPassword, passwordHash == null ? dummyPasswordHash : passwordHash);
        return passwordHash != null && matches;
    }

    private static boolean refreshTokenMatches(String refreshToken, String storedHash) {
        return MessageDigest.isEqual(
            hashRefreshToken(refreshToken).getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hashRefreshToken(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
