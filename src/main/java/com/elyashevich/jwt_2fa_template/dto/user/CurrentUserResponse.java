package com.elyashevich.jwt_2fa_template.dto.user;

import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.entity.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUserResponse(
    Long id,
    String email,
    String name,
    UserStatus status,
    String locale,
    Set<RoleCode> roles,
    Instant emailVerifiedAt,
    Instant lastLoginAt
) {

    public static CurrentUserResponse from(UserProfile user) {
        return new CurrentUserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getStatus(),
            user.getLocale().getCode(),
            user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
            user.getEmailVerifiedAt(),
            user.getLastLoginAt()
        );
    }
}
