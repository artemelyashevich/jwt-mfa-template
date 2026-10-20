package com.elyashevich.jwt_2fa_template.repositroy;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserProfile> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserProfile u SET u.refreshTokenHash = :refreshTokenHash, u.lastLoginAt = :lastLoginAt WHERE u.id = :id")
    int updateLoginState(@Param("id") Long id,
        @Param("refreshTokenHash") String refreshTokenHash,
        @Param("lastLoginAt") Instant lastLoginAt);
}
