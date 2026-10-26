package com.pki.ca.entity;

import com.pki.ca.entity.converter.StringListJsonConverter;
import com.pki.ca.enums.ProfileType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "certificate_profiles")
public class CertificateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_code", length = 32, nullable = false, unique = true)
    private String profileCode;

    @Column(name = "profile_name", length = 64, nullable = false)
    private String profileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", length = 32, nullable = false)
    private ProfileType profileType;

    @Column(name = "default_validity_days", nullable = false)
    private int defaultValidityDays;

    @Column(name = "max_validity_days", nullable = false)
    private int maxValidityDays;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_key_types", columnDefinition = "TEXT", nullable = false)
    private List<String> allowedKeyTypes = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "key_usages", columnDefinition = "TEXT", nullable = false)
    private List<String> keyUsages = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "extended_key_usages", columnDefinition = "TEXT", nullable = false)
    private List<String> extendedKeyUsages = new ArrayList<>();

    @Column(name = "require_san", nullable = false)
    private boolean requireSan = true;

    @Column(name = "allow_wildcards", nullable = false)
    private boolean allowWildcards = false;

    @Column(name = "requires_dual_approval", nullable = false)
    private boolean requiresDualApproval = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    public CertificateProfile() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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
        CertificateProfile that = (CertificateProfile) o;
        return Objects.equals(profileCode, that.profileCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileCode);
    }
}
