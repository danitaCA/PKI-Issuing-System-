package com.pki.ca.security;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SerialNumberGenerator;
import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.entity.*;
import com.pki.ca.enums.*;
import com.pki.ca.repository.*;
import com.pki.ca.service.CaAuthorityService;
import com.pki.ca.service.CaIssuanceService;
import com.pki.ca.service.CertificateBuilderService;
import com.pki.ca.service.CsrParserService;
import com.pki.ca.service.ProfileValidationService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security Check 3: Issuance Crash Recovery and Non-Atomic Signing Boundary Verification.
 *
 * Simulates system interruptions across the non-atomic boundary:
 * Boundary 1: HSM signature succeeds and attempt is recorded as SIGNED, but DB persistence interrupted.
 * Boundary 2: Certificate entity saved, but EnrollmentRequest status update interrupted.
 * Boundary 3: Idempotent repeat issuance on fully completed requests.
 */
@SpringBootTest
@ActiveProfiles("test")
class IssuanceCrashRecoveryIntegrationTest {

    @Autowired
    private EnrollmentRequestRepository enrollmentRequestRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private IssuanceAttemptRepository issuanceAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CertificateProfileRepository certificateProfileRepository;

    @Autowired
    private CaAuthorityRepository caAuthorityRepository;

    @Autowired
    private CaIssuanceService caIssuanceService;

    @Autowired
    private CertificateBuilderService certificateBuilderService;

    private User requester;
    private User reviewer;
    private User operator;
    private UserPrincipal operatorPrincipal;
    private CertificateProfile profile;
    private CaAuthority caAuthority;

    @BeforeAll
    static void initBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() {
        Role roleEe = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        Role roleRa = roleRepository.findByName(RoleName.ROLE_RA_OPERATOR).orElseThrow();

        requester = userRepository.findByUsername("crash_test_requester").orElseGet(() -> {
            User u = new User("crash_test_requester", "crash_ee@test.com", "hash", "Crash EE");
            u.setRoles(java.util.Set.of(roleEe));
            return userRepository.save(u);
        });

        reviewer = userRepository.findByUsername("crash_test_reviewer").orElseGet(() -> {
            User u = new User("crash_test_reviewer", "crash_ra@test.com", "hash", "Crash RA");
            u.setRoles(java.util.Set.of(roleRa));
            return userRepository.save(u);
        });

        operator = userRepository.findByUsername("crash_test_operator").orElseGet(() -> {
            User u = new User("crash_test_operator", "crash_op@test.com", "hash", "Crash OP");
            u.setRoles(java.util.Set.of(roleRa));
            return userRepository.save(u);
        });

        operatorPrincipal = UserPrincipal.create(operator);

        profile = certificateProfileRepository.findByProfileCode("TLS_SERVER").orElseThrow();
        caAuthority = caAuthorityRepository.findFirstByStatus(CaStatus.ACTIVE).orElseThrow();
    }

    @Test
    @DisplayName("Boundary 1: Should safely recover when HSM signature succeeded and attempt is SIGNED but certificate row missing")
    void shouldRecoverFromCrashAfterHsmSigningBeforeCertificateInsert() {
        // 1. Create and approve enrollment request
        EnrollmentRequest request = createApprovedRequest("api.crash1.internal.corp");

        // 2. Simulate pre-crash state: HSM signed certificate and attempt was persisted as SIGNED
        String allocatedSerial = "c1a500112233445566778899aabbccdd";
        BigInteger serialBigInt = new BigInteger(allocatedSerial, 16);
        X509Certificate signedCert = certificateBuilderService.buildAndSignCertificate(request, caAuthority, serialBigInt);
        String signedCertPem = certificateBuilderService.convertToPem(signedCert);

        IssuanceAttempt attempt = new IssuanceAttempt();
        attempt.setEnrollmentRequest(request);
        attempt.setCaAuthority(caAuthority);
        attempt.setAllocatedSerialNumber(allocatedSerial);
        attempt.setStatus(IssuanceAttemptStatus.SIGNED);
        attempt.setInitiatedBy(operator);
        attempt.setSignedCertificatePem(signedCertPem);
        issuanceAttemptRepository.saveAndFlush(attempt);

        // Verify pre-condition: No certificate row exists in database yet
        assertThat(certificateRepository.findByEnrollmentRequestId(request.getId())).isEmpty();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);

        // 3. Retry issuance via service
        CertificateResponse response = caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1");

        // 4. Verify post-recovery state
        assertThat(response).isNotNull();
        assertThat(response.getSerialNumber()).isEqualTo(allocatedSerial);
        assertThat(response.getSubjectDn()).contains("CN=api.crash1.internal.corp");

