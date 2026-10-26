package com.pki.ca.service;

import com.pki.ca.dto.internal.ParsedCsrDto;
import com.pki.ca.exception.BadRequestException;
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

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsrParserServiceTest {

    private CsrParserService csrParserService;

    @BeforeEach
    void setUp() {
        csrParserService = new CsrParserService();
    }

    private String generateRsaCsrPem(int keySize, String dn, String[] dnsSans, String[] ipSans) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(keySize, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        X500Principal subject = new X500Principal(dn);
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());

        if ((dnsSans != null && dnsSans.length > 0) || (ipSans != null && ipSans.length > 0)) {
            ExtensionsGenerator extGen = new ExtensionsGenerator();
            java.util.List<GeneralName> names = new java.util.ArrayList<>();
            if (dnsSans != null) {
                for (String dns : dnsSans) {
                    names.add(new GeneralName(GeneralName.dNSName, dns));
                }
            }
            if (ipSans != null) {
                for (String ip : ipSans) {
                    names.add(new GeneralName(GeneralName.iPAddress, ip));
                }
            }
            extGen.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(new GeneralName[0])));
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

    private String generateEcCsrPem(String curveName, String dn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(curveName), new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        X500Principal subject = new X500Principal(dn);
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(csr);
        }
        return sw.toString();
    }

    @Test
    @DisplayName("Should parse valid RSA-2048 CSR and verify Proof-of-Possession signature")
    void testParseAndVerifyValidRsaCsr() throws Exception {
        String csrPem = generateRsaCsrPem(2048, "CN=api.internal.corp, O=Acme Corp, C=US",
                new String[]{"api.internal.corp", "api-backup.internal.corp"},
                new String[]{"10.0.12.5"});

        ParsedCsrDto parsed = csrParserService.parseAndVerifyCsr(csrPem);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getPublicKeyAlgorithm()).isEqualTo("RSA");
        assertThat(parsed.getKeyLength()).isEqualTo(2048);
        assertThat(parsed.getKeySpec()).isEqualTo("RSA-2048");
        assertThat(parsed.getSubjectDn()).contains("CN=api.internal.corp");
        assertThat(parsed.getSanEntries()).contains("DNS:api.internal.corp", "DNS:api-backup.internal.corp", "IP:10.0.12.5");
        assertThat(parsed.getPublicKeyFingerprint()).hasSize(64).matches("^[a-f0-9]+$");
    }

    @Test
    @DisplayName("Should parse valid ECDSA P-256 CSR")
    void testParseAndVerifyValidEcCsr() throws Exception {
        String csrPem = generateEcCsrPem("secp256r1", "CN=device.internal.corp, O=Acme Corp, C=US");

        ParsedCsrDto parsed = csrParserService.parseAndVerifyCsr(csrPem);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getPublicKeyAlgorithm()).isEqualTo("ECDSA");
        assertThat(parsed.getKeyLength()).isEqualTo(256);
        assertThat(parsed.getKeySpec()).isEqualTo("ECDSA-P256");
        assertThat(parsed.getSubjectDn()).contains("CN=device.internal.corp");
    }

    @Test
    @DisplayName("Should reject CSR with invalid Proof-of-Possession signature")
    void testRejectInvalidSignature() throws Exception {
        // Generate CSR signed by key pair A
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kpA = kpg.generateKeyPair();
        KeyPair kpB = kpg.generateKeyPair();

        X500Principal subject = new X500Principal("CN=spoofed.internal.corp");
        // Build CSR with Public Key A, but signed by Private Key B (signature mismatch!)
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, kpA.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kpB.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(csr);
        }
        String spoofedCsrPem = sw.toString();

        assertThatThrownBy(() -> csrParserService.parseAndVerifyCsr(spoofedCsrPem))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Proof-of-possession signature verification failed")
                .extracting("errorCode").isEqualTo("PKI-40012");
    }

    @Test
    @DisplayName("Should reject malformed or non-CSR PEM content")
    void testRejectMalformedPem() {
        String garbage = "-----BEGIN CERTIFICATE REQUEST-----\nNotValidBase64Garbage\n-----END CERTIFICATE REQUEST-----";

        assertThatThrownBy(() -> csrParserService.parseAndVerifyCsr(garbage))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("PKI-40011");
    }

    @Test
    @DisplayName("Should reject null or empty CSR")
    void testRejectEmptyCsr() {
        assertThatThrownBy(() -> csrParserService.parseAndVerifyCsr(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSR PEM content cannot be null or empty")
                .extracting("errorCode").isEqualTo("PKI-40011");

        assertThatThrownBy(() -> csrParserService.parseAndVerifyCsr(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSR PEM content cannot be null or empty");
    }

    @Test
    @DisplayName("Should reject oversized CSR payloads (> 16KB)")
    void testRejectOversizedCsr() {
        String oversized = "-----BEGIN CERTIFICATE REQUEST-----\n" + "A".repeat(17000) + "\n-----END CERTIFICATE REQUEST-----";

        assertThatThrownBy(() -> csrParserService.parseAndVerifyCsr(oversized))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds maximum allowed size of 16KB")
                .extracting("errorCode").isEqualTo("PKI-40011");
    }
}
