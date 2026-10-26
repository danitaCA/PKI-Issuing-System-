package com.pki.ca.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EnrollmentSubmissionRequest {

    @NotBlank(message = "Profile code is required")
    @Size(max = 32, message = "Profile code must not exceed 32 characters")
    private String profileCode;

    @Min(value = 1, message = "Validity days must be at least 1")
    @Max(value = 3650, message = "Validity days must not exceed 3650")
    private Integer validityDays;

    @NotBlank(message = "CSR PEM content is required")
    @Size(max = 16384, message = "CSR PEM payload must not exceed 16KB")
    private String csrPem;

    public EnrollmentSubmissionRequest() {
    }

    public EnrollmentSubmissionRequest(String profileCode, Integer validityDays, String csrPem) {
        this.profileCode = profileCode;
        this.validityDays = validityDays;
        this.csrPem = csrPem;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }

    public String getCsrPem() {
        return csrPem;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }
}
