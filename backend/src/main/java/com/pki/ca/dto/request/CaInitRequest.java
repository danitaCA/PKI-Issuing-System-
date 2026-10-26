package com.pki.ca.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CaInitRequest {

    @NotBlank(message = "CA name is required")
    private String caName;

    @NotBlank(message = "Subject DN is required")
    private String subjectDn;

    @NotBlank(message = "Algorithm is required (e.g. RSA-4096, RSA-2048, ECDSA-P256)")
    private String algorithm;

    @NotBlank(message = "HSM slot ID is required")
    private String hsmSlotId;

    @NotBlank(message = "HSM key alias is required")
    private String hsmKeyAlias;

    public CaInitRequest() {
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
}
