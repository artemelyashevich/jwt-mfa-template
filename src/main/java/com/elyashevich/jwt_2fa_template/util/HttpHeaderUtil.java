package com.elyashevich.jwt_2fa_template.util;

import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

public final class HttpHeaderUtil {

    public static final Locale DEFAULT_LOCALE = Locale.of("ru", "RU");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.of("ru"), Locale.of("en"), Locale.of("be"));

    public static String getToken(HttpServletRequest httpServletRequest) {
        return Optional.ofNullable(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
            .orElseThrow(() -> new AppException(AppExceptionCode.EX401_TOKEN_MISSING))
            .replace("Bearer ", "");
    }

    public static Locale getLocale(HttpServletRequest httpServletRequest) {
        return getLocale(httpServletRequest.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
    }

    public static Locale getLocale(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_LOCALE;
        }
        try {
            return Optional.ofNullable(Locale.lookup(Locale.LanguageRange.parse(header), SUPPORTED_LOCALES))
                .orElse(DEFAULT_LOCALE);
        } catch (IllegalArgumentException e) {
            return DEFAULT_LOCALE;
        }
    }
}
