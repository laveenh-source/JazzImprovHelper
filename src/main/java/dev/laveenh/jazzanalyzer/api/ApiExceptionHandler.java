package dev.laveenh.jazzanalyzer.api;

import dev.laveenh.jazzanalyzer.api.dto.ErrorResponse;
import dev.laveenh.jazzanalyzer.service.AnalysisNotReadyException;
import dev.laveenh.jazzanalyzer.service.FileTooLargeException;
import dev.laveenh.jazzanalyzer.service.InvalidUploadException;
import dev.laveenh.jazzanalyzer.service.NotFoundException;
import dev.laveenh.jazzanalyzer.service.UnsupportedFileTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;

/**
 * Turns every exception into the same JSON error shape: {timestamp, status, error, message, path}.
 * Extending Spring's base class means its own errors (bad UUID, missing part, wrong method, unknown URL)
 * go through the same format instead of Spring's default.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({InvalidUploadException.class, InvalidRequestException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ResponseEntity<ErrorResponse> unsupportedType(UnsupportedFileTypeException e, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage(), request);
    }

    /**
     * Our own size check (MaxUploadSizeExceededException, thrown by Spring when the servlet limit is hit,
     * is handled by the base class and ends up in the same format).
     */
    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ErrorResponse> tooLarge(FileTooLargeException e, HttpServletRequest request) {
        return error(HttpStatus.CONTENT_TOO_LARGE, e.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(AnalysisNotReadyException.class)
    ResponseEntity<ErrorResponse> notReady(AnalysisNotReadyException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    /** Last resort: log the details, tell the client nothing about the internals. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    /** Spring's own exceptions (400, 404, 405, 415...) all pass through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        String message = ex instanceof TypeMismatchException mismatch
                ? "Invalid value '" + mismatch.getValue() + "' for '" + mismatch.getPropertyName() + "'."
                : ex.getMessage();
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ErrorResponse response = new ErrorResponse(clock.instant(), status.value(), status.getReasonPhrase(),
                message, servletRequest.getRequestURI());
        return new ResponseEntity<>(response, headers, statusCode);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(clock.instant(), status.value(), status.getReasonPhrase(), message,
                        request.getRequestURI()));
    }
}
