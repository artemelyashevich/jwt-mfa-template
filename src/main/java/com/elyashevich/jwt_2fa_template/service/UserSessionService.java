package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.cache.UserSessionCache;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.util.Optional;

public interface UserSessionService {

    UserSessionCache getCurrentUser();

    Optional<UserSessionCache> getCurrentUserSafety();

    Optional<UserSessionCache> findActiveSession(Long userId, String token);

    void updateSession(UserProfile userProfile);

    void invalidateSession(Long userId);

    UserSessionCache create(String token, UserProfile userProfile);
}
