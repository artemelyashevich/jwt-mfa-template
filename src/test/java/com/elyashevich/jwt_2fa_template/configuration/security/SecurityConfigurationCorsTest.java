package com.elyashevich.jwt_2fa_template.configuration.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@ExtendWith(MockitoExtension.class)
class SecurityConfigurationCorsTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:3000";

    @Mock
    private SecurityExceptionResponseWriter responseWriter;
    @Mock
    private TokenAuthenticationProvider tokenAuthenticationProvider;

    private CorsConfigurationSource source;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getCors().setAllowedOrigins(List.of(FRONTEND_ORIGIN));
        source = new SecurityConfiguration(properties, responseWriter, tokenAuthenticationProvider).corsConfigurationSource();
    }

    @Test
    void allowsConfiguredOriginWithAuthorizationHeader() {
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/account/me"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin(FRONTEND_ORIGIN)).isEqualTo(FRONTEND_ORIGIN);
        assertThat(configuration.checkOrigin("http://evil.example")).isNull();
        assertThat(configuration.checkHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)))
            .containsExactlyInAnyOrder(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE);
        assertThat(configuration.getAllowCredentials()).isNull();
    }

    @Test
    void answersPreflightWithoutPassingItDownTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/account/me");
        request.addHeader(HttpHeaders.ORIGIN, FRONTEND_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response,
            (req, res) -> {
                throw new AssertionError("Preflight must not reach token authentication");
            });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONTEND_ORIGIN);
    }

    @Test
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://evil.example");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
