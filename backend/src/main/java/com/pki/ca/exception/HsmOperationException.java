package com.pki.ca.exception;

public class HsmOperationException extends RuntimeException {
    private final String errorCode;

    public HsmOperationException(String message) {
        super(message);
        this.errorCode = "PKI-50001";
    }

    public HsmOperationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public HsmOperationException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
