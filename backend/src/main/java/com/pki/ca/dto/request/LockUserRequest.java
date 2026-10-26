package com.pki.ca.dto.request;

import jakarta.validation.constraints.Min;

public class LockUserRequest {

    @Min(value = 1, message = "Lockout duration must be at least 1 minute")
    private Integer durationMinutes;

    private String reason;

    public LockUserRequest() {
    }

    public LockUserRequest(Integer durationMinutes, String reason) {
        this.durationMinutes = durationMinutes;
        this.reason = reason;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
