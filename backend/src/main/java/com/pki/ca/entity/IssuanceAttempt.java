package com.pki.ca.entity;

import com.pki.ca.enums.IssuanceAttemptStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "issuance_attempts")
public class IssuanceAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private EnrollmentRequest enrollmentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ca_id", nullable = false)
    private CaAuthority caAuthority;

    @Column(name = "allocated_serial_number", length = 64, nullable = false, unique = true)
    private String allocatedSerialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private IssuanceAttemptStatus status;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "signed_certificate_pem", columnDefinition = "TEXT")
    private String signedCertificatePem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    public IssuanceAttempt() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EnrollmentRequest getEnrollmentRequest() {
        return enrollmentRequest;
    }

    public void setEnrollmentRequest(EnrollmentRequest enrollmentRequest) {
        this.enrollmentRequest = enrollmentRequest;
    }

    public CaAuthority getCaAuthority() {
        return caAuthority;
    }

    public void setCaAuthority(CaAuthority caAuthority) {
        this.caAuthority = caAuthority;
    }

    public String getAllocatedSerialNumber() {
        return allocatedSerialNumber;
    }

    public void setAllocatedSerialNumber(String allocatedSerialNumber) {
        this.allocatedSerialNumber = allocatedSerialNumber;
    }

    public IssuanceAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(IssuanceAttemptStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSignedCertificatePem() {
        return signedCertificatePem;
    }

    public void setSignedCertificatePem(String signedCertificatePem) {
        this.signedCertificatePem = signedCertificatePem;
    }

    public User getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(User initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssuanceAttempt that = (IssuanceAttempt) o;
        return Objects.equals(id, that.id) || Objects.equals(allocatedSerialNumber, that.allocatedSerialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, allocatedSerialNumber);
    }
}
