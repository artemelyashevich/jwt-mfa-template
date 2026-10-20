package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.dto.auth.OtpResponse;
import com.elyashevich.jwt_2fa_template.dto.cache.CacheType;
import com.elyashevich.jwt_2fa_template.dto.cache.OtpCache;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationDto;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationEventType;
import com.elyashevich.jwt_2fa_template.dto.otp.CheckOtpRequest;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmation;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpConfirmationResponse;
import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.OtpService;
import com.elyashevich.jwt_2fa_template.service.notification.NotificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApplicationProperties applicationProperties;
    private final CacheService cacheService;
    private final NotificationService notificationService;

    @Override
    public OtpResponse prepare(Long userId, OtpType type) {
        String userOtpKey = userOtpKey(userId, type);
        ApplicationProperties.OtpLimit limit = applicationProperties.getCache().getOtpLimit(type);
        if (limit.isEnabled() && dailyFailuresExhausted(userOtpKey, limit)) {
            log.warn("OTP {} for user {} rejected: daily failure limit reached", type, userId);
            throw new AppException(AppExceptionCode.EX429_OTP_DAILY_LIMIT_EXCEEDED);
        }
        Duration resendCooldown = applicationProperties.getCache().getOtpResendCooldown();
        if (!cacheService.putIfAbsent(CacheType.OTP_RESEND_COOLDOWN, userOtpKey, Instant.now().toString(), resendCooldown)) {
            Duration retryAfter = cacheService.getRemainingTtl(CacheType.OTP_RESEND_COOLDOWN, userOtpKey);
            log.warn("OTP {} for user {} requested too often, retry after {}s", type, userId, retryAfter.toSeconds());
            throw new AppException(
                "OTP can be requested again in " + Math.max(1, retryAfter.toSeconds()) + " seconds",
                AppExceptionCode.EX429_OTP_RESEND_TOO_OFTEN
            );
        }

        if (limit.isEnabled()) {
            long dailyRequests = cacheService.increment(CacheType.OTP_DAILY_REQUESTS, userOtpKey, limit.getWindow());
            if (dailyRequests > limit.getDailyRequestLimit()) {
                log.warn("OTP {} for user {} rejected: {} requests within daily window", type, userId, dailyRequests);
                throw new AppException(AppExceptionCode.EX429_OTP_DAILY_LIMIT_EXCEEDED);
            }
        }

        cacheService.<String>get(CacheType.OTP_ACTIVE_REQUEST, userOtpKey)
            .ifPresent(this::invalidate);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String requestId = UUID.randomUUID().toString();
        OtpCache otpCache = OtpCache.builder()
            .userId(userId)
            .type(type)
            .requestId(requestId)
            .otp(otp)
            .build();
        Duration ttl = applicationProperties.getCache().getOtpSeconds();
        cacheService.put(CacheType.OTP_CACHE, requestId, otpCache, ttl);
        cacheService.put(CacheType.OTP_ACTIVE_REQUEST, userOtpKey, requestId, ttl);
        notificationService.send(NotificationDto.builder()
            .eventType(toNotificationEvent(type))
            .userId(userId)
            .params(Map.of(
                "code", otp,
                "ttlMinutes", Math.max(1, ttl.toMinutes())))
            .build());
        return new OtpResponse(requestId, ttl.toSeconds(), resendCooldown.toSeconds());
    }

    @Override
    public OtpCache verify(CheckOtpRequest checkOtpRequest, OtpType type) {
        String requestId = checkOtpRequest.requestId();
        OtpCache otpCache = cacheService.<OtpCache>get(CacheType.OTP_CACHE, requestId)
            .orElseThrow(() -> new AppException(AppExceptionCode.EX401_OTP_EXPIRED));
        ApplicationProperties.OtpLimit limit = applicationProperties.getCache().getOtpLimit(otpCache.type());
        if (limit.isEnabled() && dailyFailuresExhausted(userOtpKey(otpCache.userId(), otpCache.type()), limit)) {
            log.warn("OTP request {} of user {} rejected: daily failure limit reached", requestId, otpCache.userId());
            burn(otpCache);
            throw new AppException(AppExceptionCode.EX429_OTP_DAILY_LIMIT_EXCEEDED);
        }

        long attempts = registerAttempt(otpCache);
        int maxAttempts = applicationProperties.getCache().getOtpMaxAttempts();
        if (attempts > maxAttempts) {
            log.warn("OTP request {} of user {} rejected: {} attempts exceed the limit", requestId, otpCache.userId(), attempts);
            burn(otpCache);
            throw new AppException(AppExceptionCode.EX429_OTP_ATTEMPTS_EXCEEDED);
        }

        boolean belongs = type == otpCache.type();
        boolean codeMatches = MessageDigest.isEqual(
            checkOtpRequest.code().getBytes(StandardCharsets.UTF_8),
            otpCache.otp().getBytes(StandardCharsets.UTF_8)
        );
        if (belongs && codeMatches) {
            if (cacheService.getAndDelete(CacheType.OTP_CACHE, requestId).isEmpty()) {
                log.warn("OTP request {} of user {} was already used by a concurrent request", requestId, otpCache.userId());
                throw new AppException(AppExceptionCode.EX401_OTP_EXPIRED);
            }
            burn(otpCache);
            return otpCache;
        }

        if (!belongs) {
            log.warn("OTP request {} does not belong to type {}", requestId, type);
        }
        registerFailedAttempt(otpCache, limit, attempts, maxAttempts);
        throw new AppException(AppExceptionCode.EX401_OTP_INVALID);
    }

    @Override
    public OtpConfirmationResponse confirm(CheckOtpRequest checkOtpRequest) {
        OtpType type = cacheService.<OtpCache>get(CacheType.OTP_CACHE, checkOtpRequest.requestId())
            .map(OtpCache::type)
            .orElseThrow(() -> new AppException(AppExceptionCode.EX401_OTP_EXPIRED));
        if (!type.isConfirmable()) {
            log.warn("OTP request {} of type {} cannot be confirmed separately", checkOtpRequest.requestId(), type);
            throw new AppException(AppExceptionCode.EX400_OTP_CONFIRMATION_NOT_SUPPORTED);
        }
        OtpCache otpCache = verify(checkOtpRequest, type);

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String confirmationToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Duration ttl = applicationProperties.getCache().getOtpConfirmationTtl();
        cacheService.put(CacheType.OTP_CONFIRMATION, confirmationKey(confirmationToken), new OtpConfirmation(otpCache.userId(), type, otpCache.target()), ttl);
        log.debug("OTP {} confirmed for user {}", type, otpCache.userId());
        return new OtpConfirmationResponse(confirmationToken, type, ttl.toSeconds());
    }

    @Override
    public OtpConfirmation consumeConfirmation(String confirmationToken, OtpType type) {
        OtpConfirmation confirmation = cacheService.<OtpConfirmation>getAndDelete(CacheType.OTP_CONFIRMATION,
                confirmationKey(confirmationToken))
            .orElseThrow(() -> new AppException(AppExceptionCode.EX401_OTP_CONFIRMATION_INVALID));
        if (confirmation.type() != type) {
            log.warn("OTP confirmation of type {} used for {} by user {}", confirmation.type(), type, confirmation.userId());
            throw new AppException(AppExceptionCode.EX401_OTP_CONFIRMATION_INVALID);
        }
        return confirmation;
    }

    private static String confirmationKey(String confirmationToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(confirmationToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String userOtpKey(Long userId, OtpType type) {
        return userId.toString() + ":" + type;
    }

    private static NotificationEventType toNotificationEvent(OtpType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> NotificationEventType.EMAIL_VERIFICATION;
        };
    }

    private long registerAttempt(OtpCache otpCache) {
        Duration window = cacheService.getRemainingTtl(CacheType.OTP_CACHE, otpCache.requestId());
        return cacheService.increment(
            CacheType.OTP_ATTEMPTS, otpCache.requestId(), window.isZero() ? applicationProperties.getCache().getOtpSeconds() : window
        );
    }

    private void registerFailedAttempt(OtpCache otpCache, ApplicationProperties.OtpLimit limit, long attempts, int maxAttempts) {
        String requestId = otpCache.requestId();
        if (limit.isEnabled()) {
            long dailyFailures = cacheService.increment(
                CacheType.OTP_DAILY_FAILURES, userOtpKey(otpCache.userId(), otpCache.type()), limit.getWindow());
            if (dailyFailures >= limit.getDailyFailureLimit()) {
                log.warn("OTP request {} of user {} burned: {} failed attempts within daily window", requestId, otpCache.userId(), dailyFailures);
                burn(otpCache);
                throw new AppException(AppExceptionCode.EX429_OTP_DAILY_LIMIT_EXCEEDED);
            }
        }
        if (attempts >= maxAttempts) {
            log.warn("OTP request {} of user {} burned after {} failed attempts", requestId, otpCache.userId(), attempts);
            burn(otpCache);
            throw new AppException(AppExceptionCode.EX429_OTP_ATTEMPTS_EXCEEDED);
        }
    }

    private boolean dailyFailuresExhausted(String userOtpKey, ApplicationProperties.OtpLimit limit) {
        long failures = cacheService.<Long>get(CacheType.OTP_DAILY_FAILURES, userOtpKey).orElse(0L);
        return failures >= limit.getDailyFailureLimit();
    }

    private void burn(OtpCache otpCache) {
        invalidate(otpCache.requestId());
        cacheService.evict(CacheType.OTP_ACTIVE_REQUEST, userOtpKey(otpCache.userId(), otpCache.type()));
    }

    private void invalidate(String requestId) {
        cacheService.evict(CacheType.OTP_CACHE, requestId);
        cacheService.evict(CacheType.OTP_ATTEMPTS, requestId);
    }
}
