package com.pki.ca.exception;

public class AccountLockedException extends RuntimeException {
    private final String errorCode = "PKI-42300";

    public AccountLockedException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
