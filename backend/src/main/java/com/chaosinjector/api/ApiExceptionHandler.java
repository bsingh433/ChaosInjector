package com.chaosinjector.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chaosinjector.api.dto.ApiError;
import com.chaosinjector.config.ChaosInjectorException;
import com.chaosinjector.config.Errors;

/** Maps typed exceptions to the {@code {code, message, details}} body (spec §15). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ChaosInjectorException.class)
    public ResponseEntity<ApiError> handleTyped(ChaosInjectorException ex) {
        HttpStatus status = statusFor(ex);
        return ResponseEntity.status(status).body(new ApiError(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Request validation failed", messages));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", ex.getMessage(), null));
    }

    private HttpStatus statusFor(ChaosInjectorException ex) {
        if (ex instanceof Errors.ValidationError) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof Errors.TargetNotFoundError) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof Errors.ConflictError) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof Errors.ConnectionError) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
