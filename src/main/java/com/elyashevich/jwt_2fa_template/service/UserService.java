package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface UserService {

    UserProfile save(UserProfile userProfile);

    Optional<UserProfile> findByEmail(String email);

    UserProfile update(Long id, Consumer<UserProfile> mutation);

    void recordLogin(Long userId, String refreshTokenHash, Instant lastLoginAt);
}
