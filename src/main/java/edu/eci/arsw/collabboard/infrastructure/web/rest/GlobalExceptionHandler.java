package edu.eci.arsw.collabboard.infrastructure.web.rest;

import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<ApiError> boardNotFound(BoardNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidDomainInput(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedRequestBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // A nested BoardElement violating its own invariants throws IllegalArgumentException from its
        // compact constructor while Jackson is still building the object graph; Spring wraps that in
        // HttpMessageNotReadableException before bean validation ever runs, so it must be unwrapped here
        // to keep returning the same INVALID_INPUT contract instead of leaking a generic 500.
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        if (cause != null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", cause.getMessage(), request.getRequestURI());
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request body", request.getRequestURI());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiError> starterTodo(UnsupportedOperationException ex, HttpServletRequest request) {
        // This handler makes an incomplete use case explicit instead of leaking a stack trace.
        return error(HttpStatus.NOT_IMPLEMENTED, "LAB_NOT_IMPLEMENTED", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        // Catch-all so no internal Java message or stack trace ever reaches the client.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, path
        ));
    }
}
