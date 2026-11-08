package com.pki.ca.entity;

import com.pki.ca.entity.converter.StringListJsonConverter;
import com.pki.ca.enums.CertificateStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ca_id", nullable = false)
    private CaAuthority caAuthority;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private EnrollmentRequest enrollmentRequest;

    @Column(name = "serial_number", length = 64, nullable = false, unique = true)
    private String serialNumber;

    @Column(name = "subject_dn", length = 255, nullable = false)
    private String subjectDn;

    @Column(name = "issuer_dn", length = 255, nullable = false)
    private String issuerDn;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "san_entries", columnDefinition = "TEXT")
    private List<String> sanEntries = new ArrayList<>();

    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    @Column(name = "certificate_pem", columnDefinition = "TEXT", nullable = false)
    private String certificatePem;

    @Column(name = "public_key_pem", columnDefinition = "TEXT", nullable = false)
    private String publicKeyPem;

    @Column(name = "public_key_fingerprint", length = 64, nullable = false)
    private String publicKeyFingerprint;

    @Column(name = "fingerprint_sha256", length = 64, nullable = false, unique = true)
    private String fingerprintSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private CertificateStatus status = CertificateStatus.ACTIVE;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 50)
    private String revocationReason;

    @Column(name = "revocation_invalidity_date")
    private LocalDateTime revocationInvalidityDate;

    @Column(name = "renewed_from_serial", length = 64)
    private String renewedFromSerial;

    @Column(name = "renewed_to_serial", length = 64)
    private String renewedToSerial;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    public Certificate() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CaAuthority getCaAuthority() {
        return caAuthority;
    }

    public void setCaAuthority(CaAuthority caAuthority) {
        this.caAuthority = caAuthority;
    }

    public EnrollmentRequest getEnrollmentRequest() {
        return enrollmentRequest;
    }

    public void setEnrollmentRequest(EnrollmentRequest enrollmentRequest) {
        this.enrollmentRequest = enrollmentRequest;
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

    public List<String> getSanEntries() {
        return sanEntries;
    }

    public void setSanEntries(List<String> sanEntries) {
        this.sanEntries = sanEntries;
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

    public String getCertificatePem() {
        return certificatePem;
    }

    public void setCertificatePem(String certificatePem) {
        this.certificatePem = certificatePem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void setFingerprintSha256(String fingerprintSha256) {
        this.fingerprintSha256 = fingerprintSha256;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateStatus status) {
        this.status = status;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public LocalDateTime getRevocationInvalidityDate() {
        return revocationInvalidityDate;
    }

    public void setRevocationInvalidityDate(LocalDateTime revocationInvalidityDate) {
        this.revocationInvalidityDate = revocationInvalidityDate;
    }

    public String getRenewedFromSerial() {
        return renewedFromSerial;
    }

    public void setRenewedFromSerial(String renewedFromSerial) {
        this.renewedFromSerial = renewedFromSerial;
    }

    public String getRenewedToSerial() {
        return renewedToSerial;
    }

    public void setRenewedToSerial(String renewedToSerial) {
        this.renewedToSerial = renewedToSerial;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
        Certificate that = (Certificate) o;
        return Objects.equals(id, that.id) || Objects.equals(serialNumber, that.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serialNumber);
    }
}
