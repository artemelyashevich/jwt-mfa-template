package com.elyashevich.jwt_2fa_template.annotation;

import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.LockService;
import com.elyashevich.jwt_2fa_template.util.SpELUtils;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class BusinessLockAspect {

    private final LockService lockerService;
    private final SpELUtils spelUtils;

    @Around("@annotation(businessLock)")
    public Object businessLock(ProceedingJoinPoint pjp, BusinessLock businessLock) throws Throwable {
        StandardEvaluationContext context = spelUtils.createContext(pjp);
        final Boolean condition = spelUtils.evaluate(businessLock.condition(), context, Boolean.class);
        if (Boolean.FALSE.equals(condition)) {
            log.debug("BusinessLock skipped by condition");
            return pjp.proceed();
        }
        final String keyValue = spelUtils.evaluate(businessLock.key(), context, String.class);
        if (keyValue == null || keyValue.isBlank()) {
            log.error("BusinessLock key '{}' resolved to empty value", businessLock.key());
            throw new AppException(AppExceptionCode.EX500);
        }
        final String businessLockKey = businessLock.type().name() + ":" + keyValue.trim().toLowerCase(Locale.ROOT);

        return lockerService.tryWithLock(businessLockKey, () -> {
            try {
                return pjp.proceed();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, businessLock.onFail());
    }
}