package com.pki.ca.security;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.dto.request.CaImportCertRequest;
import com.pki.ca.dto.request.CaInitRequest;
import com.pki.ca.dto.response.CaInitResponse;
import com.pki.ca.dto.response.CaResponse;
import com.pki.ca.entity.CaAuthority;
import com.pki.ca.enums.CaStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.repository.CaAuthorityRepository;
import com.pki.ca.service.CaAuthorityService;
import com.pki.ca.service.CertificateBuilderService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security Check 1: Root CA Isolation and Out-of-Band Signing Ceremony Verification.
 *
 * Confirms that:
 * 1. The Root CA private key is NEVER possessed by, accessible to, or stored in the online application.
 * 2. The online system functions via a genuine 2-step offline root signing ceremony:
 *    a) Online CA generates on-token key pair and exports PKCS#10 CSR (initCaKey).
 *    b) Air-gapped / offline process signs the intermediate certificate using the offline Root key.
 *    c) Online CA imports and cryptographically verifies the intermediate certificate and chain (importSignedCert).
 * 3. Any attempt to import an intermediate certificate with spoofed root signature or key mismatch is rejected.
 */
@ExtendWith(MockitoExtension.class)
class RootCaIsolationSecurityTest {

    @Mock
    private CaAuthorityRepository caAuthorityRepository;

    private CryptoProvider cryptoProvider;
    private CertificateBuilderService certificateBuilderService;
    private CaAuthorityService caAuthorityService;

    @BeforeAll
    static void initBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() {
        cryptoProvider = new SoftwareCryptoProvider();
        certificateBuilderService = new CertificateBuilderService(cryptoProvider);
        caAuthorityService = new CaAuthorityService(caAuthorityRepository, cryptoProvider, certificateBuilderService);
    }

    @Test
    @DisplayName("Genuinely isolated Root signing ceremony: Online system generates CSR, offline root signs, online imports")
    void shouldExecuteIsolatedRootSigningCeremonyWithoutRootKeyInApplication() throws Exception {
        // Step 1: Online CA initializes its key in token boundary and emits Subordinate CSR
        CaInitRequest initReq = new CaInitRequest();
        initReq.setCaName("Enterprise Issuing CA 01");
        initReq.setSubjectDn("CN=Enterprise Issuing CA 01, O=Acme Corp, C=US");
        initReq.setHsmKeyAlias("ceremony-sub-key-alias");
        initReq.setHsmSlotId("1");
        initReq.setAlgorithm("RSA-2048");

        when(caAuthorityRepository.findByCaName(any())).thenReturn(Optional.empty());
        when(caAuthorityRepository.findByHsmKeyAlias(any())).thenReturn(Optional.empty());

        java.util.concurrent.atomic.AtomicReference<CaAuthority> savedHolder = new java.util.concurrent.atomic.AtomicReference<>();
        when(caAuthorityRepository.save(any(CaAuthority.class))).thenAnswer(inv -> {
            CaAuthority a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(10L);
            }
            savedHolder.set(a);
            return a;
        });

        CaInitResponse initResp = caAuthorityService.initCaKey(initReq);

        assertThat(initResp).isNotNull();
        assertThat(initResp.getCaId()).isEqualTo(10L);
        assertThat(initResp.getStatus()).isEqualTo("INITIALIZING");
        assertThat(initResp.getCaCsrPem()).contains("BEGIN CERTIFICATE REQUEST");

        // Verify state in repository: Issuer and certs are PENDING
        CaAuthority initializedCa = savedHolder.get();
        assertThat(initializedCa.getStatus()).isEqualTo(CaStatus.INITIALIZING);
        assertThat(initializedCa.getIssuerDn()).isEqualTo("PENDING_ROOT_SIGNATURE");
        assertThat(initializedCa.getCertificatePem()).isEqualTo("PENDING");

        // Verify online application has NO root private key in memory or token
        assertThat(cryptoProvider.keyExists("root-ca-key")).isFalse();

