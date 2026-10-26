package com.pki.ca.dto.response;

import com.pki.ca.enums.ApprovalAction;
import com.pki.ca.enums.ReviewStage;
import java.time.LocalDateTime;

public class RequestApprovalResponse {

    private Long id;
    private Long reviewerId;
    private String reviewerUsername;
    private ApprovalAction action;
    private ReviewStage reviewStage;
    private String reviewerComment;
    private String clientIp;
    private LocalDateTime reviewedAt;

    public RequestApprovalResponse() {
    }

    public RequestApprovalResponse(Long id, Long reviewerId, String reviewerUsername,
                                   ApprovalAction action, ReviewStage reviewStage,
                                   String reviewerComment, String clientIp, LocalDateTime reviewedAt) {
        this.id = id;
        this.reviewerId = reviewerId;
        this.reviewerUsername = reviewerUsername;
        this.action = action;
        this.reviewStage = reviewStage;
        this.reviewerComment = reviewerComment;
        this.clientIp = clientIp;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerUsername() {
        return reviewerUsername;
    }

    public void setReviewerUsername(String reviewerUsername) {
        this.reviewerUsername = reviewerUsername;
    }

    public ApprovalAction getAction() {
        return action;
    }

    public void setAction(ApprovalAction action) {
        this.action = action;
    }

    public ReviewStage getReviewStage() {
        return reviewStage;
    }

    public void setReviewStage(ReviewStage reviewStage) {
        this.reviewStage = reviewStage;
    }

    public String getReviewerComment() {
        return reviewerComment;
    }

    public void setReviewerComment(String reviewerComment) {
        this.reviewerComment = reviewerComment;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
