package com.elyashevich.jwt_2fa_template.dto.cache;

import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.util.HashSet;
import java.util.Set;
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
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
    private String email;

    public static UserSessionCache of(UserProfile userProfile) {
        return builder()
            .userId(userProfile.getId())
            .username(userProfile.getName())
            .email(userProfile.getEmail())
            .build();
    }
}
