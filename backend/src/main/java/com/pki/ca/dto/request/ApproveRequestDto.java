package com.pki.ca.dto.request;

import jakarta.validation.constraints.Size;

public class ApproveRequestDto {

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    public ApproveRequestDto() {
    }

    public ApproveRequestDto(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
