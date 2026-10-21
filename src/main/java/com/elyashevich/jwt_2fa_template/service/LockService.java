package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import java.util.function.Supplier;
import lombok.NonNull;

public interface LockService {

    <T> T tryWithLock(@NonNull String lockName, @NonNull Supplier<T> action, AppExceptionCode exceptionCode);
}