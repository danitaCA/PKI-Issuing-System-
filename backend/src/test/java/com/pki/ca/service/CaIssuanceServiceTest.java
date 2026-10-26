package com.pki.ca.service;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SerialNumberGenerator;
import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.entity.*;
import com.pki.ca.enums.*;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.repository.*;
import com.pki.ca.security.UserPrincipal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaIssuanceServiceTest {

    @Mock
    private EnrollmentRequestRepository enrollmentRequestRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private IssuanceAttemptRepository issuanceAttemptRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaAuthorityService caAuthorityService;

    @Mock
    private CertificateBuilderService certificateBuilderService;

    @Mock
    private SerialNumberGenerator serialNumberGenerator;

    @Mock
    private CryptoProvider cryptoProvider;

    @Mock
    private CsrParserService csrParserService;

    @Mock
    private ProfileValidationService profileValidationService;

    private CaIssuanceService caIssuanceService;

    private User requester;
    private User reviewer1;
    private User reviewer2;
    private User operatorUser;
    private UserPrincipal operatorPrincipal;
    private CertificateProfile profile;
    private EnrollmentRequest enrollmentRequest;
    private CaAuthority caAuthority;

    @BeforeAll
    static void initBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() {
        caIssuanceService = new CaIssuanceService(
                enrollmentRequestRepository,
                certificateRepository,
                issuanceAttemptRepository,
                userRepository,
                caAuthorityService,
                certificateBuilderService,
                serialNumberGenerator,
                cryptoProvider,
                csrParserService,
                profileValidationService
        );

        requester = new User("end_entity_a", "ee@test.com", "hash", "End Entity A");
        requester.setId(10L);

        reviewer1 = new User("ra_reviewer_1", "ra1@test.com", "hash", "RA Reviewer 1");
        reviewer1.setId(20L);

        reviewer2 = new User("sec_officer_1", "sec1@test.com", "hash", "Sec Officer 1");
        reviewer2.setId(30L);

        operatorUser = new User("ra_operator_exec", "exec@test.com", "hash", "RA Operator Exec");
        operatorUser.setId(40L);

        operatorPrincipal = new UserPrincipal(
                40L, "ra_operator_exec", "exec@test.com", "hash", "RA Operator Exec",
                UserStatus.ACTIVE, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_RA_OPERATOR"))
        );

        profile = new CertificateProfile();
        profile.setId(1L);
        profile.setProfileCode("TLS_SERVER");
        profile.setActive(true);
        profile.setMaxValidityDays(730);
        profile.setRequiresDualApproval(false);

        enrollmentRequest = new EnrollmentRequest();
        enrollmentRequest.setId(100L);
        enrollmentRequest.setRequester(requester);
        enrollmentRequest.setProfile(profile);
        enrollmentRequest.setStatus(RequestStatus.APPROVED);
        enrollmentRequest.setSubjectDn("CN=web.internal.corp, O=Acme, C=US");
        enrollmentRequest.setCsrPem("-----BEGIN CERTIFICATE REQUEST-----\ntest\n-----END CERTIFICATE REQUEST-----");
        enrollmentRequest.setPublicKeyFingerprint("test-fingerprint");
        enrollmentRequest.setValidityDays(365);

        RequestApproval approval1 = new RequestApproval();
        approval1.setRequest(enrollmentRequest);
        approval1.setReviewer(reviewer1);
        approval1.setAction(ApprovalAction.APPROVED);
        approval1.setReviewStage(ReviewStage.INITIAL_REVIEW);
        enrollmentRequest.addApproval(approval1);

        caAuthority = new CaAuthority();
        caAuthority.setId(1L);
        caAuthority.setCaName("Enterprise Issuing CA 01");
        caAuthority.setStatus(CaStatus.ACTIVE);
        caAuthority.setHsmKeyAlias("issuing-ca-key-01");
        caAuthority.setNotBefore(LocalDateTime.now().minusYears(1));
        caAuthority.setNotAfter(LocalDateTime.now().plusYears(10));
    }

    @Test
    @DisplayName("Should successfully issue certificate for fully approved single-approval request")
    void shouldIssueCertificateSuccessfully() throws Exception {
        when(userRepository.findById(40L)).thenReturn(Optional.of(operatorUser));
        when(enrollmentRequestRepository.findById(100L)).thenReturn(Optional.of(enrollmentRequest));
        when(certificateRepository.findByEnrollmentRequestId(100L)).thenReturn(Optional.empty());
        when(caAuthorityService.getActiveCa()).thenReturn(caAuthority);
        when(cryptoProvider.keyExists("issuing-ca-key-01")).thenReturn(true);
        when(issuanceAttemptRepository.findTopByEnrollmentRequestIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(serialNumberGenerator.generateSerialNumberHex()).thenReturn("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        when(certificateRepository.existsBySerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c")).thenReturn(false);
        when(issuanceAttemptRepository.findByAllocatedSerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c")).thenReturn(Optional.empty());

        com.pki.ca.dto.internal.ParsedCsrDto parsedCsr = new com.pki.ca.dto.internal.ParsedCsrDto();
        parsedCsr.setSubjectDn("CN=web.internal.corp, O=Acme, C=US");
        parsedCsr.setPublicKey(mock(java.security.PublicKey.class));
        parsedCsr.setPublicKeyAlgorithm("RSA");
        parsedCsr.setKeyLength(2048);
        parsedCsr.setKeySpec("RSA-2048");
        parsedCsr.setPublicKeyFingerprint("test-fingerprint");
        parsedCsr.setSanEntries(List.of("web.internal.corp"));
        when(csrParserService.parseAndVerifyCsr(anyString())).thenReturn(parsedCsr);

        IssuanceAttempt savedAttempt = new IssuanceAttempt();
        savedAttempt.setId(1L);
        savedAttempt.setAllocatedSerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        when(issuanceAttemptRepository.saveAndFlush(any())).thenReturn(savedAttempt);

        X509Certificate mockCert = mock(X509Certificate.class);
        when(mockCert.getEncoded()).thenReturn(new byte[]{1, 2, 3});
        when(mockCert.getNotBefore()).thenReturn(new java.util.Date());
        when(mockCert.getNotAfter()).thenReturn(new java.util.Date(System.currentTimeMillis() + 86400000L));
        when(mockCert.getSubjectX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=web.internal.corp, O=Acme, C=US"));
        when(mockCert.getIssuerX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Enterprise Issuing CA 01, O=Acme, C=US"));
        java.security.PublicKey mockPublicKey = mock(java.security.PublicKey.class);
        when(mockCert.getPublicKey()).thenReturn(mockPublicKey);

        when(certificateBuilderService.buildAndSignCertificate(eq(enrollmentRequest), eq(caAuthority), any(BigInteger.class)))
                .thenReturn(mockCert);
        when(certificateBuilderService.convertToPem(mockCert)).thenReturn("-----BEGIN CERTIFICATE-----\nCERT\n-----END CERTIFICATE-----");
        when(certificateBuilderService.computeSha256Fingerprint(any())).thenReturn("cert-fingerprint-sha256");
        when(certificateBuilderService.convertToPem(any(java.security.PublicKey.class))).thenReturn("-----BEGIN PUBLIC KEY-----\nKEY\n-----END PUBLIC KEY-----");

        Certificate savedCert = new Certificate();
        savedCert.setId(501L);
        savedCert.setEnrollmentRequest(enrollmentRequest);
        savedCert.setSerialNumber("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        savedCert.setSubjectDn("CN=web.internal.corp, O=Acme, C=US");
        savedCert.setIssuerDn("CN=Enterprise Issuing CA 01, O=Acme, C=US");
        savedCert.setStatus(CertificateStatus.ACTIVE);
        savedCert.setFingerprintSha256("cert-fingerprint-sha256");
        savedCert.setCertificatePem("-----BEGIN CERTIFICATE-----\nCERT\n-----END CERTIFICATE-----");
        savedCert.setCreatedAt(LocalDateTime.now());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(savedCert);

        // Execute issuance
        CertificateResponse response = caIssuanceService.issueCertificate(100L, operatorPrincipal, "127.0.0.1");

        // Verify assertions
        assertThat(response).isNotNull();
        assertThat(response.getCertificateId()).isEqualTo(501L);
        assertThat(response.getSerialNumber()).isEqualTo("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(enrollmentRequest.getStatus()).isEqualTo(RequestStatus.ISSUED);
    }

    @Test
    @DisplayName("Should reject issuance when request is in PENDING_REVIEW state")
    void shouldRejectIssuanceWhenRequestNotApproved() {
        enrollmentRequest.setStatus(RequestStatus.PENDING_REVIEW);
        when(userRepository.findById(40L)).thenReturn(Optional.of(operatorUser));
        when(enrollmentRequestRepository.findById(100L)).thenReturn(Optional.of(enrollmentRequest));
        when(certificateRepository.findByEnrollmentRequestId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(100L, operatorPrincipal, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not approved");
    }

    @Test
    @DisplayName("Should block issuance and detect SoD violation if request was self-approved")
    void shouldBlockSelfApprovedRequest() {
        // Tamper request so that requester approved their own request
        enrollmentRequest.getApprovals().clear();
        RequestApproval selfApproval = new RequestApproval();
        selfApproval.setRequest(enrollmentRequest);
        selfApproval.setReviewer(requester); // Same user as requester!
        selfApproval.setAction(ApprovalAction.APPROVED);
        enrollmentRequest.addApproval(selfApproval);

        when(userRepository.findById(40L)).thenReturn(Optional.of(operatorUser));
        when(enrollmentRequestRepository.findById(100L)).thenReturn(Optional.of(enrollmentRequest));
        when(certificateRepository.findByEnrollmentRequestId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(100L, operatorPrincipal, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Separation of duties violation");
    }

    @Test
    @DisplayName("Should enforce dual approval and reject issuance if only 1 approver reviewed")
    void shouldEnforceDualApprovalRequirement() {
        profile.setRequiresDualApproval(true);
        // Only 1 approval present
        when(userRepository.findById(40L)).thenReturn(Optional.of(operatorUser));
        when(enrollmentRequestRepository.findById(100L)).thenReturn(Optional.of(enrollmentRequest));
        when(certificateRepository.findByEnrollmentRequestId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(100L, operatorPrincipal, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Profile requires dual approval");
    }

    @Test
    @DisplayName("Should return existing certificate idempotently if already issued")
    void shouldReturnExistingCertificateIdempotently() {
        Certificate existing = new Certificate();
        existing.setId(777L);
        existing.setEnrollmentRequest(enrollmentRequest);
        existing.setSerialNumber("existing-serial-1234");
        existing.setStatus(CertificateStatus.ACTIVE);
        existing.setSubjectDn("CN=web.internal.corp, O=Acme, C=US");
        existing.setIssuerDn("CN=Enterprise Issuing CA 01, O=Acme, C=US");
        existing.setFingerprintSha256("existing-sha256");

        when(userRepository.findById(40L)).thenReturn(Optional.of(operatorUser));
        when(enrollmentRequestRepository.findById(100L)).thenReturn(Optional.of(enrollmentRequest));
        when(certificateRepository.findByEnrollmentRequestId(100L)).thenReturn(Optional.of(existing));

        CertificateResponse response = caIssuanceService.issueCertificate(100L, operatorPrincipal, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getCertificateId()).isEqualTo(777L);
        assertThat(response.getSerialNumber()).isEqualTo("existing-serial-1234");
        // Verify HSM signing was NOT invoked
        verify(certificateBuilderService, never()).buildAndSignCertificate(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject issuance request from unauthorized role (e.g. END_ENTITY)")
    void shouldRejectUnauthorizedRole() {
        UserPrincipal eePrincipal = new UserPrincipal(
                10L, "end_entity_a", "ee@test.com", "hash", "End Entity A",
                UserStatus.ACTIVE, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_END_ENTITY"))
        );

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(100L, eePrincipal, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Certificate issuance requires ROLE_RA_OPERATOR or ROLE_CA_ADMIN");
    }
}
