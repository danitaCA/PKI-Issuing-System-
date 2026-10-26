package com.pki.ca.service;

import com.pki.ca.dto.internal.ParsedCsrDto;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.enums.ProfileType;
import com.pki.ca.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileValidationServiceTest {

    private ProfileValidationService validationService;
    private CertificateProfile tlsProfile;

    @BeforeEach
    void setUp() {
        validationService = new ProfileValidationService();

        tlsProfile = new CertificateProfile();
        tlsProfile.setProfileCode("TLS_SERVER");
        tlsProfile.setProfileName("Internal TLS Server Certificate");
        tlsProfile.setProfileType(ProfileType.SERVER_AUTH);
        tlsProfile.setDefaultValidityDays(365);
        tlsProfile.setMaxValidityDays(730);
        tlsProfile.setAllowedKeyTypes(List.of("RSA-2048", "RSA-3072", "RSA-4096", "ECDSA-P256"));
        tlsProfile.setKeyUsages(List.of("digitalSignature", "keyEncipherment"));
        tlsProfile.setExtendedKeyUsages(List.of("1.3.6.1.5.5.7.3.1"));
        tlsProfile.setRequireSan(true);
        tlsProfile.setAllowWildcards(false);
        tlsProfile.setRequiresDualApproval(false);
        tlsProfile.setActive(true);
    }

    @Test
    @DisplayName("Should accept valid CSR compliant with profile constraints")
    void testValidCsr() {
        ParsedCsrDto dto = new ParsedCsrDto();
        dto.setKeySpec("RSA-2048");
        dto.setSubjectDn("CN=api.internal.corp, O=Acme Corp, C=US");
        dto.setSanEntries(List.of("DNS:api.internal.corp"));

        int validity = validationService.validateCsrAgainstProfile(tlsProfile, dto, 365);
        assertThat(validity).isEqualTo(365);
    }

    @Test
    @DisplayName("Should fall back to default validity when requested validity is null or 0")
    void testDefaultValidityFallback() {
        ParsedCsrDto dto = new ParsedCsrDto();
        dto.setKeySpec("RSA-2048");
        dto.setSubjectDn("CN=api.internal.corp");
        dto.setSanEntries(List.of("DNS:api.internal.corp"));

        int validityNull = validationService.validateCsrAgainstProfile(tlsProfile, dto, null);
        assertThat(validityNull).isEqualTo(365);

        int validityZero = validationService.validateCsrAgainstProfile(tlsProfile, dto, 0);
        assertThat(validityZero).isEqualTo(365);
    }

    @Test
    @DisplayName("Should reject inactive profile")
    void testRejectInactiveProfile() {
        tlsProfile.setActive(false);
        ParsedCsrDto dto = new ParsedCsrDto();

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dto, 365))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is inactive")
                .extracting("errorCode").isEqualTo("PKI-40020");
    }

    @Test
    @DisplayName("Should reject validity exceeding profile maxValidityDays")
    void testRejectExcessiveValidity() {
        ParsedCsrDto dto = new ParsedCsrDto();

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dto, 1000))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds profile maximum allowed duration")
                .extracting("errorCode").isEqualTo("PKI-40021");
    }

    @Test
    @DisplayName("Should reject disallowed key type or length (e.g. RSA-1024)")
    void testRejectDisallowedKeyType() {
        ParsedCsrDto dto = new ParsedCsrDto();
        dto.setKeySpec("RSA-1024");
        dto.setSubjectDn("CN=api.internal.corp");
        dto.setSanEntries(List.of("DNS:api.internal.corp"));

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dto, 365))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not permitted by profile")
                .extracting("errorCode").isEqualTo("PKI-40022");
    }

    @Test
    @DisplayName("Should reject CSR without SAN when requireSan is true")
    void testRejectMissingSan() {
        ParsedCsrDto dto = new ParsedCsrDto();
        dto.setKeySpec("RSA-2048");
        dto.setSubjectDn("CN=api.internal.corp");
        dto.setSanEntries(List.of());

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dto, 365))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("strictly requires at least one Subject Alternative Name")
                .extracting("errorCode").isEqualTo("PKI-40023");
    }

    @Test
    @DisplayName("Should reject wildcard in Subject DN or SAN when allowWildcards is false")
    void testRejectWildcards() {
        ParsedCsrDto dtoDnWildcard = new ParsedCsrDto();
        dtoDnWildcard.setKeySpec("RSA-2048");
        dtoDnWildcard.setSubjectDn("CN=*.internal.corp");
        dtoDnWildcard.setSanEntries(List.of("DNS:*.internal.corp"));

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dtoDnWildcard, 365))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Wildcard identifiers are strictly prohibited")
                .extracting("errorCode").isEqualTo("PKI-40024");

        ParsedCsrDto dtoSanWildcard = new ParsedCsrDto();
        dtoSanWildcard.setKeySpec("RSA-2048");
        dtoSanWildcard.setSubjectDn("CN=api.internal.corp");
        dtoSanWildcard.setSanEntries(List.of("DNS:*.internal.corp"));

        assertThatThrownBy(() -> validationService.validateCsrAgainstProfile(tlsProfile, dtoSanWildcard, 365))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Wildcard identifiers ('DNS:*.internal.corp') are strictly prohibited")
                .extracting("errorCode").isEqualTo("PKI-40024");
    }
}
