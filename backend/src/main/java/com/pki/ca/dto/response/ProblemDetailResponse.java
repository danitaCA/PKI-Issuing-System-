package com.pki.ca.dto.response;

import java.time.Instant;
import java.util.List;

public class ProblemDetailResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String timestamp;
    private String errorCode;
    private List<InvalidParam> invalidParams;

    public ProblemDetailResponse() {
        this.timestamp = Instant.now().toString();
    }

    public ProblemDetailResponse(String type, String title, int status, String detail, String instance, String errorCode) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
        this.timestamp = Instant.now().toString();
    }

    public static class InvalidParam {
        private String field;
        private String reason;

        public InvalidParam() {
        }

        public InvalidParam(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public List<InvalidParam> getInvalidParams() {
        return invalidParams;
    }

    public void setInvalidParams(List<InvalidParam> invalidParams) {
        this.invalidParams = invalidParams;
    }
}
