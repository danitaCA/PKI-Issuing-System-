package com.pki.ca.dto.response;

import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.enums.ProfileType;
import java.util.List;

public class CertificateProfileResponse {

    private Long id;
    private String profileCode;
    private String profileName;
    private ProfileType profileType;
    private int defaultValidityDays;
    private int maxValidityDays;
    private List<String> allowedKeyTypes;
    private List<String> keyUsages;
    private List<String> extendedKeyUsages;
    private boolean requireSan;
    private boolean allowWildcards;
    private boolean requiresDualApproval;
    private boolean isActive;

    public CertificateProfileResponse() {
    }

    public static CertificateProfileResponse fromEntity(CertificateProfile profile) {
        CertificateProfileResponse dto = new CertificateProfileResponse();
        dto.setId(profile.getId());
        dto.setProfileCode(profile.getProfileCode());
        dto.setProfileName(profile.getProfileName());
        dto.setProfileType(profile.getProfileType());
        dto.setDefaultValidityDays(profile.getDefaultValidityDays());
        dto.setMaxValidityDays(profile.getMaxValidityDays());
        dto.setAllowedKeyTypes(profile.getAllowedKeyTypes());
        dto.setKeyUsages(profile.getKeyUsages());
        dto.setExtendedKeyUsages(profile.getExtendedKeyUsages());
        dto.setRequireSan(profile.isRequireSan());
        dto.setAllowWildcards(profile.isAllowWildcards());
        dto.setRequiresDualApproval(profile.isRequiresDualApproval());
        dto.setActive(profile.isActive());
        return dto;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public ProfileType getProfileType() {
        return profileType;
    }

    public void setProfileType(ProfileType profileType) {
        this.profileType = profileType;
    }

    public int getDefaultValidityDays() {
        return defaultValidityDays;
    }

    public void setDefaultValidityDays(int defaultValidityDays) {
        this.defaultValidityDays = defaultValidityDays;
    }

    public int getMaxValidityDays() {
        return maxValidityDays;
    }

    public void setMaxValidityDays(int maxValidityDays) {
        this.maxValidityDays = maxValidityDays;
    }

    public List<String> getAllowedKeyTypes() {
        return allowedKeyTypes;
    }

    public void setAllowedKeyTypes(List<String> allowedKeyTypes) {
        this.allowedKeyTypes = allowedKeyTypes;
    }

    public List<String> getKeyUsages() {
        return keyUsages;
    }

    public void setKeyUsages(List<String> keyUsages) {
        this.keyUsages = keyUsages;
    }

    public List<String> getExtendedKeyUsages() {
        return extendedKeyUsages;
    }

    public void setExtendedKeyUsages(List<String> extendedKeyUsages) {
        this.extendedKeyUsages = extendedKeyUsages;
    }

    public boolean isRequireSan() {
        return requireSan;
    }

    public void setRequireSan(boolean requireSan) {
        this.requireSan = requireSan;
    }

    public boolean isAllowWildcards() {
        return allowWildcards;
    }

    public void setAllowWildcards(boolean allowWildcards) {
        this.allowWildcards = allowWildcards;
    }

    public boolean isRequiresDualApproval() {
        return requiresDualApproval;
    }

    public void setRequiresDualApproval(boolean requiresDualApproval) {
        this.requiresDualApproval = requiresDualApproval;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
