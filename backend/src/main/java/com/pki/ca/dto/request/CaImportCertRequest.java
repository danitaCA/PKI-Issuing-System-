package com.pki.ca.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaImportCertRequest {

    @NotNull(message = "CA ID is required")
    private Long caId;

    @NotBlank(message = "Certificate PEM is required")
    private String certificatePem;

    @NotBlank(message = "Root certificate PEM is required")
    private String rootCertificatePem;

    public CaImportCertRequest() {
    }

    public Long getCaId() {
        return caId;
    }

    public void setCaId(Long caId) {
        this.caId = caId;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public void setCertificatePem(String certificatePem) {
        this.certificatePem = certificatePem;
    }

    public String getRootCertificatePem() {
        return rootCertificatePem;
    }

    public void setRootCertificatePem(String rootCertificatePem) {
        this.rootCertificatePem = rootCertificatePem;
    }
}
