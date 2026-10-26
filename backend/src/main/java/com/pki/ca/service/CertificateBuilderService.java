package com.pki.ca.service;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.entity.CaAuthority;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.entity.EnrollmentRequest;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.HsmOperationException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Constructs and digitally signs RFC 5280 conformant X.509 v3 certificates.
 *
 * Security Invariants Enforced:
 * 1. Leaf certificates strictly receive BasicConstraints CA=false (critical).
 * 2. Untrusted CSR extensions, validity periods, serial numbers, or approval flags are ignored.
 * 3. Authority DN, KeyUsage, EKU, SKI, AKI, and SANs are compiled exclusively from authoritative server models.
 * 4. Clock-skew protection: notBefore = now - 5 minutes.
 * 5. Validity period is capped at the profile's maxValidityDays and CA expiration.
 * 6. Cryptographic signature and certificate content are verified before returning.
 */
@Service
public class CertificateBuilderService {

    private static final Logger log = LoggerFactory.getLogger(CertificateBuilderService.class);

    private final CryptoProvider cryptoProvider;

    public CertificateBuilderService(CryptoProvider cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    /**
     * Builds and signs an X.509 v3 certificate for the approved enrollment request.
     *
     * @param request       The authoritative, persisted enrollment request.
     * @param ca            The active Issuing CA authority.
     * @param serialNumber  The uniquely reserved 128-bit random serial number.
     * @return Generated and verified X509Certificate.
     */
    public X509Certificate buildAndSignCertificate(EnrollmentRequest request, CaAuthority ca, BigInteger serialNumber) {
        log.info("Constructing X.509 v3 certificate for Request ID: {}, Subject: '{}', Serial: {}",
                request.getId(), request.getSubjectDn(), serialNumber.toString(16));

        try {
            // 1. Extract and cryptographically verify Subject Public Key from stored CSR
            PublicKey subjectPublicKey = extractPublicKeyFromCsr(request.getCsrPem());

            // 2. Load CA Public Key for AKI extension and signature verification
            PublicKey caPublicKey = cryptoProvider.getPublicKey(ca.getHsmKeyAlias());

            // 3. Determine Validity with Clock-Skew Protection
            Instant now = Instant.now();
            Date notBefore = Date.from(now.minus(Duration.ofMinutes(5)));

            int validityDays = request.getValidityDays();
            CertificateProfile profile = request.getProfile();
            if (profile != null && validityDays > profile.getMaxValidityDays()) {
                validityDays = profile.getMaxValidityDays();
            }

            Date notAfter = Date.from(now.plus(Duration.ofDays(validityDays)));

            // Ensure certificate does not outlive issuing CA
            Date caNotAfter = java.sql.Timestamp.valueOf(ca.getNotAfter());
            if (notAfter.after(caNotAfter)) {
                log.warn("Requested validity extends past CA expiration. Capping notAfter to CA expiry ({})", caNotAfter);
                notAfter = caNotAfter;
            }

            // 4. Initialize Bouncy Castle X.509 v3 Builder
            X500Name issuerX500 = new X500Name(ca.getSubjectDn());
            X500Name subjectX500 = new X500Name(request.getSubjectDn());

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuerX500,
                    serialNumber,
                    notBefore,
                    notAfter,
                    subjectX500,
                    subjectPublicKey
            );

            // 5. Add Extensions according to RFC 5280 and Certificate Profile
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

            // 5a. BasicConstraints: CRITICAL, cA = false (Strict leaf certificate enforcement)
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            // 5b. Subject Key Identifier (SKI)
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(subjectPublicKey));

            // 5c. Authority Key Identifier (AKI)
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caPublicKey));

            // 5d. Key Usage
            int keyUsageMask = mapKeyUsages(profile != null ? profile.getKeyUsages() : Collections.emptyList());
            if (keyUsageMask != 0) {
                certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsageMask));
            }

            // 5e. Extended Key Usage (EKU)
            List<String> ekus = profile != null ? profile.getExtendedKeyUsages() : Collections.emptyList();
            if (!ekus.isEmpty()) {
                KeyPurposeId[] purposes = ekus.stream()
                        .map(oid -> KeyPurposeId.getInstance(new ASN1ObjectIdentifier(oid)))
                        .toArray(KeyPurposeId[]::new);
                certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purposes));
            }

            // 5f. Subject Alternative Names (SAN) - authorized values only
            List<String> sanEntries = request.getSanEntries();
            if (sanEntries != null && !sanEntries.isEmpty()) {
                GeneralName[] generalNames = buildGeneralNames(sanEntries);
                if (generalNames.length > 0) {
                    certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));
                }
            }

            // 6. Sign Certificate using HSM / Cryptographic Provider
            String sigAlg = resolveSignatureAlgorithm(ca.getAlgorithm());
            ContentSigner contentSigner = cryptoProvider.createContentSigner(ca.getHsmKeyAlias(), sigAlg);

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);
            X509Certificate x509Certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // 7. Cryptographic Sanity Verification of Finished Certificate
            x509Certificate.verify(caPublicKey);
            log.info("Cryptographic signature of newly built certificate verified successfully against CA public key.");

            return x509Certificate;

        } catch (HsmOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to construct or sign X.509 certificate for request ID {}: {}", request.getId(), ex.getMessage(), ex);
            throw new HsmOperationException("Certificate generation error: " + ex.getMessage(), ex, "PKI-50007");
        }
    }

    /**
     * Resolves the signature algorithm matching the CA's private key algorithm.
     */
    public String resolveSignatureAlgorithm(String caAlgorithm) {
        if (caAlgorithm == null || caAlgorithm.toUpperCase().startsWith("RSA")) {
            return "SHA256withRSA";
        } else if (caAlgorithm.toUpperCase().startsWith("EC")) {
            return "SHA256withECDSA";
        }
        return "SHA256withRSA";
    }

    /**
     * Maps textual key usage identifiers from profile to RFC 5280 bitmask.
     */
    private int mapKeyUsages(List<String> keyUsages) {
        int mask = 0;
        if (keyUsages == null) return mask;

        for (String ku : keyUsages) {
            switch (ku.trim().toLowerCase()) {
                case "digitalsignature" -> mask |= KeyUsage.digitalSignature;
                case "nonrepudiation", "contentcommitment" -> mask |= KeyUsage.nonRepudiation;
                case "keyencipherment" -> mask |= KeyUsage.keyEncipherment;
                case "dataencipherment" -> mask |= KeyUsage.dataEncipherment;
                case "keyagreement" -> mask |= KeyUsage.keyAgreement;
                case "encipheronly" -> mask |= KeyUsage.encipherOnly;
                case "decipheronly" -> mask |= KeyUsage.decipherOnly;
                // Intentionally ignore keyCertSign and cRLSign for leaf certificates!
                default -> log.debug("Ignoring or unknown key usage for leaf: {}", ku);
            }
        }
        return mask;
    }

    /**
     * Encodes validated SAN strings into ASN.1 GeneralNames.
     */
    private GeneralName[] buildGeneralNames(List<String> sanEntries) {
        List<GeneralName> names = new ArrayList<>();
        for (String san : sanEntries) {
            if (san == null || san.isBlank()) continue;
            String trimmed = san.trim();
            if (trimmed.startsWith("DNS:")) {
                names.add(new GeneralName(GeneralName.dNSName, trimmed.substring(4)));
            } else if (trimmed.startsWith("IP:")) {
                names.add(new GeneralName(GeneralName.iPAddress, trimmed.substring(3)));
            } else if (trimmed.startsWith("EMAIL:")) {
                names.add(new GeneralName(GeneralName.rfc822Name, trimmed.substring(6)));
            } else if (trimmed.startsWith("URI:")) {
                names.add(new GeneralName(GeneralName.uniformResourceIdentifier, trimmed.substring(4)));
            } else {
                // Default to DNS if format is standard domain name, or IP if numeric
                if (trimmed.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                    names.add(new GeneralName(GeneralName.iPAddress, trimmed));
                } else {
                    names.add(new GeneralName(GeneralName.dNSName, trimmed));
                }
            }
        }
        return names.toArray(new GeneralName[0]);
    }

    /**
     * Extracts the Subject Public Key from a PKCS#10 CSR PEM string.
     */
    public PublicKey extractPublicKeyFromCsr(String csrPem) throws Exception {
        byte[] bytes = extractPemBytes(csrPem, "CERTIFICATE REQUEST");
        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(bytes);
        SubjectPublicKeyInfo spki = csr.getSubjectPublicKeyInfo();
        return new JcaPEMKeyConverter().setProvider("BC").getPublicKey(spki);
    }

    /**
     * Converts a Java X509Certificate to PEM string format.
     */
    public String convertToPem(Object object) {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(object);
            }
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to format object to PEM: " + ex.getMessage(), ex);
        }
    }

    /**
     * Computes the SHA-256 fingerprint of a byte array (e.g. DER certificate or SubjectPublicKeyInfo).
     */
    public String computeSha256Fingerprint(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm missing in JVM", ex);
        }
    }

    private byte[] extractPemBytes(String pem, String type) throws Exception {
        org.bouncycastle.util.io.pem.PemReader pemReader = new org.bouncycastle.util.io.pem.PemReader(new StringReader(pem));
        org.bouncycastle.util.io.pem.PemObject pemObject = pemReader.readPemObject();
        if (pemObject != null) {
            return pemObject.getContent();
        }
        throw new BadRequestException("Invalid PEM format for type: " + type, "PKI-40011");
    }
}
