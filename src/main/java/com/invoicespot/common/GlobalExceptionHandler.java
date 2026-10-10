package com.invoicespot.common;

import com.invoicespot.business.BusinessChangedException;
import com.invoicespot.ratelimit.RateLimitedException;
import com.invoicespot.upload.UploadService;
import java.sql.SQLException;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(BusinessChangedException.class)
    public ResponseEntity<ErrorResponse> handleBusinessChanged(BusinessChangedException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(BusinessChangedException.HEADER, ex.getActiveBusiness())
                .body(ErrorResponse.of(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You are not authorized to perform this request");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    // A value PostgreSQL can't store is the request's to fix, not a clash
    // with a record already saved: a number too large for its column, such
    // as a document total past 17 digits (SQLSTATE 22003), or text with a
    // NUL character in it (22021).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        String state = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
        if ("22003".equals(state)) {
            return build(HttpStatus.BAD_REQUEST, "An amount is too large to save");
        }
        if ("22021".equals(state)) {
            return build(HttpStatus.BAD_REQUEST, "Text can't contain a NUL character");
        }
        return build(HttpStatus.CONFLICT, "That conflicts with a record that already exists");
    }

    // Spring reports a body sent in more parts than Tomcat takes as too large
    // as well; that one is answered as an unreadable body, below, as the
    // logo's size has nothing to do with it.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, WebRequest request) {
        if (ex.getMostSpecificCause() instanceof FileCountLimitExceededException) {
            return handleUnreadableParts(ex, request);
        }
        return build(HttpStatus.PAYLOAD_TOO_LARGE, UploadService.TOO_LARGE);
    }

    // Any other multipart body that can't be read is a 400. Most are the
    // sender's to fix, such as one cut short or sent in more parts than
    // Tomcat takes, but Tomcat failing on its own side lands here too, such
    // as when it can't write a part to its temp folder, so each is logged in
    // one warning line. One over the size limit is answered above.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableParts(MultipartException ex, WebRequest request) {
        LOGGER.warn("Unreadable multipart body on {}: {}", describe(request), ex.getMostSpecificCause().toString());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException ex, WebRequest request) {
        String path = request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getRequestURI()
                : "/" + ex.getResourcePath();
        return build(HttpStatus.NOT_FOUND, "That route does not exist - " + path);
    }

    // Spring's own request errors, such as a method or media type the route
    // doesn't take or a missing parameter, keep their status. Anything else
    // is unexpected: it is logged with where it happened, and answered
    // without its details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse spring
                && spring.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(spring.getStatusCode().value());
            String detail = spring.getBody().getDetail();
            return build(status, detail == null ? status.getReasonPhrase() : detail);
        }
        LOGGER.error("Unexpected error on {}", describe(request), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
    }

    private static String describe(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getMethod() + " " + servlet.getRequest().getRequestURI()
                : request.getDescription(false);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), message));
    }
}
