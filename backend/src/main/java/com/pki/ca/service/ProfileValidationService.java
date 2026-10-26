package com.pki.ca.service;

import com.pki.ca.dto.internal.ParsedCsrDto;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileValidationService {

    private static final Logger log = LoggerFactory.getLogger(ProfileValidationService.class);

    /**
     * Validates a parsed CSR against a certificate profile's constraints.
     *
     * @param profile The target certificate profile
     * @param parsedCsr The cryptographically verified CSR details
     * @param requestedValidityDays Requested validity in days, or null for default
     * @return Resolved validity days
     */
    public int validateCsrAgainstProfile(CertificateProfile profile, ParsedCsrDto parsedCsr, Integer requestedValidityDays) {
        if (!profile.isActive()) {
            throw new BadRequestException("Certificate profile '" + profile.getProfileCode() + "' is inactive and cannot accept enrollment requests", "PKI-40020");
        }

        // 1. Resolve and validate validity duration
        int effectiveValidityDays;
        if (requestedValidityDays == null || requestedValidityDays <= 0) {
            effectiveValidityDays = profile.getDefaultValidityDays();
        } else {
            if (requestedValidityDays > profile.getMaxValidityDays()) {
                throw new BadRequestException(
                        "Requested validity of " + requestedValidityDays + " days exceeds profile maximum allowed duration of " + profile.getMaxValidityDays() + " days",
                        "PKI-40021"
                );
            }
            effectiveValidityDays = requestedValidityDays;
        }

        // 2. Validate cryptographic key specification
        String keySpec = parsedCsr.getKeySpec();
        List<String> allowedKeyTypes = profile.getAllowedKeyTypes();
        if (allowedKeyTypes != null && !allowedKeyTypes.isEmpty()) {
            boolean keyAllowed = allowedKeyTypes.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(keySpec));
            if (!keyAllowed) {
                log.warn("Rejected CSR with keySpec '{}' against profile '{}'. Allowed: {}", keySpec, profile.getProfileCode(), allowedKeyTypes);
                throw new BadRequestException(
                        "Public key type/length '" + keySpec + "' is not permitted by profile '" + profile.getProfileCode() + "'. Allowed key types: " + allowedKeyTypes,
                        "PKI-40022"
                );
            }
        }

        // 3. Validate SAN requirements
        List<String> sanEntries = parsedCsr.getSanEntries();
        if (profile.isRequireSan()) {
            if (sanEntries == null || sanEntries.isEmpty()) {
                throw new BadRequestException(
                        "Certificate profile '" + profile.getProfileCode() + "' strictly requires at least one Subject Alternative Name (SAN)",
                        "PKI-40023"
                );
            }
        }

        // 4. Validate wildcard restrictions
        if (!profile.isAllowWildcards()) {
            if (parsedCsr.getSubjectDn() != null && parsedCsr.getSubjectDn().contains("*")) {
                throw new BadRequestException(
                        "Wildcard identifiers are strictly prohibited in Subject DN for profile '" + profile.getProfileCode() + "'",
                        "PKI-40024"
                );
            }
            if (sanEntries != null) {
                for (String san : sanEntries) {
                    if (san.contains("*")) {
                        throw new BadRequestException(
                                "Wildcard identifiers ('" + san + "') are strictly prohibited in SANs for profile '" + profile.getProfileCode() + "'",
                                "PKI-40024"
                        );
                    }
                }
            }
        }

        return effectiveValidityDays;
    }
}
