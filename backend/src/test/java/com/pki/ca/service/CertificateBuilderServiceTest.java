package com.pki.ca.service;

import com.pki.ca.crypto.SoftwareCryptoProvider;
import com.pki.ca.entity.CaAuthority;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.entity.EnrollmentRequest;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateBuilderServiceTest {

    private SoftwareCryptoProvider cryptoProvider;
    private CertificateBuilderService certificateBuilderService;
    private KeyPair caKeyPair;
    private CaAuthority caAuthority;

    @BeforeAll
    static void initBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        cryptoProvider = new SoftwareCryptoProvider();
        certificateBuilderService = new CertificateBuilderService(cryptoProvider);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        caKeyPair = kpg.generateKeyPair();
        cryptoProvider.registerKey("test-issuing-ca-alias", caKeyPair);

        caAuthority = new CaAuthority();
        caAuthority.setId(1L);
        caAuthority.setCaName("Enterprise Issuing CA 01");
        caAuthority.setSubjectDn("CN=Enterprise Issuing CA 01, O=Acme Corp, C=US");
        caAuthority.setIssuerDn("CN=Acme Offline Root CA, O=Acme Corp, C=US");
        caAuthority.setSerialNumber("01");
        caAuthority.setHsmKeyAlias("test-issuing-ca-alias");
        caAuthority.setHsmSlotId("1");
        caAuthority.setAlgorithm("RSA-2048");
        caAuthority.setNotBefore(LocalDateTime.now().minusYears(1));
        caAuthority.setNotAfter(LocalDateTime.now().plusYears(10));
    }

    @Test
    @DisplayName("Should build leaf certificate with BasicConstraints CA=false, clock-skew, SANs, and valid signature")
    void shouldBuildValidLeafCertificate() throws Exception {
        // Generate applicant key and CSR
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair applicantKeyPair = kpg.generateKeyPair();

        X500Name subjectDn = new X500Name("CN=api.internal.corp, O=Acme Corp, C=US");
        JcaPKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(subjectDn, applicantKeyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(applicantKeyPair.getPrivate());
        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(csr);
        }
        String csrPem = sw.toString();

        CertificateProfile profile = new CertificateProfile();
        profile.setProfileCode("TLS_SERVER");
        profile.setMaxValidityDays(730);
        profile.setDefaultValidityDays(365);
        profile.setKeyUsages(List.of("digitalSignature", "keyEncipherment"));
        profile.setExtendedKeyUsages(List.of("1.3.6.1.5.5.7.3.1")); // id-kp-serverAuth

        EnrollmentRequest request = new EnrollmentRequest();
        request.setId(101L);
        request.setSubjectDn("CN=api.internal.corp, O=Acme Corp, C=US");
        request.setSanEntries(List.of("DNS:api.internal.corp", "IP:10.0.0.1"));
        request.setCsrPem(csrPem);
        request.setValidityDays(365);
        request.setProfile(profile);

        BigInteger serial = new BigInteger("7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c", 16);

        // Execute issuance construction
        X509Certificate certificate = certificateBuilderService.buildAndSignCertificate(request, caAuthority, serial);

        // Assertions:
        assertThat(certificate).isNotNull();
        assertThat(certificate.getSerialNumber()).isEqualTo(serial);
        assertThat(certificate.getSubjectX500Principal().getName()).contains("CN=api.internal.corp");
        assertThat(certificate.getIssuerX500Principal().getName()).contains("CN=Enterprise Issuing CA 01");

        // Basic Constraints MUST be CA=false (getBasicConstraints returns -1 for non-CA)
        assertThat(certificate.getBasicConstraints()).isEqualTo(-1);

        // Clock Skew: notBefore must be in the past (approx 5 minutes earlier)
        Date notBefore = certificate.getNotBefore();
        assertThat(notBefore).isBefore(new Date());

        // Key Usage: digitalSignature (bit 0) and keyEncipherment (bit 2)
        boolean[] keyUsage = certificate.getKeyUsage();
        assertThat(keyUsage).isNotNull();
        assertThat(keyUsage[0]).isTrue(); // digitalSignature
        assertThat(keyUsage[2]).isTrue(); // keyEncipherment
        assertThat(keyUsage[5]).isFalse(); // keyCertSign MUST BE FALSE!
        assertThat(keyUsage[6]).isFalse(); // cRLSign MUST BE FALSE!

        // Extended Key Usage: serverAuth
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        assertThat(extendedKeyUsage).contains("1.3.6.1.5.5.7.3.1");

        // Subject Alternative Names: DNS & IP
        var sanList = certificate.getSubjectAlternativeNames();
        assertThat(sanList).isNotNull();
        assertThat(sanList).hasSize(2);

        // Verify cryptographic signature against CA public key
        certificate.verify(caKeyPair.getPublic());
    }
}
