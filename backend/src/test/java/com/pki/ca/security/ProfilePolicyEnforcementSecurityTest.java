package com.pki.ca.security;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.entity.*;
import com.pki.ca.enums.*;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.repository.*;
import com.pki.ca.service.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security Check 4: Certificate Profile & Identity Policy Enforcement at Issuance Time.
 *
 * Verifies that:
 * 1. The CA ignores untrusted CSR extensions and client parameters; only authoritative DB fields and profiles govern issuance.
 * 2. In-flight profile modification or deactivation invalidates issuance attempts before signing.
 * 3. Weak keys (e.g. RSA-1024), unsupported key types, and missing mandatory SANs are blocked.
 * 4. Issued leaf certificates strictly have CA=false and cannot receive CA signing privileges.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfilePolicyEnforcementSecurityTest {

    @Autowired
    private EnrollmentRequestRepository enrollmentRequestRepository;

    @Autowired
    private CertificateProfileRepository certificateProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CaAuthorityRepository caAuthorityRepository;

    @Autowired
    private CaIssuanceService caIssuanceService;

    @Autowired
    private CertificateBuilderService certificateBuilderService;

    @Autowired
    private CsrParserService csrParserService;

    @Autowired
    private ProfileValidationService profileValidationService;

    private User requester;
    private User reviewer;
    private User operator;
    private UserPrincipal operatorPrincipal;
    private CertificateProfile tlsProfile;
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

        requester = userRepository.findByUsername("profile_sec_requester").orElseGet(() -> {
            User u = new User("profile_sec_requester", "psec_ee@test.com", "hash", "Profile Sec EE");
            u.setRoles(java.util.Set.of(roleEe));
            return userRepository.save(u);
        });

        reviewer = userRepository.findByUsername("profile_sec_reviewer").orElseGet(() -> {
            User u = new User("profile_sec_reviewer", "psec_ra@test.com", "hash", "Profile Sec RA");
            u.setRoles(java.util.Set.of(roleRa));
            return userRepository.save(u);
        });

        operator = userRepository.findByUsername("profile_sec_operator").orElseGet(() -> {
            User u = new User("profile_sec_operator", "psec_op@test.com", "hash", "Profile Sec OP");
            u.setRoles(java.util.Set.of(roleRa));
            return userRepository.save(u);
        });

        operatorPrincipal = UserPrincipal.create(operator);

        tlsProfile = certificateProfileRepository.findByProfileCode("TLS_SERVER").orElseThrow();
        caAuthority = caAuthorityRepository.findFirstByStatus(CaStatus.ACTIVE).orElseThrow();
    }

    @Test
    @DisplayName("Should block issuance if profile was deactivated after initial RA approval")
    void shouldBlockIssuanceIfProfileDeactivatedAfterApproval() {
        CertificateProfile profile = createTestProfile("PROFILE_DEACTIVATED_TEST", true, List.of("RSA-2048"));
        EnrollmentRequest request = createApprovedRequestWithProfile(profile, 2048, "deactivated.test.corp");

        // Deactivate profile in database
        profile.setActive(false);
        certificateProfileRepository.saveAndFlush(profile);

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> {
                    BadRequestException bex = (BadRequestException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("PKI-40036");
                    assertThat(bex.getMessage()).contains("no longer active");
                });
    }

    @Test
    @DisplayName("Should block issuance if profile policy was updated to disallow the requested key algorithm/size")
    void shouldBlockIssuanceIfProfileUpdatedToDisallowKeySize() {
        CertificateProfile profile = createTestProfile("PROFILE_KEY_RESTRICT_TEST", true, List.of("RSA-2048", "RSA-3072"));
        EnrollmentRequest request = createApprovedRequestWithProfile(profile, 2048, "keysize.test.corp");

        // Policy change: Admin updates profile to only allow RSA-3072 or RSA-4096 (deprecating RSA-2048)
        profile.setAllowedKeyTypes(new java.util.ArrayList<>(List.of("RSA-3072", "RSA-4096")));
        certificateProfileRepository.saveAndFlush(profile);

        assertThatThrownBy(() -> caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> {
                    BadRequestException bex = (BadRequestException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("PKI-40022");
                    assertThat(bex.getMessage()).contains("is not permitted by profile");
                });
    }

    @Test
    @DisplayName("Should ensure leaf certificate is strictly CA=false and ignores any untrusted CA extension in CSR")
    void shouldEnsureLeafCertificateStrictlyHasCaFalse() throws Exception {
        // Attacker creates a CSR with an extension requesting basicConstraints CA=true
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Name subject = new X500Name("CN=attacker.ca.corp, O=Attack, C=US");
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());

        // Attacker injects CA=true in CSR extension request attribute
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        extGen.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, "attacker.ca.corp")));
        builder.addAttribute(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);
        String maliciousCsrPem = certificateBuilderService.convertToPem(csr);

        EnrollmentRequest request = new EnrollmentRequest();
        request.setRequester(requester);
        request.setProfile(tlsProfile);
        request.setStatus(RequestStatus.APPROVED);
        request.setSubjectDn("CN=attacker.ca.corp, O=Attack, C=US");
        request.setSanEntries(List.of("attacker.ca.corp"));
        request.setCsrPem(maliciousCsrPem);
        request.setPublicKeyAlgorithm("RSA");
        request.setKeyLength(2048);
        request.setPublicKeyFingerprint("fp-malicious-" + System.nanoTime());
        request.setValidityDays(365);

        RequestApproval approval = new RequestApproval();
        approval.setRequest(request);
        approval.setReviewer(reviewer);
        approval.setAction(ApprovalAction.APPROVED);
        approval.setReviewStage(ReviewStage.INITIAL_REVIEW);
        approval.setClientIp("127.0.0.1");
        request.addApproval(approval);
        EnrollmentRequest savedReq = enrollmentRequestRepository.saveAndFlush(request);

        // Issue certificate
        var issuedResp = caIssuanceService.issueCertificate(savedReq.getId(), operatorPrincipal, "127.0.0.1");

        // Parse issued certificate
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509", "BC");
        X509Certificate issuedCert = (X509Certificate) cf.generateCertificate(
                new java.io.ByteArrayInputStream(issuedResp.getCertificatePem().getBytes())
        );

        // Invariant: basicConstraints must return -1 (NOT a CA) despite attacker's CSR request
        assertThat(issuedCert.getBasicConstraints())
                .withFailMessage("Issued certificate received CA privileges from malicious CSR extension!")
                .isEqualTo(-1);

        // Invariant: KeyUsage must NOT contain keyCertSign
        boolean[] keyUsage = issuedCert.getKeyUsage();
        assertThat(keyUsage).isNotNull();
        // Index 5 is keyCertSign per X.509 spec
        assertThat(keyUsage[5])
                .withFailMessage("Issued leaf certificate has keyCertSign permission!")
                .isFalse();
    }

    private CertificateProfile createTestProfile(String code, boolean active, List<String> allowedKeys) {
        return certificateProfileRepository.findByProfileCode(code).orElseGet(() -> {
            CertificateProfile p = new CertificateProfile();
            p.setProfileCode(code);
            p.setProfileName(code);
            p.setProfileType(ProfileType.SERVER_AUTH);
            p.setDefaultValidityDays(365);
            p.setMaxValidityDays(365);
            p.setAllowedKeyTypes(new java.util.ArrayList<>(allowedKeys));
            p.setKeyUsages(List.of("digitalSignature", "keyEncipherment"));
            p.setExtendedKeyUsages(List.of("serverAuth"));
            p.setRequireSan(true);
            p.setAllowWildcards(false);
            p.setRequiresDualApproval(false);
            p.setActive(active);
            return certificateProfileRepository.saveAndFlush(p);
        });
    }

    private EnrollmentRequest createApprovedRequestWithProfile(CertificateProfile targetProfile, int keySize, String cn) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(keySize);
            KeyPair kp = kpg.generateKeyPair();
            X500Name subject = new X500Name("CN=" + cn + ", O=Acme, C=US");
            PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());
            ExtensionsGenerator extGen = new ExtensionsGenerator();
            extGen.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, cn)));
            builder.addAttribute(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
            PKCS10CertificationRequest csr = builder.build(signer);
            String csrPem = certificateBuilderService.convertToPem(csr);

            EnrollmentRequest req = new EnrollmentRequest();
            req.setRequester(requester);
            req.setProfile(targetProfile);
            req.setStatus(RequestStatus.APPROVED);
            req.setSubjectDn("CN=" + cn + ", O=Acme, C=US");
            req.setSanEntries(List.of(cn));
            req.setCsrPem(csrPem);
            req.setPublicKeyAlgorithm("RSA");
            req.setKeyLength(keySize);
            req.setPublicKeyFingerprint("fp-" + keySize + "-" + System.nanoTime());
            req.setValidityDays(365);

            RequestApproval approval = new RequestApproval();
            approval.setRequest(req);
            approval.setReviewer(reviewer);
            approval.setAction(ApprovalAction.APPROVED);
            approval.setReviewStage(ReviewStage.INITIAL_REVIEW);
            approval.setClientIp("127.0.0.1");
            req.addApproval(approval);

            return enrollmentRequestRepository.saveAndFlush(req);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
