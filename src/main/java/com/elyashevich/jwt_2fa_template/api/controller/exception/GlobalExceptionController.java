package com.elyashevich.jwt_2fa_template.api.controller.exception;

import static com.elyashevich.jwt_2fa_template.exception.AppExceptionCode.EX400;
import static com.elyashevich.jwt_2fa_template.exception.AppExceptionCode.EX403;
import static com.elyashevich.jwt_2fa_template.exception.AppExceptionCode.EX404_ROUTE;
import static com.elyashevich.jwt_2fa_template.exception.AppExceptionCode.EX409;

import com.elyashevich.jwt_2fa_template.dto.exception.ExceptionBody;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionController {

    private final I18nService i18nService;

    private static String messageOrDefault(String message) {
        return message == null ? "Invalid value" : message;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ExceptionBody> handleAppException(AppException exception, HttpServletRequest request) {
        return buildExceptionBody(exception, exception.getAppExceptionCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionBody> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = exception.getBindingResult()
            .getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> messageOrDefault(fieldError.getDefaultMessage()),
                (exist, newMessage) -> exist + " " + newMessage,
                LinkedHashMap::new
            ));
        return buildExceptionBody(exception, EX400, request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ExceptionBody> handleMethodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
            errors.merge(result.getMethodParameter().getParameterName(), messageOrDefault(error.getDefaultMessage()),
                (exist, newMessage) -> exist + " " + newMessage)));
        return buildExceptionBody(exception, EX400, request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ExceptionBody> handleMalformed(Exception exception, HttpServletRequest request) {
        return buildExceptionBody(exception, AppExceptionCode.EX400_MALFORMED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionBody> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        return buildExceptionBody(exception, EX404_ROUTE, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionBody> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return buildExceptionBody(exception, EX403, request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ExceptionBody> handlePessimisticLock(PessimisticLockingFailureException exception, HttpServletRequest request) {
        return buildExceptionBody(exception, AppExceptionCode.EX409_OPERATION_IN_PROGRESS, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionBody> handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        return buildExceptionBody(exception, EX409, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionBody> handleException(Exception exception, HttpServletRequest request) {
        return buildExceptionBody(exception, AppExceptionCode.EX500, request);
    }

    private ResponseEntity<ExceptionBody> buildExceptionBody(Exception e, AppExceptionCode exceptionCode, HttpServletRequest request) {
        return buildExceptionBody(e, exceptionCode, request, Map.of());
    }

    private ResponseEntity<ExceptionBody> buildExceptionBody(Exception e,
        AppExceptionCode exceptionCode,
        HttpServletRequest request,
        Map<String, String> extraDetails) {
        UUID exceptionId = UUID.randomUUID();
        String path = request.getServletPath();
        HttpStatus httpStatus = HttpStatus.valueOf(exceptionCode.getCode());

        if (httpStatus.is5xxServerError()) {
            log.error("PATH: {} - STATUS: {} - errorId: {}", path, httpStatus.value(), exceptionId, e);
        } else {
            log.warn("PATH: {} - STATUS: {} - '{}' - errorId: {}", path, httpStatus.value(), e.getMessage(), exceptionId);
        }

        Map<String, String> details = new LinkedHashMap<>(extraDetails);
        details.put("errorId", exceptionId.toString());
        details.put("errorCode", String.valueOf(exceptionCode.getCode()));
        details.put("error", exceptionCode.getLabel());

        return ResponseEntity.status(httpStatus).body(ExceptionBody.builder()
            .message(i18nService.getErrorMessage(exceptionCode.getLabel()))
            .details(details)
            .build());
    }
}
