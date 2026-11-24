package com.elyashevich.jwt_2fa_template.configuration.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class PublicUrls {

    private static final String PREFIX = "/api/v1/";

    public static final String AUTH_TAG = PREFIX + "auth";

    public static final String ACCOUNT_TAG = PREFIX + "account";

    public static final String ADMIN_TAG = PREFIX + "admin";

    static final RequestMatcher GRAPHIQL = PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.GET, "/graphiql");

    public static final RequestMatcher PUBLIC_URLS = new OrRequestMatcher(
        PathPatternRequestMatcher.withDefaults().matcher("/error"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, PREFIX + "graphql/ws"),
        PathPatternRequestMatcher.withDefaults().matcher(AUTH_TAG + "/**")
    );

    private PublicUrls() {
        throw new UnsupportedOperationException();
    }
}
