package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface UserService {

    UserProfile save(UserProfile userProfile);

    Optional<UserProfile> findByEmail(String email);

    UserProfile findById(Long id);

    /**
     * Finds users whose email contains {@code emailPart}, ignoring case, sorted by email. Roles are loaded eagerly.
     */
    List<UserProfile> searchByEmail(String emailPart);

    UserProfile update(Long id, Consumer<UserProfile> mutation);

    void recordLogin(Long userId, String refreshTokenHash, Instant lastLoginAt);
}
