package com.elyashevich.jwt_2fa_template.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppException extends RuntimeException {

    private final AppExceptionCode appExceptionCode;

    public AppException(AppExceptionCode appExceptionCode) {
        this.appExceptionCode = appExceptionCode;
    }

    public AppException(String message, AppExceptionCode appExceptionCode) {
        super(message);
        this.appExceptionCode = appExceptionCode;
    }

    public AppException(String message, Throwable cause, AppExceptionCode appExceptionCode) {
        super(message, cause);
        this.appExceptionCode = appExceptionCode;
    }

    public AppException(Throwable cause, AppExceptionCode appExceptionCode) {
        super(cause);
        this.appExceptionCode = appExceptionCode;
    }
}
