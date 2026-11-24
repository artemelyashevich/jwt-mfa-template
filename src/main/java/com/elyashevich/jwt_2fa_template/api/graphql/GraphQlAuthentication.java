
package com.elyashevich.jwt_2fa_template.api.graphql;

import com.elyashevich.jwt_2fa_template.dto.auth.AuthenticatedUser;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
class GraphQlAuthentication implements WebSocketGraphQlInterceptor {

    static final String USER_ID = "checkersUserId";
    static final String SOCKET_TOKEN = "checkersSocketToken";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo session, Map<String, Object> payload) {
        return Mono.fromRunnable(() -> {
            if (!(payload.get(HttpHeaders.AUTHORIZATION) instanceof String authorization) || !authorization.startsWith(BEARER_PREFIX)) {
                throw new BadCredentialsException("A bearer access token is required");
            }
            String token = authorization.substring(BEARER_PREFIX.length());
            authenticate(token);
            session.getAttributes().put(SOCKET_TOKEN, token);
        }).subscribeOn(Schedulers.boundedElastic()).then(Mono.empty());
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        if (request instanceof WebSocketGraphQlRequest socketRequest) {
            String token = (String) socketRequest.getSessionInfo().getAttributes().get(SOCKET_TOKEN);
            return Mono.fromCallable(() -> authenticate(token))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(userId -> {
                        request.configureExecutionInput((input, builder) ->
                                builder.graphQLContext(Map.of(USER_ID, userId, SOCKET_TOKEN, token)).build());
                        return chain.next(request);
                    });
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(Map.of(USER_ID, user.getSession().getUserId())).build());
        }
        return chain.next(request);
    }

    long authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("A bearer access token is required");
        }
        try {
            var authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(token, token));
            return ((AuthenticatedUser) authentication.getPrincipal()).getSession().getUserId();
        } catch (AppException | AuthenticationException ex) {
            throw new BadCredentialsException("Invalid access token");
        }
    }

    Instant expiresAt(String token) {
        return jwtService.extractExpiration(token, JwtType.ACCESS).toInstant();
    }
}
