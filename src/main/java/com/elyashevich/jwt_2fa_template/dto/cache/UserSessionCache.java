package com.elyashevich.jwt_2fa_template.dto.cache;

import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.entity.UserStatus;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionCache {

    private Long userId;
    private String username;
    private String email;
    @Builder.Default
    private UserLocale locale = UserLocale.RU;
    private UserStatus status;
    @Builder.Default
    private Set<RoleCode> roleCodes = new HashSet<>();

    public static UserSessionCache of(UserProfile userProfile) {
        return builder()
            .userId(userProfile.getId())
            .username(userProfile.getName())
            .email(userProfile.getEmail())
            .locale(userProfile.getLocale())
            .status(userProfile.getStatus())
            .roleCodes(userProfile.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet()))
            .build();
    }
}
