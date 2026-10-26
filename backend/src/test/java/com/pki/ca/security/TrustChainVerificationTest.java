package com.pki.ca.security;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.entity.*;
import com.pki.ca.enums.*;
import com.pki.ca.repository.*;
import com.pki.ca.service.CaIssuanceService;
import com.pki.ca.service.CertificateBuilderService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.Extension;
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

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security Check 5: End-to-End PKIX Trust Chain and Constraints Verification.
 *
 * Confirms that:
 * 1. An issued leaf certificate chains authoritatively to the Subordinate Issuing CA and isolated Root CA.
 * 2. Java standard CertPathValidator (PKIX) validates the full certificate path without errors.
 * 3. BasicConstraints path length constraints are strictly enforced:
 *    - Root CA: cA = true, pathLen = 1
 *    - Subordinate CA: cA = true, pathLen = 0
 *    - Leaf Certificate: cA = false (BasicConstraints.getBasicConstraints() == -1)
 * 4. KeyUsage bits are correct:
 *    - CAs have keyCertSign and cRLSign
 *    - Leaf does NOT have keyCertSign
 * 5. AuthorityKeyIdentifier (AKI) on Leaf matches SubjectKeyIdentifier (SKI) on Subordinate CA.
 * 6. AuthorityKeyIdentifier (AKI) on Subordinate CA matches SubjectKeyIdentifier (SKI) on Root CA.
 * 7. Validity periods are strictly nested: leaf.notAfter <= subCA.notAfter <= rootCA.notAfter.
 */
@SpringBootTest
@ActiveProfiles("test")
class TrustChainVerificationTest {

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

        requester = userRepository.findByUsername("chain_sec_requester").orElseGet(() -> {
            User u = new User("chain_sec_requester", "chain_ee@test.com", "hash", "Chain EE");
            u.setRoles(Set.of(roleEe));
            return userRepository.save(u);
        });

        reviewer = userRepository.findByUsername("chain_sec_reviewer").orElseGet(() -> {
            User u = new User("chain_sec_reviewer", "chain_ra@test.com", "hash", "Chain RA");
            u.setRoles(Set.of(roleRa));
            return userRepository.save(u);
        });

        operator = userRepository.findByUsername("chain_sec_operator").orElseGet(() -> {
            User u = new User("chain_sec_operator", "chain_op@test.com", "hash", "Chain OP");
            u.setRoles(Set.of(roleRa));
            return userRepository.save(u);
        });

        operatorPrincipal = UserPrincipal.create(operator);