        // Verify attempt is now COMPLETED
        IssuanceAttempt reconciledAttempt = issuanceAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reconciledAttempt.getStatus()).isEqualTo(IssuanceAttemptStatus.COMPLETED);

        // Verify certificate is persisted
        Certificate cert = certificateRepository.findByEnrollmentRequestId(request.getId()).orElseThrow();
        assertThat(cert.getSerialNumber()).isEqualTo(allocatedSerial);

        // Verify request is now ISSUED
        EnrollmentRequest updatedReq = enrollmentRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updatedReq.getStatus()).isEqualTo(RequestStatus.ISSUED);
    }

    @Test
    @DisplayName("Boundary 2: Should safely recover when certificate row was saved but request status was not yet marked ISSUED")
    void shouldRecoverFromCrashAfterCertificateSavedBeforeRequestMarkedIssued() throws Exception {
        // 1. Create request in APPROVED status
        EnrollmentRequest request = createApprovedRequest("web.crash2.internal.corp");

        // 2. Simulate pre-crash state: Certificate row exists in database, but request is still APPROVED
        String allocatedSerial = "c2a500112233445566778899aabbccdd";
        BigInteger serialBigInt = new BigInteger(allocatedSerial, 16);
        X509Certificate signedCert = certificateBuilderService.buildAndSignCertificate(request, caAuthority, serialBigInt);
        String signedCertPem = certificateBuilderService.convertToPem(signedCert);
        byte[] certBytes = signedCert.getEncoded();
        String fingerprint = certificateBuilderService.computeSha256Fingerprint(certBytes);

        Certificate certificate = new Certificate();
        certificate.setCaAuthority(caAuthority);
        certificate.setEnrollmentRequest(request);
        certificate.setSerialNumber(allocatedSerial);
        certificate.setSubjectDn(signedCert.getSubjectX500Principal().getName());
        certificate.setIssuerDn(signedCert.getIssuerX500Principal().getName());
        certificate.setSanEntries(request.getSanEntries());
        certificate.setNotBefore(LocalDateTime.now().minusMinutes(5));
        certificate.setNotAfter(LocalDateTime.now().plusDays(365));
        certificate.setCertificatePem(signedCertPem);
        certificate.setPublicKeyPem(certificateBuilderService.convertToPem(signedCert.getPublicKey()));
        certificate.setPublicKeyFingerprint(request.getPublicKeyFingerprint());
        certificate.setFingerprintSha256(fingerprint);
        certificate.setStatus(CertificateStatus.ACTIVE);
        certificateRepository.saveAndFlush(certificate);

        // Pre-condition: Request is still APPROVED despite certificate existing
        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);

        // 3. Retry issuance via service
        CertificateResponse response = caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1");

        // 4. Verify post-recovery state
        assertThat(response).isNotNull();
        assertThat(response.getSerialNumber()).isEqualTo(allocatedSerial);

        // Verify request was transitioned to ISSUED and no duplicate certificates were inserted
        EnrollmentRequest updatedReq = enrollmentRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updatedReq.getStatus()).isEqualTo(RequestStatus.ISSUED);

        List<Certificate> certsForRequest = certificateRepository.findAll().stream()
                .filter(c -> c.getEnrollmentRequest().getId().equals(request.getId()))
                .toList();
        assertThat(certsForRequest).hasSize(1);
    }

    @Test
    @DisplayName("Boundary 3: Idempotent retries on completed requests return original certificate without duplicate issuance")
    void shouldHandleIdempotentRetriesOnCompletedIssuance() {
        EnrollmentRequest request = createApprovedRequest("svc.crash3.internal.corp");

        // Initial issuance
        CertificateResponse initialResponse = caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1");
        assertThat(initialResponse).isNotNull();

        // Immediate retry
        CertificateResponse retryResponse = caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1");
        assertThat(retryResponse).isNotNull();
        assertThat(retryResponse.getCertificateId()).isEqualTo(initialResponse.getCertificateId());
        assertThat(retryResponse.getSerialNumber()).isEqualTo(initialResponse.getSerialNumber());
        assertThat(retryResponse.getCertificatePem()).isEqualTo(initialResponse.getCertificatePem());
    }

    private EnrollmentRequest createApprovedRequest(String cn) {
        String csrPem = generateTestCsrPem(cn);

        EnrollmentRequest req = new EnrollmentRequest();
        req.setRequester(requester);
        req.setProfile(profile);
        req.setStatus(RequestStatus.APPROVED);
        req.setSubjectDn("CN=" + cn + ", O=Acme, C=US");
        req.setSanEntries(List.of(cn));
        req.setCsrPem(csrPem);
        req.setPublicKeyAlgorithm("RSA");
        req.setKeyLength(2048);
        req.setPublicKeyFingerprint("fp-" + System.nanoTime());
        req.setValidityDays(365);

        RequestApproval approval = new RequestApproval();
        approval.setRequest(req);
        approval.setReviewer(reviewer);
        approval.setAction(ApprovalAction.APPROVED);
        approval.setReviewStage(ReviewStage.INITIAL_REVIEW);
        approval.setClientIp("127.0.0.1");
        req.addApproval(approval);

        return enrollmentRequestRepository.saveAndFlush(req);
    }

    private String generateTestCsrPem(String cn) {
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048);
            java.security.KeyPair kp = kpg.generateKeyPair();
            org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name("CN=" + cn + ", O=Acme, C=US");
            org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder builder =
                    new org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());

            org.bouncycastle.asn1.x509.GeneralNames sanNames = new org.bouncycastle.asn1.x509.GeneralNames(
                    new org.bouncycastle.asn1.x509.GeneralName(org.bouncycastle.asn1.x509.GeneralName.dNSName, cn)
            );
            org.bouncycastle.asn1.x509.ExtensionsGenerator extGen = new org.bouncycastle.asn1.x509.ExtensionsGenerator();
            extGen.addExtension(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName, false, sanNames);
            builder.addAttribute(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

            org.bouncycastle.operator.ContentSigner signer =
                    new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
            org.bouncycastle.pkcs.PKCS10CertificationRequest csr = builder.build(signer);
            return certificateBuilderService.convertToPem(csr);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
