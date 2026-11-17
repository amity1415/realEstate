package ae.nest.crm.common;

import ae.nest.crm.common.ApiExceptions.BadRequestException;
import ae.nest.crm.common.ApiExceptions.ConflictException;
import ae.nest.crm.common.ApiExceptions.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/** Maps exceptions to a stable JSON error envelope: {@code {"error":{"code","message","details"}}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> notFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> conflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "conflict", ex.getMessage(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(
                HttpStatus.CONFLICT,
                "version_conflict",
                "The record was modified by another request. Reload and retry.",
                null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> badRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> validation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed",
                details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "validation_failed", ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> dataIntegrity(DataIntegrityViolationException ex) {
        // Do not leak SQL/constraint internals to the client.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "conflict",
                "The request conflicts with existing data.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> accessDenied(AccessDeniedException ex) {
        // Method-security (@PreAuthorize) denials — return 403, not 500.
        return build(HttpStatus.FORBIDDEN, "forbidden", "Access denied", null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Object> noHandler(NoHandlerFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "not_found", "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> generic(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "An unexpected error occurred.", null);
    }

    private ResponseEntity<Object> build(HttpStatus status, String code, String message, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? status.getReasonPhrase() : message);
        if (details != null) {
            error.put("details", details);
        }
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
