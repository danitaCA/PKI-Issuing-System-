package com.pki.ca.enums;

/**
 * Standardized RFC 5280 Certificate Revocation Reason Codes (Section 5.3.1).
 */
public enum RevocationReasonCode {

    UNSPECIFIED(0, "unspecified", "Reason not explicitly specified"),
    KEY_COMPROMISE(1, "keyCompromise", "Subject private key is known or suspected to be compromised"),
    CA_COMPROMISE(2, "cACompromise", "CA private key is known or suspected to be compromised"),
    AFFILIATION_CHANGED(3, "affiliationChanged", "Subject name or other organizational identity information changed"),
    SUPERSEDED(4, "superseded", "Certificate has been superseded by a new replacement certificate"),
    CESSATION_OF_OPERATION(5, "cessationOfOperation", "Certified server, device, or service has been decommissioned"),
    CERTIFICATE_HOLD(6, "certificateHold", "Certificate temporarily suspended from active validity"),
    REMOVE_FROM_CRL(8, "removeFromCRL", "Certificate hold lifted; revoked entry removed from Delta CRL"),
    PRIVILEGE_WITHDRAWN(9, "privilegeWithdrawn", "Privilege or authorization granted to certificate has been withdrawn"),
    AA_COMPROMISE(10, "aACompromise", "Attribute Authority private key is compromised");

    private final int crlCode;
    private final String rfcName;
    private final String description;

    RevocationReasonCode(int crlCode, String rfcName, String description) {
        this.crlCode = crlCode;
        this.rfcName = rfcName;
        this.description = description;
    }

    public int getCrlCode() {
        return crlCode;
    }

    public String getRfcName() {
        return rfcName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAllowedForLeaf() {
        return this != CA_COMPROMISE && this != AA_COMPROMISE;
    }

    public static RevocationReasonCode fromCrlCode(int code) {
        for (RevocationReasonCode reason : values()) {
            if (reason.crlCode == code) {
                return reason;
            }
        }
        return UNSPECIFIED;
    }

    public static RevocationReasonCode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNSPECIFIED;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (RevocationReasonCode reason : values()) {
            if (reason.name().equalsIgnoreCase(normalized) || reason.rfcName.equalsIgnoreCase(value.trim())) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unsupported RFC 5280 revocation reason: " + value);
    }
}
