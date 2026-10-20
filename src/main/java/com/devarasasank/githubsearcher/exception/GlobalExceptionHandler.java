package com.devarasasank.githubsearcher.exception;

import com.devarasasank.githubsearcher.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates every application exception into a consistent JSON error body
 * with the appropriate HTTP status.
 *
 * <p>Status mapping:
 * <ul>
 *   <li>validation / bad input &rarr; 400</li>
 *   <li>unknown route &rarr; 404</li>
 *   <li>unsupported HTTP method &rarr; 405</li>
 *   <li>GitHub rate limit &rarr; 429</li>
 *   <li>GitHub auth failure &rarr; 502</li>
 *   <li>GitHub upstream error &rarr; 502</li>
 *   <li>GitHub unreachable &rarr; 503</li>
 *   <li>anything else &rarr; 500</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({InvalidSortException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message;
        if (ex instanceof InvalidSortException invalidSort) {
            message = invalidSort.getMessage();
        } else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Invalid value '" + mismatch.getValue()
                    + "' for parameter '" + mismatch.getName() + "'";
        } else {
            message = "Request body is missing or malformed";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(GitHubRateLimitException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(GitHubAuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(GitHubAuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(GitHubUpstreamException.class)
    public ResponseEntity<ApiError> handleUpstream(GitHubUpstreamException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(GitHubUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(GitHubUnavailableException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /**
     * Keeps Spring's own HTTP errors at their intended status codes. Without
     * these handlers, the catch-all below would intercept them first and turn
     * every unknown route into a 500 (the original root-cause bug for
     * {@code GET /}). Genuine application exceptions are unaffected and still
     * reach their own handlers or the 500 catch-all.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No resource found for " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for " + request.getRequestURI(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
