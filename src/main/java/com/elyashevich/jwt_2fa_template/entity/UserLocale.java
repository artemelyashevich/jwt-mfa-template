package com.elyashevich.jwt_2fa_template.entity;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserLocale {

    RU("ru"),
    BE("be");

    private final String code;

    public static UserLocale fromCode(String code) {
        return Arrays.stream(values())
            .filter(locale -> locale.code.equalsIgnoreCase(code))
            .findFirst()
            .orElse(RU);
    }

    public Locale toLocale() {
        return Locale.of(code);
    }
}
