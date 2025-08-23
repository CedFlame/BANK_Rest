package com.example.bankcards.exception;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "validation.failed", "Validation failed", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex,
                                                          HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            fields.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "validation.constraint", "Constraint violation", req, fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.missing_parameter", ex.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                          HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.bad", ex.getMessage(), req);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwt(JwtException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "auth.jwt_invalid", "Invalid or expired token", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "auth.forbidden", "Forbidden", req);
    }

    @ExceptionHandler({
            OwnershipViolationException.class,
            CardOwnershipException.class
    })
    public ResponseEntity<ErrorResponse> handleOwnership(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ownership.forbidden", ex.getMessage(), req);
    }

    @ExceptionHandler({
            UsernameNotFoundException.class,
            UserNotFoundException.class,
            CardNotFoundException.class,
            TransferNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "resource.not_found", ex.getMessage(), req);
    }

    @ExceptionHandler({
            CardAlreadyExistsException.class,
            IdempotencyConflictException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "resource.conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "db.integrity_violation", "Data integrity violation", req);
    }

    @ExceptionHandler({
            InvalidCardStateException.class,
            CardExpiredException.class,
            TransferInvalidStateException.class,
            TransferExpiredException.class,
            InsufficientFundsException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "business.invalid_state", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "server.error", "Internal server error", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String code,
                                                String message,
                                                HttpServletRequest req) {
        return build(status, code, message, req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String code,
                                                String message,
                                                HttpServletRequest req,
                                                Map<String, String> fields) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(req != null ? req.getRequestURI() : null)
                .fields(fields == null || fields.isEmpty() ? null : fields)
                .build();

        if (status.is5xxServerError()) {
            log.error("{} {} -> {} {}", req.getMethod(), req.getRequestURI(), status.value(), code);
        } else {
            log.warn("{} {} -> {} {} ({})", req.getMethod(), req.getRequestURI(), status.value(), code, message);
        }
        return ResponseEntity.status(status).body(body);
    }
}
