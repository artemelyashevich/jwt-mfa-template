package com.elyashevich.jwt_2fa_template.annotation;


import com.elyashevich.jwt_2fa_template.dto.cache.LockerType;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessLock {

    String CURRENT_USER_KEY = "@userSessionServiceImpl.getCurrentUser().getUserId()";

    LockerType type();

    String key();

    String condition() default "true"; // SpEL

    AppExceptionCode onFail() default AppExceptionCode.EX409_OPERATION_IN_PROGRESS;
}