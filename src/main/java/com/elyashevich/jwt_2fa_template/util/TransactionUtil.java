package com.elyashevich.jwt_2fa_template.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@UtilityClass
public class TransactionUtil {

    public static void afterCompletion(Consumer<Boolean> committedCallback) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                committedCallback.accept(status == STATUS_COMMITTED);
            }
        });
    }

    public static void afterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    public static void afterCommitEnsuringSync(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit(runnable);
        }
    }

    public static <T> T execute(TransactionTemplate transactionTemplate, Consumer<Boolean> committedCallback, Supplier<T> action) {
        AtomicBoolean registered = new AtomicBoolean();
        try {
            return transactionTemplate.execute(status -> {
                afterCompletion(committedCallback);
                registered.set(true);
                return action.get();
            });
        } catch (RuntimeException e) {
            if (!registered.get()) {
                committedCallback.accept(false);
            }
            throw e;
        }
    }
}