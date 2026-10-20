package com.elyashevich.jwt_2fa_template.entity;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public enum RoleCode {

    USER,
    MODERATOR,
    ADMIN;

    public static Optional<RoleCode> highest(Collection<RoleCode> roleCodes) {
        return roleCodes.stream().max(Comparator.naturalOrder());
    }

    public String asAuthority() {
        return "ROLE_" + name();
    }
}