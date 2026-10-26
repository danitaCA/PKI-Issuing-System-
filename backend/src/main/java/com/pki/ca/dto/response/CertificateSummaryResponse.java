package com.pki.ca.dto.response;

import java.time.LocalDateTime;

public class CertificateSummaryResponse {
    private Long id;
    private Long requestId;
    private String serialNumber;
    private String subjectDn;
    private String issuerDn;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private String status;
    private String fingerprintSha256;
    private LocalDateTime createdAt;

    public CertificateSummaryResponse() {
    }

    public CertificateSummaryResponse(Long id, Long requestId, String serialNumber, String subjectDn,
                                      String issuerDn, LocalDateTime notBefore, LocalDateTime notAfter,
                                      String status, String fingerprintSha256, LocalDateTime createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.serialNumber = serialNumber;
        this.subjectDn = subjectDn;
        this.issuerDn = issuerDn;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.status = status;
        this.fingerprintSha256 = fingerprintSha256;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public void setSubjectDn(String subjectDn) {
        this.subjectDn = subjectDn;
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public void setIssuerDn(String issuerDn) {
        this.issuerDn = issuerDn;
    }

    public LocalDateTime getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(LocalDateTime notBefore) {
        this.notBefore = notBefore;
    }

    public LocalDateTime getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(LocalDateTime notAfter) {
        this.notAfter = notAfter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void setFingerprintSha256(String fingerprintSha256) {
        this.fingerprintSha256 = fingerprintSha256;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
