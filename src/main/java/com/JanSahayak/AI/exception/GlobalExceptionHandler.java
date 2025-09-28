package com.JanSahayak.AI.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.JanSahayak.AI.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.multipart.MultipartException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFound(UserNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("User not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("User not found", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handlePostNotFound(PostNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Post not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Post not found", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(SecurityException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Security error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Access denied", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Access denied error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Access denied", "You don't have permission to access this resource");
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(jakarta.validation.ValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Validation failed", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Constraint violation error [{}]: {}", requestId, ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Method argument not valid error [{}]: {}", requestId, ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Illegal argument error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Invalid request", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Max upload size exceeded error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("File too large", "The uploaded file exceeds the maximum allowed size");
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaValidation(MediaValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Media validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Media validation failed", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleServiceException(ServiceException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Service error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Service error", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Runtime error [{}]: {}", requestId, ex.getMessage(), ex);

        // Don't expose internal error details in production
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            message = "An unexpected error occurred";
        }

        ApiResponse<Object> response = ApiResponse.error("Internal server error", message);
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Unexpected error [{}]: {}", requestId, ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error("Internal server error", "An unexpected error occurred");
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    // Add this handler method to your existing GlobalExceptionHandler class

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomValidationException(ValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Custom validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Validation failed", ex.getMessage());
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipartException(MultipartException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Multipart parsing error [{}]: {}", requestId, ex.getMessage(), ex);

        String userMessage;
        if (ex.getCause() instanceof IllegalStateException) {
            userMessage = "The uploaded file could not be processed. Please try again with a different file.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("size")) {
            userMessage = "The uploaded file is too large. Maximum size allowed is 512MB.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("parse")) {
            userMessage = "There was an error processing your file upload. Please check the file format and try again.";
        } else {
            userMessage = "File upload failed. Please try again with a valid file.";
        }

        ApiResponse<Object> response = ApiResponse.error("File upload failed", userMessage);
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}