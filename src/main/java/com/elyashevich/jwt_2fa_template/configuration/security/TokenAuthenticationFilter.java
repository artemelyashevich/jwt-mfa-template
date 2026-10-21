package com.elyashevich.jwt_2fa_template.configuration.security;

import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RequestMatcher protectedUrls;
    private final AuthenticationManager authenticationManager;
    private final SecurityExceptionResponseWriter responseWriter;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !protectedUrls.matches(request);
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.debug("[AUTH FILTER] No Bearer token found for {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(token, token));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AppException ex) {
            SecurityContextHolder.clearContext();
            log.warn("[AUTH FILTER] Authentication FAILED for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getAppExceptionCode());
            responseWriter.write(response, ex.getAppExceptionCode());
            return;
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("[AUTH FILTER] Authentication FAILED for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            responseWriter.write(response, AppExceptionCode.EX401);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
