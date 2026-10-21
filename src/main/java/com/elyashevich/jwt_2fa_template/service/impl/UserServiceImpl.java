package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.repository.UserProfileRepository;
import com.elyashevich.jwt_2fa_template.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Map<String, Object> LOCK_HINTS = Map.of("jakarta.persistence.lock.timeout", 5000);

    private final UserProfileRepository userProfileRepository;
    private final EntityManager entityManager;

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileRepository.save(userProfile);
    }

    @Override
    public Optional<UserProfile> findByEmail(String email) {
        return userProfileRepository.findByEmail(email);
    }

    @Override
    public UserProfile findById(Long id) {
        return userProfileRepository.findWithRolesById(id)
            .orElseThrow(() -> new AppException(AppExceptionCode.EX404_USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserProfile update(Long id, Consumer<UserProfile> mutation) {
        UserProfile user = lockForUpdate(id);
        mutation.accept(user);
        return save(user);
    }

    @Override
    @Transactional
    public void recordLogin(Long userId, String refreshTokenHash, Instant lastLoginAt) {
        if (userProfileRepository.updateLoginState(userId, refreshTokenHash, lastLoginAt) == 0) {
            throw new AppException(AppExceptionCode.EX404_USER_NOT_FOUND);
        }
    }

    private UserProfile lockForUpdate(Long userId) {
        UserProfile user = entityManager.find(UserProfile.class, userId);
        if (user == null) {
            throw new AppException(AppExceptionCode.EX404_USER_NOT_FOUND);
        }
        entityManager.flush();
        try {
            entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE, LOCK_HINTS);
            entityManager.refresh(user);
        } catch (EntityNotFoundException e) {
            throw new AppException(e, AppExceptionCode.EX404_USER_NOT_FOUND);
        }
        return user;
    }
}
