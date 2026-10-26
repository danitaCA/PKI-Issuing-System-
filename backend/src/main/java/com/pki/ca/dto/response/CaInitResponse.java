package com.pki.ca.dto.response;

public class CaInitResponse {
    private Long caId;
    private String status;
    private String caCsrPem;

    public CaInitResponse() {
    }

    public CaInitResponse(Long caId, String status, String caCsrPem) {
        this.caId = caId;
        this.status = status;
        this.caCsrPem = caCsrPem;
    }

    public Long getCaId() {
        return caId;
    }

    public void setCaId(Long caId) {
        this.caId = caId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCaCsrPem() {
        return caCsrPem;
    }

    public void setCaCsrPem(String caCsrPem) {
        this.caCsrPem = caCsrPem;
    }
}
