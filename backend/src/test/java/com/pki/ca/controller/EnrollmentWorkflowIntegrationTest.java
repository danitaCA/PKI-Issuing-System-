package com.pki.ca.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.dto.request.ApproveRequestDto;
import com.pki.ca.dto.request.EnrollmentSubmissionRequest;
import com.pki.ca.dto.request.RejectRequestDto;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.entity.EnrollmentRequest;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.ProfileType;
import com.pki.ca.enums.RequestStatus;
import com.pki.ca.enums.RoleName;
import com.pki.ca.repository.CertificateProfileRepository;
import com.pki.ca.repository.EnrollmentRequestRepository;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import com.pki.ca.security.JwtTokenProvider;
import com.pki.ca.security.UserPrincipal;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnrollmentWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnrollmentRequestRepository enrollmentRepository;

    @Autowired
    private com.pki.ca.repository.RequestApprovalRepository approvalRepository;

    @Autowired
    private CertificateProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User endEntityUserA;
    private User endEntityUserB;
    private User raOperatorUser1;
    private User securityOfficerUser;
    private User auditorUser;

    private String tokenEndEntityA;
    private String tokenEndEntityB;
    private String tokenRaOperator1;
    private String tokenSecurityOfficer;
    private String tokenAuditor;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();

        ensureBaselineProfiles();

        Role roleEe = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        Role roleRa = roleRepository.findByName(RoleName.ROLE_RA_OPERATOR).orElseThrow();
        Role roleSec = roleRepository.findByName(RoleName.ROLE_SECURITY_OFFICER).orElseThrow();
        Role roleAud = roleRepository.findByName(RoleName.ROLE_AUDITOR).orElseThrow();

        endEntityUserA = getOrCreateUser("test_ee_user_a", "ee_a@pki.test", Set.of(roleEe));
        endEntityUserB = getOrCreateUser("test_ee_user_b", "ee_b@pki.test", Set.of(roleEe));
        raOperatorUser1 = getOrCreateUser("test_ra_operator_1", "ra1@pki.test", Set.of(roleRa));
        securityOfficerUser = getOrCreateUser("test_sec_officer", "sec@pki.test", Set.of(roleSec));
        auditorUser = getOrCreateUser("test_auditor_user", "aud@pki.test", Set.of(roleAud));

        tokenEndEntityA = tokenProvider.generateAccessToken(UserPrincipal.create(endEntityUserA));
        tokenEndEntityB = tokenProvider.generateAccessToken(UserPrincipal.create(endEntityUserB));
        tokenRaOperator1 = tokenProvider.generateAccessToken(UserPrincipal.create(raOperatorUser1));
        tokenSecurityOfficer = tokenProvider.generateAccessToken(UserPrincipal.create(securityOfficerUser));
        tokenAuditor = tokenProvider.generateAccessToken(UserPrincipal.create(auditorUser));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        enrollmentRepository.deleteAll();
    }

    private User getOrCreateUser(String username, String email, Set<Role> roles) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User(username, email, passwordEncoder.encode("TestPassword123!"), username);
            u.setRoles(roles);
            return userRepository.save(u);
        });
    }

    private void ensureBaselineProfiles() {
        if (!profileRepository.existsByProfileCode("TLS_SERVER")) {
            CertificateProfile p = new CertificateProfile();
            p.setProfileCode("TLS_SERVER");
            p.setProfileName("Internal TLS Server Certificate");
            p.setProfileType(ProfileType.SERVER_AUTH);
            p.setDefaultValidityDays(365);
            p.setMaxValidityDays(730);
            p.setAllowedKeyTypes(List.of("RSA-2048", "RSA-3072", "RSA-4096", "ECDSA-P256"));
            p.setKeyUsages(List.of("digitalSignature", "keyEncipherment"));
            p.setExtendedKeyUsages(List.of("1.3.6.1.5.5.7.3.1"));
            p.setRequireSan(true);
            p.setAllowWildcards(false);
            p.setRequiresDualApproval(false);
            p.setActive(true);
            profileRepository.save(p);
        }

        if (!profileRepository.existsByProfileCode("CODE_SIGNING")) {
            CertificateProfile p = new CertificateProfile();
            p.setProfileCode("CODE_SIGNING");
            p.setProfileName("Enterprise Code Signing Certificate");
            p.setProfileType(ProfileType.CODE_SIGNING);
            p.setDefaultValidityDays(365);
            p.setMaxValidityDays(365);
            p.setAllowedKeyTypes(List.of("RSA-3072", "RSA-4096", "ECDSA-P384"));
            p.setKeyUsages(List.of("digitalSignature"));
            p.setExtendedKeyUsages(List.of("1.3.6.1.5.5.7.3.3"));
            p.setRequireSan(false);
            p.setAllowWildcards(false);
            p.setRequiresDualApproval(true);
            p.setActive(true);
            profileRepository.save(p);
        }
    }

    private String createCsrPem(int keyBits, String subjectDn, String sanDns) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(keyBits, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        X500Principal subject = new X500Principal(subjectDn);
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());

        if (sanDns != null) {
            ExtensionsGenerator extGen = new ExtensionsGenerator();
            GeneralName gn = new GeneralName(GeneralName.dNSName, sanDns);
            extGen.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(gn));
            builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(csr);
        }
        return sw.toString();
    }

    @Test
    @DisplayName("End Entity can submit valid PKCS#10 CSR and receive PENDING_REVIEW response")
    void testSubmitValidCsr() throws Exception {
        String csrPem = createCsrPem(2048, "CN=portal.internal.corp, O=Acme Corp, C=US", "portal.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.profileCode").value("TLS_SERVER"))
                .andExpect(jsonPath("$.keyAlgorithm").value("RSA"))
                .andExpect(jsonPath("$.keyLength").value(2048))
                .andExpect(jsonPath("$.requesterUsername").value("test_ee_user_a"))
                .andExpect(jsonPath("$.fingerprint").isNotEmpty());

        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Duplicate CSR submission with same active key fingerprint returns 409 Conflict")
    void testDuplicateSubmissionConflict() throws Exception {
        String csrPem = createCsrPem(2048, "CN=duplicate.internal.corp", "duplicate.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        // First submission succeeds
        mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second submission with identical key returns 409
        mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PKI-40901"));
    }

    @Test
    @DisplayName("Auditor role cannot submit enrollment requests (Strict Read-Only boundary)")
    void testAuditorCannotSubmitRequest() throws Exception {
        String csrPem = createCsrPem(2048, "CN=auditor.internal.corp", "auditor.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenAuditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("End Entity B cannot view request owned by End Entity A (Ownership Fence)")
    void testOwnershipFence() throws Exception {
        String csrPem = createCsrPem(2048, "CN=private.internal.corp", "private.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        // End Entity A can view
        mockMvc.perform(get("/api/v1/enrollment/requests/" + requestId)
                        .header("Authorization", "Bearer " + tokenEndEntityA))
                .andExpect(status().isOk());

        // End Entity B is rejected with 403 Forbidden
        mockMvc.perform(get("/api/v1/enrollment/requests/" + requestId)
                        .header("Authorization", "Bearer " + tokenEndEntityB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Enforces Rule SoD-01: Requester cannot approve their own request (Self-Approval Prohibited)")
    void testSelfApprovalProhibited() throws Exception {
        // Create an RA operator who also submits an enrollment request
        Role roleRa = roleRepository.findByName(RoleName.ROLE_RA_OPERATOR).orElseThrow();
        User dualRoleUser = getOrCreateUser("test_ra_and_ee", "dual@pki.test", Set.of(roleRa));
        String dualToken = tokenProvider.generateAccessToken(UserPrincipal.create(dualRoleUser));

        String csrPem = createCsrPem(2048, "CN=self.internal.corp", "self.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + dualToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        // Same user attempts to approve their own request
        ApproveRequestDto approveDto = new ApproveRequestDto("Attempting self approval");
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + dualToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Self-approval prohibited: Requester cannot approve their own enrollment request."));
    }

    @Test
    @DisplayName("Authorized RA Operator can approve pending request and persist audit record")
    void testRaOperatorApproveRequest() throws Exception {
        String csrPem = createCsrPem(2048, "CN=valid-app.internal.corp", "valid-app.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        // Independent RA Operator approves
        ApproveRequestDto approveDto = new ApproveRequestDto("Identity verified in corporate asset inventory.");
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenRaOperator1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].reviewerUsername").value("test_ra_operator_1"));

        // Verify entity in DB
        EnrollmentRequest entity = enrollmentRepository.findById(requestId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(approvalRepository.findByRequestIdOrderByReviewedAtAsc(requestId)).hasSize(1);
    }

    @Test
    @DisplayName("Cannot approve already approved request (Invalid State Transition)")
    void testCannotApproveAlreadyApproved() throws Exception {
        String csrPem = createCsrPem(2048, "CN=double-approve.internal.corp", "double-approve.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        // First approval succeeds
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenRaOperator1))
                .andExpect(status().isOk());

        // Second approval attempt fails with 400 Bad Request
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenRaOperator1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40031"));
    }

    @Test
    @DisplayName("RA Operator can reject request with mandatory reason")
    void testRaOperatorRejectRequest() throws Exception {
        String csrPem = createCsrPem(2048, "CN=reject-me.internal.corp", "reject-me.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        RejectRequestDto rejectDto = new RejectRequestDto("INSUFFICIENT_DOMAIN_AUTHORITY", "Domain ownership could not be verified.");
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + tokenRaOperator1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_DOMAIN_AUTHORITY"));

        EnrollmentRequest entity = enrollmentRepository.findById(requestId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(entity.getRejectionReason()).isEqualTo("INSUFFICIENT_DOMAIN_AUTHORITY");
    }

    @Test
    @DisplayName("Dual-Approval Workflow (Code Signing): Enforces two distinct approvers across stages")
    void testDualApprovalWorkflow() throws Exception {
        // CODE_SIGNING profile requires RSA-3072 or higher
        String csrPem = createCsrPem(3072, "CN=Enterprise Code Signer, O=Acme, C=US", null);
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("CODE_SIGNING", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        // Stage 1 Approval by RA Operator 1 -> Transitions to PENDING_SECOND_APPROVAL
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenRaOperator1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRequestDto("Stage 1 verified."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SECOND_APPROVAL"))
                .andExpect(jsonPath("$.approvals[0].reviewStage").value("INITIAL_REVIEW"));

        // Stage 2 Attempt by same RA Operator 1 -> Rejected (SoD-03 violation)
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenRaOperator1))
                .andExpect(status().isForbidden()); // RA Operator lacks ROLE_SECURITY_OFFICER for second stage

        // Stage 2 Approval by Security Officer (Distinct individual) -> Transitions to APPROVED
        mockMvc.perform(post("/api/v1/ra/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + tokenSecurityOfficer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRequestDto("Stage 2 Security Officer sign-off."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[1].reviewStage").value("SECOND_APPROVAL"))
                .andExpect(jsonPath("$.approvals[1].reviewerUsername").value("test_sec_officer"));

        EnrollmentRequest finalReq = enrollmentRepository.findById(requestId).orElseThrow();
        assertThat(finalReq.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(approvalRepository.findByRequestIdOrderByReviewedAtAsc(requestId)).hasSize(2);
    }

    @Test
    @DisplayName("Requester can cancel their own pending enrollment request")
    void testRequesterCanCancelRequest() throws Exception {
        String csrPem = createCsrPem(2048, "CN=cancel-me.internal.corp", "cancel-me.internal.corp");
        EnrollmentSubmissionRequest req = new EnrollmentSubmissionRequest("TLS_SERVER", 365, csrPem);

        String responseStr = mockMvc.perform(post("/api/v1/enrollment/requests")
                        .header("Authorization", "Bearer " + tokenEndEntityA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseStr).get("requestId").asLong();

        mockMvc.perform(post("/api/v1/enrollment/requests/" + requestId + "/cancel")
                        .header("Authorization", "Bearer " + tokenEndEntityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        EnrollmentRequest entity = enrollmentRepository.findById(requestId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("Profiles endpoint returns active profiles")
    void testGetActiveProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + tokenEndEntityA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.profileCode == 'TLS_SERVER')]").exists())
                .andExpect(jsonPath("$[?(@.profileCode == 'CODE_SIGNING')]").exists());
    }
}
