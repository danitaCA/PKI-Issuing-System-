package com.pki.ca.dto.internal;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ParsedCsrDto {

    private String subjectDn;
    private List<String> sanEntries = new ArrayList<>();
    private String publicKeyAlgorithm;
    private int keyLength;
    private String keySpec; // e.g. "RSA-2048", "ECDSA-P256"
    private String publicKeyFingerprint;
    private PublicKey publicKey;

    public ParsedCsrDto() {
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

    public String getKeySpec() {
        return keySpec;
    }

    public void setKeySpec(String keySpec) {
        this.keySpec = keySpec;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }
}
