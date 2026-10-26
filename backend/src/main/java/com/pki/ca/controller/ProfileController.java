package com.pki.ca.controller;

import com.pki.ca.dto.response.CertificateProfileResponse;
import com.pki.ca.service.CertificateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Certificate Profiles", description = "Query available certificate policy profiles")
public class ProfileController {

    private final CertificateProfileService profileService;

    public ProfileController(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all active certificate profiles available for enrollment")
    public ResponseEntity<List<CertificateProfileResponse>> getActiveProfiles() {
        List<CertificateProfileResponse> profiles = profileService.getActiveProfiles();
        return ResponseEntity.ok(profiles);
    }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get certificate profile details by code")
    public ResponseEntity<CertificateProfileResponse> getProfileByCode(@PathVariable String code) {
        CertificateProfileResponse profile = profileService.getProfileByCode(code);
        return ResponseEntity.ok(profile);
    }
}