        profile = certificateProfileRepository.findByProfileCode("TLS_SERVER").orElseThrow();
        caAuthority = caAuthorityRepository.findFirstByStatus(CaStatus.ACTIVE).orElseThrow();
    }

    @Test
    @DisplayName("Should validate full PKIX trust chain: Leaf -> Subordinate Issuing CA -> Root CA")
    void shouldValidateFullPkixTrustChainAndConstraints() throws Exception {
        // 1. Submit and issue a certificate
        EnrollmentRequest request = createApprovedRequest("secure.chain.test.corp");
        CertificateResponse issuedResp = caIssuanceService.issueCertificate(request.getId(), operatorPrincipal, "127.0.0.1");

        // 2. Parse certificates from chain bundle
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate leafCert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(issuedResp.getCertificatePem().getBytes())
        );

        // Parse chain PEM bundle (contains Subordinate CA cert and Root CA cert)
        Collection<? extends java.security.cert.Certificate> chainCerts = cf.generateCertificates(
                new ByteArrayInputStream(caAuthority.getChainPem().getBytes())
        );
        List<X509Certificate> certList = new ArrayList<>();
        for (java.security.cert.Certificate c : chainCerts) {
            certList.add((X509Certificate) c);
        }
        assertThat(certList).hasSize(2);

        X509Certificate subCaCert = certList.get(0);
        X509Certificate rootCaCert = certList.get(1);

        // 3. Cryptographic Signature Link Verification
        leafCert.verify(subCaCert.getPublicKey());
        subCaCert.verify(rootCaCert.getPublicKey());
        rootCaCert.verify(rootCaCert.getPublicKey()); // Self-signed trust anchor

        // 4. Standard PKIX CertPath Validation using Java Security CertPathValidator
        CertPath certPath = cf.generateCertPath(List.of(leafCert, subCaCert));
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");

        TrustAnchor trustAnchor = new TrustAnchor(rootCaCert, null);
        PKIXParameters params = new PKIXParameters(Collections.singleton(trustAnchor));
        params.setRevocationEnabled(false); // Revocation belongs to Phase 5 CRL/OCSP

        CertPathValidatorResult validationResult = validator.validate(certPath, params);
        assertThat(validationResult).isNotNull();
        assertThat(validationResult).isInstanceOf(PKIXCertPathValidatorResult.class);

        // 5. Verify BasicConstraints
        // Root CA: cA = true, pathLen >= 1
        assertThat(rootCaCert.getBasicConstraints()).isGreaterThanOrEqualTo(1);

        // Subordinate CA: cA = true, pathLen == 0
        assertThat(subCaCert.getBasicConstraints()).isEqualTo(0);

        // Leaf: cA = false (returns -1)
        assertThat(leafCert.getBasicConstraints()).isEqualTo(-1);

        // 6. Verify KeyUsage Bits
        // KeyUsage indices: 0=digitalSignature, 1=nonRepudiation, 2=keyEncipherment, 3=dataEncipherment, 4=keyAgreement, 5=keyCertSign, 6=cRLSign
        boolean[] rootKu = rootCaCert.getKeyUsage();
        assertThat(rootKu[5]).as("Root CA must have keyCertSign").isTrue();
        assertThat(rootKu[6]).as("Root CA must have cRLSign").isTrue();

        boolean[] subKu = subCaCert.getKeyUsage();
        assertThat(subKu[5]).as("Subordinate CA must have keyCertSign").isTrue();
        assertThat(subKu[6]).as("Subordinate CA must have cRLSign").isTrue();

        boolean[] leafKu = leafCert.getKeyUsage();
        assertThat(leafKu[5]).as("Leaf certificate must NOT have keyCertSign").isFalse();
        assertThat(leafKu[6]).as("Leaf certificate must NOT have cRLSign").isFalse();
        assertThat(leafKu[0]).as("TLS Server certificate must have digitalSignature").isTrue();
        assertThat(leafKu[2]).as("TLS Server certificate must have keyEncipherment").isTrue();

        // 7. Verify SKI / AKI Extension Chaining
        byte[] leafAkiRaw = leafCert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
        byte[] subSkiRaw = subCaCert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
        byte[] subAkiRaw = subCaCert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
        byte[] rootSkiRaw = rootCaCert.getExtensionValue(Extension.subjectKeyIdentifier.getId());

        assertThat(leafAkiRaw).isNotNull();
        assertThat(subSkiRaw).isNotNull();
        assertThat(subAkiRaw).isNotNull();
        assertThat(rootSkiRaw).isNotNull();

        // AuthorityKeyIdentifier on Leaf must point to SubjectKeyIdentifier on SubCA
        AuthorityKeyIdentifier leafAki = AuthorityKeyIdentifier.getInstance(
                org.bouncycastle.asn1.ASN1OctetString.getInstance(leafAkiRaw).getOctets()
        );
        SubjectKeyIdentifier subSki = SubjectKeyIdentifier.getInstance(
                org.bouncycastle.asn1.ASN1OctetString.getInstance(subSkiRaw).getOctets()
        );
        assertThat(leafAki.getKeyIdentifier()).isEqualTo(subSki.getKeyIdentifier());

        // AuthorityKeyIdentifier on SubCA must point to SubjectKeyIdentifier on Root
        AuthorityKeyIdentifier subAki = AuthorityKeyIdentifier.getInstance(
                org.bouncycastle.asn1.ASN1OctetString.getInstance(subAkiRaw).getOctets()
        );
        SubjectKeyIdentifier rootSki = SubjectKeyIdentifier.getInstance(
                org.bouncycastle.asn1.ASN1OctetString.getInstance(rootSkiRaw).getOctets()
        );
        assertThat(subAki.getKeyIdentifier()).isEqualTo(rootSki.getKeyIdentifier());

        // 8. Verify Validity Window Nesting
        assertThat(leafCert.getNotBefore()).isAfterOrEqualTo(subCaCert.getNotBefore());
        assertThat(leafCert.getNotAfter()).isBeforeOrEqualTo(subCaCert.getNotAfter());
        assertThat(subCaCert.getNotAfter()).isBeforeOrEqualTo(rootCaCert.getNotAfter());

        // 9. Verify Signature Algorithm
        assertThat(leafCert.getSigAlgName()).isEqualToIgnoringCase("SHA256withRSA");
        assertThat(subCaCert.getSigAlgName()).isEqualToIgnoringCase("SHA256withRSA");
    }

    private EnrollmentRequest createApprovedRequest(String cn) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048);
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
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
