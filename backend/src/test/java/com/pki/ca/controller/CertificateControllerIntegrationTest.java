package com.pki.ca.controller;

import com.pki.ca.dto.response.CertificateDownloadDto;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.dto.response.CertificateSummaryResponse;
import com.pki.ca.entity.User;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.security.JwtTokenProvider;
import com.pki.ca.security.UserPrincipal;
import com.pki.ca.service.CaAuthorityService;
import com.pki.ca.service.CaIssuanceService;
import com.pki.ca.service.CertificateQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.pki.ca.repository.UserRepository userRepository;

    @Autowired
    private com.pki.ca.repository.RoleRepository roleRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @MockBean
    private CaIssuanceService caIssuanceService;

    @MockBean
    private CertificateQueryService certificateQueryService;

    @MockBean
    private CaAuthorityService caAuthorityService;

    private String raOperatorToken;
    private String endEntityToken;
    private String caAdminToken;

    @BeforeEach
    void setUp() {
        com.pki.ca.entity.Role roleRa = roleRepository.findByName(com.pki.ca.enums.RoleName.ROLE_RA_OPERATOR).orElseThrow();
        com.pki.ca.entity.Role roleEe = roleRepository.findByName(com.pki.ca.enums.RoleName.ROLE_END_ENTITY).orElseThrow();
        com.pki.ca.entity.Role roleAdmin = roleRepository.findByName(com.pki.ca.enums.RoleName.ROLE_CA_ADMIN).orElseThrow();

        User raUser = getOrCreateUser("ra_operator_test", "ra@test.com", java.util.Set.of(roleRa));
        User eeUser = getOrCreateUser("end_entity_test", "ee@test.com", java.util.Set.of(roleEe));
        User adminUser = getOrCreateUser("ca_admin_test", "admin@test.com", java.util.Set.of(roleAdmin));

        raOperatorToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(raUser));
        endEntityToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(eeUser));
        caAdminToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(adminUser));
    }

    private User getOrCreateUser(String username, String email, java.util.Set<com.pki.ca.entity.Role> roles) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User(username, email, passwordEncoder.encode("TestPassword123!"), username);
            u.setRoles(roles);
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("POST /api/v1/ca/requests/{id}/issue - 201 Created when invoked by RA Operator on approved request")
    void shouldAllowRaOperatorToIssueApprovedRequest() throws Exception {
        CertificateResponse response = new CertificateResponse();
        response.setCertificateId(501L);
        response.setSerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        response.setSubjectDn("CN=api.internal.corp, O=Acme, C=US");
        response.setIssuerDn("CN=Enterprise Issuing CA 01, O=Acme, C=US");
        response.setStatus("ACTIVE");
        response.setDownloadUri("/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download");

        when(caIssuanceService.issueCertificate(eq(1042L), any(), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/ca/requests/1042/issue")
                        .header("Authorization", "Bearer " + raOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateId").value(501))
                .andExpect(jsonPath("$.serialNumber").value("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/ca/requests/{id}/issue - 403 Forbidden when invoked by End Entity")
    void shouldDenyEndEntityFromIssuingCertificate() throws Exception {
        mockMvc.perform(post("/api/v1/ca/requests/1042/issue")
                        .header("Authorization", "Bearer " + endEntityToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/ca/requests/{id}/issue - 400 Bad Request when request is not approved")
    void shouldReturnBadRequestWhenRequestNotApproved() throws Exception {
        when(caIssuanceService.issueCertificate(eq(1042L), any(), anyString()))
                .thenThrow(new BadRequestException("Cannot issue certificate: Request ID 1042 is not approved.", "PKI-40031"));

        mockMvc.perform(post("/api/v1/ca/requests/1042/issue")
                        .header("Authorization", "Bearer " + raOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40031"));
    }

    @Test
    @DisplayName("GET /api/v1/certificates - 200 OK with paginated list for RA Operator")
    void shouldAllowRaOperatorToSearchCertificates() throws Exception {
        CertificateSummaryResponse summary = new CertificateSummaryResponse(
                501L, 1042L, "7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c",
                "CN=api.internal.corp, O=Acme, C=US", "CN=Enterprise Issuing CA 01, O=Acme, C=US",
                LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                "ACTIVE", "fingerprint-sha256", LocalDateTime.now()
        );
        when(certificateQueryService.searchCertificates(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/certificates")
                        .header("Authorization", "Bearer " + raOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].serialNumber").value("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"));
    }

    @Test
    @DisplayName("GET /api/v1/certificates - 403 Forbidden for End Entity")
    void shouldDenyEndEntityFromSearchingAllCertificates() throws Exception {
        mockMvc.perform(get("/api/v1/certificates")
                        .header("Authorization", "Bearer " + endEntityToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/certificates/my - 200 OK for End Entity")
    void shouldAllowEndEntityToQueryOwnCertificates() throws Exception {
        when(certificateQueryService.getMyCertificates(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/certificates/my")
                        .header("Authorization", "Bearer " + endEntityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/certificates/{serialNumber}/download - 200 OK publicly accessible without token")
    void shouldAllowPublicCertificateDownload() throws Exception {
        byte[] certBytes = "-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----".getBytes(StandardCharsets.UTF_8);
        when(certificateQueryService.downloadCertificate("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c", "pem"))
                .thenReturn(new CertificateDownloadDto("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c.crt", "application/x-pem-file", certBytes));

        mockMvc.perform(get("/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download")
                        .param("format", "pem"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-pem-file"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c.crt\""))
                .andExpect(content().bytes(certBytes));
    }

    @Test
    @DisplayName("GET /api/v1/ca/certificate - 200 OK publicly accessible without token")
    void shouldAllowPublicCaCertificateDownload() throws Exception {
        when(caAuthorityService.getCaCertificatePem())
                .thenReturn("-----BEGIN CERTIFICATE-----\nCA_CERT\n-----END CERTIFICATE-----");

        mockMvc.perform(get("/api/v1/ca/certificate"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-pem-file"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"issuing-ca.crt\""));
    }

    @Test
    @DisplayName("GET /api/v1/certificates/{serialNumber} - 200 OK when requester is authorized")
    void shouldAllowAuthorizedUserToGetCertificateDetails() throws Exception {
        CertificateResponse response = new CertificateResponse();
        response.setCertificateId(501L);
        response.setSerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        response.setSubjectDn("CN=api.internal.corp, O=Acme, C=US");
        response.setStatus("ACTIVE");

        when(certificateQueryService.getCertificateDetails(eq("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c")
                        .header("Authorization", "Bearer " + raOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/certificates/{serialNumber} - 403 Forbidden when non-owner End Entity accesses metadata")
    void shouldDenyNonOwnerEndEntityFromGettingCertificateDetails() throws Exception {
        when(certificateQueryService.getCertificateDetails(eq("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                        "Access denied: You are not authorized to view certificate metadata for serial 7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c."));

        mockMvc.perform(get("/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c")
                        .header("Authorization", "Bearer " + endEntityToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PKI-40300"));
    }

    @Test
    @DisplayName("GET /api/v1/certificates/{serialNumber}/download - 400 Bad Request on invalid format")
    void shouldRejectInvalidDownloadFormat() throws Exception {
        when(certificateQueryService.downloadCertificate(eq("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c"), eq("exe")))
                .thenThrow(new BadRequestException("Invalid download format: exe. Allowed formats: pem, der, p7b", "PKI-40017"));

        mockMvc.perform(get("/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download")
                        .param("format", "exe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40017"));
    }
}
