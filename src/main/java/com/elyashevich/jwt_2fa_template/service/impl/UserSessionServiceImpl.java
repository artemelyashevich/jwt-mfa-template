package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.dto.auth.AuthenticatedUser;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.dto.cache.CacheType;
import com.elyashevich.jwt_2fa_template.dto.cache.LocalSessionCache;
import com.elyashevich.jwt_2fa_template.dto.cache.UserSessionCache;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import com.elyashevich.jwt_2fa_template.util.HttpHeaderUtil;
import com.elyashevich.jwt_2fa_template.util.TransactionUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

    private static final long MINIMUM_TTL_SECONDS = 60;

    private final JwtService jwtService;
    private final HttpServletRequest httpServletRequest;
    private final CacheService cacheService;
    private final LocalSessionCache localSessionCache;

    @Override
    public UserSessionCache getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.getSession();
        }
        String token = HttpHeaderUtil.getToken(httpServletRequest);
        Long userId = jwtService.extractUserId(token, JwtType.ACCESS);
        return findActiveSession(userId, token)
            .orElseThrow(() -> new AppException(AppExceptionCode.EX401_SESSION_NOT_FOUND));
    }

    @Override
    public Optional<UserSessionCache> getCurrentUserSafety() {
        if (httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(getCurrentUser());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateSession(UserProfile userProfile) {
        UserSessionCache userSession = UserSessionCache.of(userProfile);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionUtil.afterCommit(() -> replaceExistingSession(userSession));
        } else {
            replaceExistingSession(userSession);
        }
    }

    @Override
    public void invalidateSession(Long userId) {
        localSessionCache.evict(userId);
        try {
            cacheService.get(CacheType.USER_ACTIVE_TOKEN, userId)
                .ifPresent(previousToken -> {
                    try {
                        cacheService.evict(CacheType.USER_SESSION, userId);
                        cacheService.evict(CacheType.USER_ACTIVE_TOKEN, userId);
                    } catch (Exception e) {
                        log.warn("Failed to evict session for user id: {}", userId, e);
                    }
                });
        } catch (Exception e) {
            log.warn("Failed to retrieve session token for user id: {}", userId, e);
        }
    }

    @Override
    public UserSessionCache create(String token, UserProfile userProfile) {
        Long userId = jwtService.extractUserId(token, JwtType.ACCESS);
        Duration ttl = resolveTtl(token);
        UserSessionCache userSession = UserSessionCache.of(userProfile);
        invalidateSession(userId);
        cacheService.put(CacheType.USER_SESSION, userId, userSession, ttl);
        cacheService.put(CacheType.USER_ACTIVE_TOKEN, userId, token, ttl);
        return userSession;
    }

    private void replaceExistingSession(UserSessionCache userSession) {
        String userId = userSession.getUserId().toString();
        Duration ttl = cacheService.getRemainingTtl(CacheType.USER_SESSION, userId);
        if (ttl.isPositive()) {
            cacheService.put(CacheType.USER_SESSION, userId, userSession, ttl);
            localSessionCache.evict(userSession.getUserId());
        }
    }

    private Optional<UserSessionCache> findActiveSession(Long userId, String token) {
        Optional<UserSessionCache> local = localSessionCache.find(userId, token);
        if (local.isPresent()) {
            return local;
        }
        long version = localSessionCache.version();
        List<Optional<?>> entries = cacheService.getAll(userId.toString(), CacheType.USER_ACTIVE_TOKEN, CacheType.USER_SESSION);
        boolean active = entries.get(0)
            .map(activeToken -> MessageDigest.isEqual(
                ((String) activeToken).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)))
            .orElse(false);
        Optional<UserSessionCache> session = active ? entries.get(1).map(UserSessionCache.class::cast) : Optional.empty();
        session.ifPresent(found -> localSessionCache.put(userId, token, found, version));
        return session;
    }

    private Duration resolveTtl(String token) {
        Instant expiration = jwtService.extractExpiration(token, JwtType.ACCESS).toInstant();
        Instant now = Instant.now();
        Duration ttl = Duration.between(now, expiration);
        if (ttl.isNegative()) {
            log.warn("Token has already expired! Expiration: {}, now: {}", expiration, now);
            throw new AppException(AppExceptionCode.EX401_TOKEN_EXPIRED);
        }
        if (ttl.getSeconds() < MINIMUM_TTL_SECONDS) {
            log.warn("Token lifetime is too short: {}s. Setting minimum TTL: {}s", ttl.getSeconds(), MINIMUM_TTL_SECONDS);
            return Duration.ofSeconds(MINIMUM_TTL_SECONDS);
        }
        return ttl;
    }
}
