package com.elyashevich.jwt_2fa_template.configuration.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ApplicationProperties applicationProperties;
    private final SecurityExceptionResponseWriter responseWriter;
    private final TokenAuthenticationProvider tokenAuthenticationProvider;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(tokenAuthenticationProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(applicationProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT_LANGUAGE));
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment) {
        RequestMatcher publicUrls = environment.matchesProfiles("local")
            && environment.getProperty("spring.graphql.graphiql.enabled", Boolean.class, false)
            ? new OrRequestMatcher(PublicUrls.PUBLIC_URLS, PublicUrls.GRAPHIQL)
            : PublicUrls.PUBLIC_URLS;
        RequestMatcher protectedUrls = new NegatedRequestMatcher(publicUrls);
        http
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .exceptionHandling(exc -> exc
                .authenticationEntryPoint((req, res, ex) -> responseWriter.write(res, AppExceptionCode.EX401_TOKEN_MISSING))
                .accessDeniedHandler((req, res, ex) -> responseWriter.write(res, AppExceptionCode.EX403)))
            .authenticationProvider(tokenAuthenticationProvider)
            .addFilterBefore(new TokenAuthenticationFilter(protectedUrls, authenticationManager(), responseWriter),
                AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(req -> req
                .requestMatchers(publicUrls).permitAll()
                .requestMatchers(protectedUrls).authenticated()
                .anyRequest().denyAll())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
