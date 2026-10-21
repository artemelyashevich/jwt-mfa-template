package com.elyashevich.jwt_2fa_template.configuration.security;

import com.elyashevich.jwt_2fa_template.dto.auth.AuthenticatedUser;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.dto.cache.UserSessionCache;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.entity.UserStatus;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    private final JwtService jwtService;
    private final UserSessionService userSessionService;

    @Override
    protected void additionalAuthenticationChecks(
        @NonNull UserDetails userDetails,
        @NonNull UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
    }

    @NonNull
    @Override
    protected UserDetails retrieveUser(
        @NonNull String username,
        @NonNull UsernamePasswordAuthenticationToken authentication
    ) throws AuthenticationException {
        String accessToken = String.valueOf(authentication.getCredentials());
        Long userId = jwtService.extractUserId(accessToken, JwtType.ACCESS);
        UserSessionCache session = userSessionService.findActiveSession(userId, accessToken).orElseThrow(() -> {
            log.debug("Access token of user {} is not the active one: logged out or replaced", userId);
            return new AppException(AppExceptionCode.EX401_SESSION_NOT_FOUND);
        });
        if (session.getStatus() != UserStatus.ACTIVE) {
            log.warn("Access token of user {} rejected: status {}", userId, session.getStatus());
            throw new AppException(AppExceptionCode.EX403_USER_BLOCKED);
        }
        Set<SimpleGrantedAuthority> authorities = session.getRoleCodes().stream()
            .map(RoleCode::asAuthority)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
        return new AuthenticatedUser(session, authorities);
    }
}
