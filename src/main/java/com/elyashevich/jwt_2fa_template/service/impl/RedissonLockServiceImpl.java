package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.LockService;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonLockServiceImpl implements LockService {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    @Override
    public <T> T tryWithLock(@NonNull String lockName, @NonNull Supplier<T> action, AppExceptionCode exceptionCode) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lockName);
        if (!lock.tryLock()) {
            log.debug("Lock is busy, rejecting action: {}", lockName);
            throw new AppException(exceptionCode);
        }
        try {
            return action.get();
        } finally {
            unlock(lock, lockName);
        }
    }

    private void unlock(RLock lock, String lockName) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            log.error("Failed to release lock: {}", lockName, e);
        }
    }
}
