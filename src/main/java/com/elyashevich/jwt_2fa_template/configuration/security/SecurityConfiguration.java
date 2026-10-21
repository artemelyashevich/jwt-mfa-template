package com.elyashevich.jwt_2fa_template.configuration.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final RequestMatcher PROTECTED_URLS = new NegatedRequestMatcher(PublicUrls.PUBLIC_URLS);

    private final SecurityExceptionResponseWriter responseWriter;
    private final TokenAuthenticationProvider tokenAuthenticationProvider;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(tokenAuthenticationProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .exceptionHandling(exc -> exc
                .authenticationEntryPoint((req, res, ex) -> responseWriter.write(res, AppExceptionCode.EX401_TOKEN_MISSING))
                .accessDeniedHandler((req, res, ex) -> responseWriter.write(res, AppExceptionCode.EX403)))
            .authenticationProvider(tokenAuthenticationProvider)
            .addFilterBefore(new TokenAuthenticationFilter(PROTECTED_URLS, authenticationManager(), responseWriter),
                AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(req -> req
                .requestMatchers(PublicUrls.PUBLIC_URLS).permitAll()
                .requestMatchers(PROTECTED_URLS).authenticated()
                .anyRequest().denyAll())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
