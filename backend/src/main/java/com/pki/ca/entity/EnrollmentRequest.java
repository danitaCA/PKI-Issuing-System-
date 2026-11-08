package com.pki.ca.entity;

import com.pki.ca.entity.converter.StringListJsonConverter;
import com.pki.ca.enums.RequestStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "enrollment_requests")
public class EnrollmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private CertificateProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RequestStatus status = RequestStatus.PENDING_REVIEW;

    @Column(name = "subject_dn", length = 255, nullable = false)
    private String subjectDn;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "san_entries", columnDefinition = "TEXT")
    private List<String> sanEntries = new ArrayList<>();

    @Column(name = "csr_pem", columnDefinition = "TEXT", nullable = false)
    private String csrPem;

    @Column(name = "public_key_algorithm", length = 32, nullable = false)
    private String publicKeyAlgorithm;

    @Column(name = "key_length", nullable = false)
    private int keyLength;

    @Column(name = "public_key_fingerprint", length = 64, nullable = false)
    private String publicKeyFingerprint;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "rejection_comment", columnDefinition = "TEXT")
    private String rejectionComment;

    @Column(name = "renewed_certificate_serial", length = 64)
    private String renewedCertificateSerial;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("reviewedAt ASC")
    private List<RequestApproval> approvals = new ArrayList<>();

    public EnrollmentRequest() {
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

    public void addApproval(RequestApproval approval) {
        approvals.add(approval);
        approval.setRequest(this);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getRequester() {
        return requester;
    }

    public void setRequester(User requester) {
        this.requester = requester;
    }

    public CertificateProfile getProfile() {
        return profile;
    }

    public void setProfile(CertificateProfile profile) {
        this.profile = profile;
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

    public List<String> getSanEntries() {
        return sanEntries;
    }

    public void setSanEntries(List<String> sanEntries) {
        this.sanEntries = sanEntries;
    }

    public String getCsrPem() {
        return csrPem;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public String getPublicKeyAlgorithm() {
        return publicKeyAlgorithm;
    }

    public void setPublicKeyAlgorithm(String publicKeyAlgorithm) {
        this.publicKeyAlgorithm = publicKeyAlgorithm;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
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

    public String getRenewedCertificateSerial() {
        return renewedCertificateSerial;
    }

    public void setRenewedCertificateSerial(String renewedCertificateSerial) {
        this.renewedCertificateSerial = renewedCertificateSerial;
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

    public List<RequestApproval> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<RequestApproval> approvals) {
        this.approvals = approvals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnrollmentRequest that = (EnrollmentRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
