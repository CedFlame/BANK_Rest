package com.example.bankcards.exception;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "validation.failed", "Validation failed", req, emptyToNull(fields), ex, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            fields.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "validation.constraint", "Constraint violation", req, emptyToNull(fields), ex, false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.illegal_argument", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        ex.getFieldErrors().forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "validation.bind", "Binding failed", req, emptyToNull(fields), ex, false);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Parameter '%s' has invalid value".formatted(ex.getName());
        return build(HttpStatus.BAD_REQUEST, "request.type_mismatch", msg, req, null, ex, false);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.malformed_json", "Malformed JSON request", req, null, ex, false);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.missing_parameter", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponse> handleMissingPathVar(MissingPathVariableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.missing_path_variable", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request.bad", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwt(JwtException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "auth.jwt_invalid", "Invalid or expired token", req, null, ex, false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "auth.forbidden", "Forbidden", req, null, ex, false);
    }

    @ExceptionHandler({
            UsernameNotFoundException.class,
            UserNotFoundException.class,
            CardNotFoundException.class,
            TransferNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "resource.not_found", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "request.method_not_allowed", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler({
            CardAlreadyExistsException.class,
            IdempotencyConflictException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "resource.conflict", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String msgLower = (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        String code = (msgLower != null && msgLower.toLowerCase().contains("duplicate key")) ? "db.unique_violation" : "db.integrity_violation";
        String msg = code.equals("db.unique_violation") ? "Unique constraint violation" : "Data integrity violation";
        return build(HttpStatus.CONFLICT, code, msg, req, null, ex, false);
    }

    @ExceptionHandler({
            InvalidCardStateException.class,
            CardExpiredException.class,
            TransferInvalidStateException.class,
            TransferExpiredException.class,
            InsufficientFundsException.class,
            OwnershipViolationException.class,
            CardOwnershipException.class,
            CardDeletionNotAllowedException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "business.invalid_state", ex.getMessage(), req, null, ex, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "server.error", "Internal server error", req, null, ex, true);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest req, Map<String, String> fields, Exception ex, boolean logStack) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(req != null ? req.getRequestURI() : null)
                .fields(fields)
                .build();
        logForStatus(status, code, message, req, ex, logStack);
        return ResponseEntity.status(status).body(body);
    }

    private static Map<String, String> emptyToNull(Map<String, String> map) {
        return (map == null || map.isEmpty()) ? null : Map.copyOf(map);
    }

    private void logForStatus(HttpStatus status, String code, String message, HttpServletRequest req, Exception ex, boolean logStack) {
        String base = "%s %s -> %d %s (%s)".formatted(
                req != null ? req.getMethod() : "-",
                req != null ? req.getRequestURI() : "-",
                status.value(),
                code,
                message
        );
        if (status.is5xxServerError()) {
            if (logStack || ex == null) log.error(base, ex);
            else log.error(base);
            return;
        }
        if (ex != null && logStack) log.info(base, ex);
        else log.info(base);
    }
}
