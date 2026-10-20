package com.elyashevich.jwt_2fa_template.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EmailUtil {

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
