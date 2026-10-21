package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private static final long MINIMUM_TOKEN_LIFETIME_SECONDS = 300;

    private final ApplicationProperties applicationProperties;

    private SecretKey accessTokenSecretKey;
    private SecretKey refreshTokenSecretKey;

    @PostConstruct
    public void init() {
        this.accessTokenSecretKey = Keys.hmacShaKeyFor(applicationProperties.getAuth().getAccessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshTokenSecretKey = Keys.hmacShaKeyFor(applicationProperties.getAuth().getRefreshSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generate(UserProfile user, JwtType jwtType) {
        long lifetime = getLifetime(jwtType);
        if (lifetime < MINIMUM_TOKEN_LIFETIME_SECONDS * 1000) {
            log.warn("Token lifetime {} is less than minimum {}ms", lifetime, MINIMUM_TOKEN_LIFETIME_SECONDS * 1000);
        }
        SecretKey secretKey = getKey(jwtType);
        Date now = new Date();
        Date expiry = new Date(now.getTime() + lifetime);
        log.debug("Generating {} token for user {}, lifetime: {}s", jwtType, user.getId(), lifetime / 1000);
        List<String> roles = user.getRoles().stream()
            .map(Role::getCode)
            .map(Enum::name)
            .sorted()
            .toList();
        return Jwts.builder()
            .setId(UUID.randomUUID().toString())
            .setSubject(user.getId().toString())
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    @Override
    public Long extractUserId(String token, JwtType jwtType) {
        Claims claims = getClaims(token, jwtType);
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        }
        try {
            return Long.parseLong(subject);
        } catch (IllegalArgumentException e) {
            log.error("Failed parse userId: {}", subject);
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        }
    }

    @Override
    public Date extractExpiration(String token, JwtType jwtType) {
        return extractClaim(token, jwtType, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, JwtType tokenType, Function<Claims, T> claimsResolver) {
        final Claims claims = getClaims(token, tokenType);
        T result = claimsResolver.apply(claims);
        if (result == null) {
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        }
        return result;
    }

    private Claims getClaims(String token, JwtType tokenType) {
        if (token == null || token.isBlank()) {
            throw new AppException(AppExceptionCode.EX401_TOKEN_MISSING);
        }
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getKey(tokenType))
                .build()
                .parseClaimsJws(token)
                .getBody();
            validateClaimsContent(claims);
            return claims;
        } catch (ExpiredJwtException e) {
            log.warn("Token has expired: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_EXPIRED);
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        } catch (IllegalArgumentException e) {
            log.error("Invalid JWT argument: {}", e.getMessage());
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during JWT parsing", e);
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        }
    }

    private void validateClaimsContent(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration != null && new Date().after(expiration)) {
            throw new AppException(AppExceptionCode.EX401_TOKEN_EXPIRED);
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AppException(AppExceptionCode.EX401_TOKEN_INVALID);
        }
    }

    private SecretKey getKey(JwtType type) {
        return JwtType.ACCESS.equals(type) ? accessTokenSecretKey : refreshTokenSecretKey;
    }

    private long getLifetime(JwtType type) {
        long seconds = JwtType.ACCESS.equals(type)
            ? applicationProperties.getAuth().getAccessTokenExpirationSeconds()
            : applicationProperties.getAuth().getRefreshTokenExpirationSeconds();
        if (seconds <= 0) {
            log.warn("Invalid expiration seconds configuration: {}", seconds);
            seconds = JwtType.ACCESS.equals(type) ? 3600 : 3600 * 24;
        }
        return seconds * 1000;
    }
}
