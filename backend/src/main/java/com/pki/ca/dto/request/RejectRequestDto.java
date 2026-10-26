package com.pki.ca.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRequestDto {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 255, message = "Rejection reason must not exceed 255 characters")
    private String rejectionReason;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    public RejectRequestDto() {
    }

    public RejectRequestDto(String rejectionReason, String comment) {
        this.rejectionReason = rejectionReason;
        this.comment = comment;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
