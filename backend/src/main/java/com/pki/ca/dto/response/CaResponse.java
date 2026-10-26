package com.pki.ca.dto.response;

import java.time.LocalDateTime;

public class CaResponse {
    private Long id;
    private String caName;
    private String status;
    private String subjectDn;
    private String issuerDn;
    private String serialNumber;
    private String algorithm;
    private String hsmSlotId;
    private String hsmKeyAlias;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private int crlValidityHours;

    public CaResponse() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getHsmSlotId() {
        return hsmSlotId;
    }

    public void setHsmSlotId(String hsmSlotId) {
        this.hsmSlotId = hsmSlotId;
    }

    public String getHsmKeyAlias() {
        return hsmKeyAlias;
    }

    public void setHsmKeyAlias(String hsmKeyAlias) {
        this.hsmKeyAlias = hsmKeyAlias;
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

    public int getCrlValidityHours() {
        return crlValidityHours;
    }

    public void setCrlValidityHours(int crlValidityHours) {
        this.crlValidityHours = crlValidityHours;
    }
}
