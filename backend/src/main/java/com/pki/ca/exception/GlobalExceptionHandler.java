package com.pki.ca.exception;

import com.pki.ca.dto.response.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetailResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        // Prevent user enumeration by returning generic credential failure
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/INVALID_CREDENTIALS",
                "Authentication Failed",
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid username or password provided.",
                request.getRequestURI(),
                "PKI-40101"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler({AccountLockedException.class, LockedException.class})
    public ResponseEntity<ProblemDetailResponse> handleAccountLocked(Exception ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/ACCOUNT_LOCKED",
                "Account Locked",
                HttpStatus.LOCKED.value(),
                "Account is temporarily locked due to excessive failed authentication attempts. Please try again later.",
                request.getRequestURI(),
                "PKI-42301"
        );
        return ResponseEntity.status(HttpStatus.LOCKED).body(problem);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ProblemDetailResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/ACCOUNT_DISABLED",
                "Account Disabled",
                HttpStatus.FORBIDDEN.value(),
                "Account is disabled. Please contact your CA Administrator.",
                request.getRequestURI(),
                "PKI-40302"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String detail = (ex.getMessage() != null && !ex.getMessage().isBlank() && !ex.getMessage().equalsIgnoreCase("Access Denied"))
                ? ex.getMessage()
                : "You do not possess the required authority or role to perform this operation.";
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/FORBIDDEN",
                "Access Denied",
                HttpStatus.FORBIDDEN.value(),
                detail,
                request.getRequestURI(),
                "PKI-40300"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetailResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/NOT_FOUND",
                "Resource Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "PKI-40400"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetailResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/BAD_REQUEST",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/CONFLICT",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/VALIDATION_ERROR",
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "One or more request parameters failed validation.",
                request.getRequestURI(),
                "PKI-40010"
        );

        List<ProblemDetailResponse.InvalidParam> invalidParams = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            invalidParams.add(new ProblemDetailResponse.InvalidParam(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        problem.setInvalidParams(invalidParams);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HsmOperationException.class)
    public ResponseEntity<ProblemDetailResponse> handleHsmOperation(HsmOperationException ex, HttpServletRequest request) {
        logger.error("HSM/Cryptographic provider failure: {}", ex.getMessage());
        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/HSM_OPERATION_FAILED",
                "Cryptographic Provider Error",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled internal server exception", ex);

        ProblemDetailResponse problem = new ProblemDetailResponse(
                "https://pki.internal.corp/errors/INTERNAL_SERVER_ERROR",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please contact the system administrator.",
                request.getRequestURI(),
                "PKI-50000"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
