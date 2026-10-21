package com.elyashevich.jwt_2fa_template.configuration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private SecurityExceptionResponseWriter responseWriter;

    private TokenAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TokenAuthenticationFilter(req -> true, authenticationManager, responseWriter);
        request = new MockHttpServletRequest("GET", "/api/v1/account/me");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesRequestWithoutBearerTokenUnauthenticated() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(authenticationManager, responseWriter);
    }

    @Test
    void authenticatesValidToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("user", "token", null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }

    @Test
    void writesAppExceptionCodeForRejectedToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer revoked");
        when(authenticationManager.authenticate(any())).thenThrow(new AppException(AppExceptionCode.EX401_SESSION_NOT_FOUND));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        verify(responseWriter).write(response, AppExceptionCode.EX401_SESSION_NOT_FOUND);
    }

    @Test
    void writesUnauthorizedForAuthenticationException() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        verify(responseWriter).write(response, AppExceptionCode.EX401);
        verify(responseWriter, never()).write(response, AppExceptionCode.EX401_SESSION_NOT_FOUND);
    }
}
