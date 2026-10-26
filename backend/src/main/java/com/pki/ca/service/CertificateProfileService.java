package com.pki.ca.service;

import com.pki.ca.dto.response.CertificateProfileResponse;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.CertificateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CertificateProfileService {

    private final CertificateProfileRepository profileRepository;

    public CertificateProfileService(CertificateProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<CertificateProfileResponse> getActiveProfiles() {
        return profileRepository.findByIsActiveTrue().stream()
                .map(CertificateProfileResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificateProfileResponse getProfileByCode(String code) {
        CertificateProfile profile = profileRepository.findByProfileCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("CertificateProfile", "code", code));
        return CertificateProfileResponse.fromEntity(profile);
    }
}
