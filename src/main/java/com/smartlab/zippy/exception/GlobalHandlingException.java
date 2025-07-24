package com.smartlab.zippy.exception;

import com.smartlab.zippy.exception.constant.ErrorCode;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalHandlingException {

    private static final Logger logger = LoggerFactory.getLogger(GlobalHandlingException.class);

    // Handle specific HTTP error codes with detailed logging

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        logger.error("401 UNAUTHORIZED: Authentication failed for request [{}]. Error: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.UNAUTHORIZED.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Authentication failed: " + ex.getMessage())
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(PaymentRequiredException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ResponseEntity<ApiResponse<Object>> handlePaymentRequiredException(
            PaymentRequiredException ex, HttpServletRequest request) {

        logger.error("402 PAYMENT REQUIRED: Payment required for request [{}]. Error: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.PAYMENT_REQUIRED.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Payment required: " + ex.getMessage())
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.PAYMENT_REQUIRED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        logger.error("403 FORBIDDEN: Access denied for request [{}]. Error: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.FORBIDDEN.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Access denied: " + ex.getMessage())
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({NoHandlerFoundException.class, ResourceNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(
            Exception ex, HttpServletRequest request) {

        logger.error("404 NOT FOUND: Resource not found for request [{}]. Error: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.NOT_FOUND.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Resource not found: " + ex.getMessage())
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(
            Exception ex, HttpServletRequest request) {

        logger.error("500 INTERNAL SERVER ERROR: Unexpected error for request [{}]",
                request.getRequestURI(), ex);

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.INTERNAL_SERVER_ERROR.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Internal server error occurred")
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadGatewayException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ResponseEntity<ApiResponse<Object>> handleBadGatewayException(
            BadGatewayException ex, HttpServletRequest request) {

        logger.error("502 BAD GATEWAY: Bad gateway for request [{}]. Error: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("path", request.getRequestURI());
        details.put("error", ErrorCode.BAD_GATEWAY.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Bad gateway: " + ex.getMessage())
                .data(details)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    // Custom exception classes needed for the handlers

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class PaymentRequiredException extends RuntimeException {
        public PaymentRequiredException(String message) {
            super(message);
        }
    }

    public static class BadGatewayException extends RuntimeException {
        public BadGatewayException(String message) {
            super(message);
        }
    }
}
