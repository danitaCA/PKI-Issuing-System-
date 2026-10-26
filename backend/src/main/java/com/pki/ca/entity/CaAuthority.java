package com.pki.ca.entity;

import com.pki.ca.enums.CaStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ca_authorities")
public class CaAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ca_name", length = 64, nullable = false, unique = true)
    private String caName;

    @Column(name = "subject_dn", length = 255, nullable = false)
    private String subjectDn;

    @Column(name = "issuer_dn", length = 255, nullable = false)
    private String issuerDn;

    @Column(name = "serial_number", length = 64, nullable = false, unique = true)
    private String serialNumber;

    @Column(name = "hsm_key_alias", length = 64, nullable = false, unique = true)
    private String hsmKeyAlias;

    @Column(name = "hsm_slot_id", length = 32, nullable = false)
    private String hsmSlotId;

    @Column(name = "algorithm", length = 32, nullable = false)
    private String algorithm;

    @Column(name = "certificate_pem", columnDefinition = "TEXT", nullable = false)
    private String certificatePem;

    @Column(name = "chain_pem", columnDefinition = "TEXT", nullable = false)
    private String chainPem;

    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private CaStatus status = CaStatus.ACTIVE;

    @Column(name = "crl_validity_hours", nullable = false)
    private int crlValidityHours = 72;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    public CaAuthority() {
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

    public String getCaName() {
        return caName;
    }

    public void setCaName(String caName) {
        this.caName = caName;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getHsmKeyAlias() {
        return hsmKeyAlias;
    }

    public void setHsmKeyAlias(String hsmKeyAlias) {
        this.hsmKeyAlias = hsmKeyAlias;
    }

    public String getHsmSlotId() {
        return hsmSlotId;
    }

    public void setHsmSlotId(String hsmSlotId) {
        this.hsmSlotId = hsmSlotId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public void setCertificatePem(String certificatePem) {
        this.certificatePem = certificatePem;
    }

    public String getChainPem() {
        return chainPem;
    }

    public void setChainPem(String chainPem) {
        this.chainPem = chainPem;
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

    public CaStatus getStatus() {
        return status;
    }

    public void setStatus(CaStatus status) {
        this.status = status;
    }

    public int getCrlValidityHours() {
        return crlValidityHours;
    }

    public void setCrlValidityHours(int crlValidityHours) {
        this.crlValidityHours = crlValidityHours;
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
        CaAuthority that = (CaAuthority) o;
        return Objects.equals(id, that.id) || Objects.equals(caName, that.caName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, caName);
    }
}
