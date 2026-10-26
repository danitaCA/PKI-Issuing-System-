package com.pki.ca.exception;

public class ConflictException extends RuntimeException {
    private final String errorCode;

    public ConflictException(String message) {
        super(message);
        this.errorCode = "PKI-40900";
    }

    public ConflictException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
