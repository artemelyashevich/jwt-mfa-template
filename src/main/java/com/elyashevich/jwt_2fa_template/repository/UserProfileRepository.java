package com.elyashevich.jwt_2fa_template.repository;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = UserProfile.Fields.roles)
    Optional<UserProfile> findWithRolesById(Long id);

    Optional<UserProfile> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserProfile u SET u.refreshTokenHash = :refreshTokenHash, u.lastLoginAt = :lastLoginAt WHERE u.id = :id")
    int updateLoginState(@Param("id") Long id,
        @Param("refreshTokenHash") String refreshTokenHash,
        @Param("lastLoginAt") Instant lastLoginAt);
}
