package com.pki.ca.dto.response;

import com.pki.ca.enums.RequestStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EnrollmentResponse {

    private Long requestId;
    private Long requesterId;
    private String requesterUsername;
    private String profileCode;
    private String profileName;
    private RequestStatus status;
    private String subjectDn;
    private SanEntriesDto sanEntries;
    private List<String> rawSanEntries = new ArrayList<>();
    private String csrPem;
    private String keyAlgorithm;
    private int keyLength;
    private String fingerprint;
    private int validityDays;
    private String rejectionReason;
    private String rejectionComment;
    private boolean requiresDualApproval;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private List<RequestApprovalResponse> approvals = new ArrayList<>();

    public EnrollmentResponse() {
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public void setRequesterUsername(String requesterUsername) {
        this.requesterUsername = requesterUsername;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public void setSubjectDn(String subjectDn) {
        this.subjectDn = subjectDn;
    }

    public SanEntriesDto getSanEntries() {
        return sanEntries;
    }

    public void setSanEntries(SanEntriesDto sanEntries) {
        this.sanEntries = sanEntries;
    }

    public List<String> getRawSanEntries() {
        return rawSanEntries;
    }

    public void setRawSanEntries(List<String> rawSanEntries) {
        this.rawSanEntries = rawSanEntries;
    }

    public String getCsrPem() {
        return csrPem;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(int validityDays) {
        this.validityDays = validityDays;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionComment() {
        return rejectionComment;
    }

    public void setRejectionComment(String rejectionComment) {
        this.rejectionComment = rejectionComment;
    }

    public boolean isRequiresDualApproval() {
        return requiresDualApproval;
    }

    public void setRequiresDualApproval(boolean requiresDualApproval) {
        this.requiresDualApproval = requiresDualApproval;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<RequestApprovalResponse> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<RequestApprovalResponse> approvals) {
        this.approvals = approvals;
    }
}