        // ----------------------------------------------------------------------------------------
        // Step 2: OFFLINE AIR-GAPPED CEREMONY (Executed on completely isolated workstation)
        // ----------------------------------------------------------------------------------------
        KeyPairGenerator rootKpg = KeyPairGenerator.getInstance("RSA", "BC");
        rootKpg.initialize(4096);
        KeyPair offlineRootKeyPair = rootKpg.generateKeyPair(); // NEVER given to online application

        Instant now = Instant.now();
        Date rootNotBefore = Date.from(now.minus(Duration.ofMinutes(10)));
        Date rootNotAfter = Date.from(now.plus(Duration.ofDays(7300))); // 20 years

        X500Name rootDn = new X500Name("CN=Acme Offline Root CA, O=Acme Corp, C=US");
        BigInteger rootSerial = BigInteger.valueOf(1);

        X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(
                rootDn, rootSerial, rootNotBefore, rootNotAfter, rootDn, offlineRootKeyPair.getPublic()
        );

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(1)); // Root CA pathLen=1
        rootBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        rootBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(offlineRootKeyPair.getPublic()));

        ContentSigner rootSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(offlineRootKeyPair.getPrivate());
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(rootBuilder.build(rootSigner));
        String rootCertPem = certificateBuilderService.convertToPem(rootCert);

        // Offline Root signs the Subordinate CSR from Step 1
        PKCS10CertificationRequest subCsr;
        try (org.bouncycastle.openssl.PEMParser pemParser = new org.bouncycastle.openssl.PEMParser(new java.io.StringReader(initResp.getCaCsrPem()))) {
            subCsr = (PKCS10CertificationRequest) pemParser.readObject();
        }
        Date subNotBefore = Date.from(now.minus(Duration.ofMinutes(5)));
        Date subNotAfter = Date.from(now.plus(Duration.ofDays(3650))); // 10 years
        BigInteger subSerial = BigInteger.valueOf(2);

        X509v3CertificateBuilder subCertBuilder = new X509v3CertificateBuilder(
                rootDn, subSerial, subNotBefore, subNotAfter, subCsr.getSubject(), subCsr.getSubjectPublicKeyInfo()
        );
        subCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0)); // SubCA pathLen=0
        subCertBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.cRLSign));
        subCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(subCsr.getSubjectPublicKeyInfo()));
        subCertBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(offlineRootKeyPair.getPublic()));

        X509Certificate subCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(subCertBuilder.build(rootSigner));
        String subCertPem = certificateBuilderService.convertToPem(subCert);

        // The offline root private key is destroyed or kept in air-gapped safe
        offlineRootKeyPair = null;

        // ----------------------------------------------------------------------------------------
        // Step 3: ONLINE CA IMPORTS SIGNED CERTIFICATE AND CHAIN
        // ----------------------------------------------------------------------------------------
        CaImportCertRequest importReq = new CaImportCertRequest();
        importReq.setCaId(10L);
        importReq.setCertificatePem(subCertPem);
        importReq.setRootCertificatePem(rootCertPem);

        when(caAuthorityRepository.findById(10L)).thenReturn(Optional.of(initializedCa));

        CaResponse caResp = caAuthorityService.importSignedCert(importReq);

        assertThat(caResp).isNotNull();
        assertThat(caResp.getStatus()).isEqualTo("ACTIVE");
        assertThat(caResp.getSubjectDn()).contains("CN=Enterprise Issuing CA 01");
        assertThat(caResp.getIssuerDn()).contains("CN=Acme Offline Root CA");
        assertThat(initializedCa.getChainPem()).contains(subCertPem.trim());
        assertThat(initializedCa.getChainPem()).contains(rootCertPem.trim());
    }

    @Test
    @DisplayName("Should reject import when Subordinate CA certificate does not match the token's private key")
    void shouldRejectImportWhenPublicKeyDoesNotMatchHsmKey() throws Exception {
        CaAuthority initializedCa = new CaAuthority();
        initializedCa.setId(10L);
        initializedCa.setCaName("Enterprise Issuing CA 01");
        initializedCa.setHsmKeyAlias("original-hsm-key");
        initializedCa.setStatus(CaStatus.INITIALIZING);

        // Generate key on token
        cryptoProvider.generateKeyPairOnToken("original-hsm-key", "RSA", 2048);

        // Attacker creates a certificate with a DIFFERENT key
        KeyPairGenerator diffKpg = KeyPairGenerator.getInstance("RSA", "BC");
        diffKpg.initialize(2048);
        KeyPair diffKeyPair = diffKpg.generateKeyPair();

        KeyPair rootKp = diffKpg.generateKeyPair();
        X500Name rootDn = new X500Name("CN=Attacker Root CA, O=Fake, C=US");
        X509v3CertificateBuilder rootBld = new JcaX509v3CertificateBuilder(
                rootDn, BigInteger.ONE, new Date(), new Date(System.currentTimeMillis() + 86400000L),
                rootDn, rootKp.getPublic()
        );
        rootBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        ContentSigner rootSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootKp.getPrivate());
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(rootBld.build(rootSigner));

        X509v3CertificateBuilder subBld = new JcaX509v3CertificateBuilder(
                rootDn, BigInteger.valueOf(2), new Date(), new Date(System.currentTimeMillis() + 86400000L),
                new X500Name("CN=Spoofed CA, O=Fake, C=US"), diffKeyPair.getPublic()
        );
        subBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        X509Certificate spoofedSubCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(subBld.build(rootSigner));

        CaImportCertRequest importReq = new CaImportCertRequest();
        importReq.setCaId(10L);
        importReq.setCertificatePem(certificateBuilderService.convertToPem(spoofedSubCert));
        importReq.setRootCertificatePem(certificateBuilderService.convertToPem(rootCert));

        when(caAuthorityRepository.findById(10L)).thenReturn(Optional.of(initializedCa));

        assertThatThrownBy(() -> caAuthorityService.importSignedCert(importReq))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> {
                    BadRequestException bex = (BadRequestException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("PKI-40033");
                    assertThat(bex.getMessage()).contains("does not match the key generated in the HSM");
                });
    }

    @Test
    @DisplayName("Should reject import when imported certificate lacks basicConstraints CA=true")
    void shouldRejectImportWhenCertificateIsNotCa() throws Exception {
        CaAuthority initializedCa = new CaAuthority();
        initializedCa.setId(10L);
        initializedCa.setCaName("Enterprise Issuing CA 01");
        initializedCa.setHsmKeyAlias("leaf-mismatch-key");
        initializedCa.setStatus(CaStatus.INITIALIZING);

        KeyPair kp = cryptoProvider.generateKeyPairOnToken("leaf-mismatch-key", "RSA", 2048);

        KeyPair rootKp = KeyPairGenerator.getInstance("RSA", "BC").generateKeyPair();
        X500Name rootDn = new X500Name("CN=Acme Offline Root CA, O=Acme, C=US");
        X509v3CertificateBuilder rootBld = new JcaX509v3CertificateBuilder(
                rootDn, BigInteger.ONE, new Date(), new Date(System.currentTimeMillis() + 86400000L),
                rootDn, rootKp.getPublic()
        );
        rootBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        ContentSigner rootSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootKp.getPrivate());
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(rootBld.build(rootSigner));

        // Subordinate certificate built with CA=false (leaf)
        X509v3CertificateBuilder subBld = new JcaX509v3CertificateBuilder(
                rootDn, BigInteger.valueOf(2), new Date(), new Date(System.currentTimeMillis() + 86400000L),
                new X500Name("CN=Enterprise Issuing CA 01, O=Acme, C=US"), kp.getPublic()
        );
        subBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(false)); // Invalid CA!
        X509Certificate nonCaCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(subBld.build(rootSigner));

        CaImportCertRequest importReq = new CaImportCertRequest();
        importReq.setCaId(10L);
        importReq.setCertificatePem(certificateBuilderService.convertToPem(nonCaCert));
        importReq.setRootCertificatePem(certificateBuilderService.convertToPem(rootCert));

        when(caAuthorityRepository.findById(10L)).thenReturn(Optional.of(initializedCa));

        assertThatThrownBy(() -> caAuthorityService.importSignedCert(importReq))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> {
                    BadRequestException bex = (BadRequestException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("PKI-40034");
                    assertThat(bex.getMessage()).contains("basicConstraints CA=true required");
                });
    }
}
