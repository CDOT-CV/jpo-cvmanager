package com.trihydro.rsuinfobridge.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Global exception handler for the RSU Info Bridge application.
 * Handles exceptions that can occur in the current API:
 * - Type mismatches on request parameters
 * - Unsupported HTTP methods
 * - Database access errors
 * - Unexpected runtime errors
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle method argument type mismatch exceptions (e.g., invalid boolean parameter)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: {}", ex.getMessage());

        String message = String.format("Parameter '%s' should be of type '%s'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Type Mismatch");

        return problemDetail;
    }

    /**
     * Handle HTTP request method not supported exceptions
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()));
        problemDetail.setTitle("Method Not Allowed");

        return problemDetail;
    }

    /**
     * Handle data access exceptions (database connectivity issues, constraint violations, etc.)
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessException(DataAccessException ex) {
        log.error("Data access error: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "An error occurred while accessing the database");
        problemDetail.setTitle("Database Error");

        return problemDetail;
    }

    /**
     * Handle all other unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");

        return problemDetail;
    }

    /**
     * Handle binding failures on parameter objects (e.g., invalid boolean in RsuFilter)
     */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex) {
        log.warn("Request binding failed: {}", ex.getMessage());

        String message = ex.getFieldErrors().stream()
                .map(error -> String.format("Parameter '%s' has invalid value '%s'", error.getField(), error.getRejectedValue()))
                .collect(Collectors.joining("; "));

        if (message.isEmpty()) {
            message = "Invalid request parameters";
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Type Mismatch");

        return problemDetail;
    }
}
