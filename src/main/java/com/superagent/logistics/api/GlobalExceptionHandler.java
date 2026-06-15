package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.ApiErrorCode;
import com.superagent.logistics.api.dto.ApiErrorResponse;
import com.superagent.logistics.security.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不完整");
        return error(ApiErrorCode.VALIDATION_ERROR, message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        return error(ApiErrorCode.ACCESS_DENIED, ex.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        return error(ApiErrorCode.BAD_REQUEST, ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        log.error("api.unhandled_runtime_error path={}", request.getRequestURI(), ex);
        return error(ApiErrorCode.INTERNAL_ERROR, "系统处理失败，请携带 traceId 联系管理员排查",
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiErrorResponse> error(ApiErrorCode code,
                                                   String message,
                                                   HttpStatus status,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code.name(),
                message,
                status.value(),
                request.getRequestURI(),
                firstNonBlank(MDC.get("traceId"), MDC.get("requestId")),
                Instant.now()
        ));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }
}
