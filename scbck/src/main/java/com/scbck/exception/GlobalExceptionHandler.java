package com.scbck.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.scbck.dto.ApiError;

import jakarta.validation.ConstraintViolationException;

/**
 * Translates exceptions into the uniform {@link ApiError} JSON body.
 *
 * Previously every controller caught its own exceptions and returned the
 * message inside a 200 OK string, which meant the client could not tell
 * success from failure by status code alone.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        // Deliberately vague: do not reveal whether the username exists.
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "This account has been deactivated.", request);
    }

    /** Bean-validation failures on @Valid @RequestBody arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed." : message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Unique-key / foreign-key violations that slipped past the service checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation on {}", request.getDescription(false), ex);
        return build(HttpStatus.CONFLICT,
                "The record conflicts with existing data. Check for duplicate values.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", request.getDescription(false), ex);
        // Never leak internal exception text to the client.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, WebRequest request) {
        ApiError body = new ApiError(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replaceFirst("^uri=", ""));
        return ResponseEntity.status(status).body(body);
    }
}
