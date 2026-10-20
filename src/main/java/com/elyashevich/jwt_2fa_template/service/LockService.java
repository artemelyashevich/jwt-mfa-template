package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import io.reactivex.rxjava3.annotations.NonNull;
import java.util.function.Supplier;

public interface LockService {

    <T> T tryWithLock(@NonNull String lockName, @NonNull Supplier<T> action, AppExceptionCode exceptionCode);
}