package com.example.orderflow.order.exception;

import com.example.orderflow.order.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private sealed interface ErrorType permits ErrorType.NotFound, ErrorType.BadRequest, ErrorType.Conflict, ErrorType.InternalError {
        record NotFound(String message) implements ErrorType {}
        record BadRequest(String message) implements ErrorType {}
        record Conflict(String message) implements ErrorType {}
        record InternalError(String message) implements ErrorType {}
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        ErrorType errorType = new ErrorType.NotFound(ex.getMessage());

        String message = switch (errorType) {
            case ErrorType.NotFound nf       -> nf.message();
            case ErrorType.BadRequest br     -> br.message();
            case ErrorType.Conflict c        -> c.message();
            case ErrorType.InternalError ie  -> ie.message();
        };

        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),
                message, request.getRequestURI(), Instant.now(), null);
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidTransition(InvalidOrderStatusTransitionException ex, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI(), Instant.now(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI(), Instant.now(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed", request.getRequestURI(), Instant.now(), fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericError(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred", request.getRequestURI(), Instant.now(), null);
    }
}
