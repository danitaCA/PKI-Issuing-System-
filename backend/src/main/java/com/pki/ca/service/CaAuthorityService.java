package com.pki.ca.service;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.dto.request.CaImportCertRequest;
import com.pki.ca.dto.request.CaInitRequest;
import com.pki.ca.dto.response.CaInitResponse;
import com.pki.ca.dto.response.CaResponse;
import com.pki.ca.entity.CaAuthority;
import com.pki.ca.enums.CaStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.ConflictException;
import com.pki.ca.exception.HsmOperationException;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.CaAuthorityRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

@Service
public class CaAuthorityService {

    private static final Logger log = LoggerFactory.getLogger(CaAuthorityService.class);

    private final CaAuthorityRepository caAuthorityRepository;
    private final CryptoProvider cryptoProvider;
    private final CertificateBuilderService certificateBuilderService;

    public CaAuthorityService(CaAuthorityRepository caAuthorityRepository,
                              CryptoProvider cryptoProvider,
                              CertificateBuilderService certificateBuilderService) {
        this.caAuthorityRepository = caAuthorityRepository;
        this.cryptoProvider = cryptoProvider;
        this.certificateBuilderService = certificateBuilderService;
    }

    /**
     * Retrieves the primary active Issuing CA authority.
     */
    @Transactional(readOnly = true)
    public CaAuthority getActiveCa() {
        return caAuthorityRepository.findFirstByStatus(CaStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active CA authority found. CA must be initialized."));
    }

    /**
     * Retrieves active CA metadata response DTO.
     */
    @Transactional(readOnly = true)
    public CaResponse getCaMetadata() {
        CaAuthority ca = getActiveCa();
        return mapToResponse(ca);
    }

    /**
     * Returns the Issuing CA certificate PEM.
     */
    @Transactional(readOnly = true)
    public String getCaCertificatePem() {
        return getActiveCa().getCertificatePem();
    }

    /**
     * Returns the full CA trust chain PEM bundle.
     */
    @Transactional(readOnly = true)
    public String getCaChainPem() {
        return getActiveCa().getChainPem();
    }

    /**
     * Triggers on-token key generation inside the HSM and outputs a Subordinate CA PKCS#10 CSR.
     */
    @Transactional
    public CaInitResponse initCaKey(CaInitRequest request) {
        log.info("Administrative initialization requested for CA '{}', alias: '{}', algorithm: '{}'",
                request.getCaName(), request.getHsmKeyAlias(), request.getAlgorithm());

        if (caAuthorityRepository.findByCaName(request.getCaName()).isPresent()) {
            throw new ConflictException("CA with name '" + request.getCaName() + "' already exists.", "PKI-40901");
        }
        if (caAuthorityRepository.findByHsmKeyAlias(request.getHsmKeyAlias()).isPresent()) {
            throw new ConflictException("CA with HSM key alias '" + request.getHsmKeyAlias() + "' already exists.", "PKI-40902");
        }

        int keySize = extractKeySize(request.getAlgorithm());
        String keyAlgo = request.getAlgorithm().toUpperCase().startsWith("EC") ? "EC" : "RSA";

        // Generate private key inside token boundary
        KeyPair keyPair = cryptoProvider.generateKeyPairOnToken(request.getHsmKeyAlias(), keyAlgo, keySize);

        try {
            // Build Subordinate CA CSR with CA=true basic constraints
            X500Name subject = new X500Name(request.getSubjectDn());
            PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

            ExtensionsGenerator extGen = new ExtensionsGenerator();
            extGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(0)); // Subordinate CA pathLen=0
            extGen.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.cRLSign));
            csrBuilder.addAttribute(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

            ContentSigner signer = cryptoProvider.createContentSigner(
                    request.getHsmKeyAlias(),
                    keyAlgo.equals("EC") ? "SHA256withECDSA" : "SHA256withRSA"
            );
            PKCS10CertificationRequest csr = csrBuilder.build(signer);
            String csrPem = certificateBuilderService.convertToPem(csr);

            // Persist CA record in INITIALIZING state
            CaAuthority ca = new CaAuthority();
            ca.setCaName(request.getCaName());
            ca.setSubjectDn(request.getSubjectDn());
            ca.setIssuerDn("PENDING_ROOT_SIGNATURE");
            ca.setSerialNumber("PENDING_INITIALIZATION");
            ca.setHsmKeyAlias(request.getHsmKeyAlias());
            ca.setHsmSlotId(request.getHsmSlotId());
            ca.setAlgorithm(request.getAlgorithm());
            ca.setCertificatePem("PENDING");
            ca.setChainPem("PENDING");
            ca.setNotBefore(LocalDateTime.now());
            ca.setNotAfter(LocalDateTime.now().plusYears(10));
            ca.setStatus(CaStatus.INITIALIZING);
            ca.setCrlValidityHours(72);

            CaAuthority saved = caAuthorityRepository.save(ca);

            log.info("AUDIT [CA_KEY_INITIALIZED]: CA '{}' key initialized on HSM. CA ID: {}, Alias: '{}'",
                    saved.getCaName(), saved.getId(), saved.getHsmKeyAlias());

            return new CaInitResponse(saved.getId(), saved.getStatus().name(), csrPem);

        } catch (Exception ex) {
            log.error("Failed to generate CA CSR: {}", ex.getMessage(), ex);
            throw new HsmOperationException("Failed to construct Subordinate CA CSR: " + ex.getMessage(), ex, "PKI-50005");
        }
    }

    /**
     * Imports the Root-signed intermediate certificate and activates the CA.
     */
    @Transactional
    public CaResponse importSignedCert(CaImportCertRequest request) {
        CaAuthority ca = caAuthorityRepository.findById(request.getCaId())
                .orElseThrow(() -> new ResourceNotFoundException("CA authority not found with ID: " + request.getCaId()));

        log.info("Importing Root-signed certificate for CA '{}' (id: {})", ca.getCaName(), ca.getId());

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate subCert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(request.getCertificatePem().getBytes(StandardCharsets.UTF_8)));
            X509Certificate rootCert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(request.getRootCertificatePem().getBytes(StandardCharsets.UTF_8)));

            // 1. Verify Subordinate Certificate Signature against Root CA public key
            subCert.verify(rootCert.getPublicKey());

            // 2. Verify Key Correspondence between imported certificate and HSM key
            PublicKey hsmPublicKey = cryptoProvider.getPublicKey(ca.getHsmKeyAlias());
            if (!subCert.getPublicKey().equals(hsmPublicKey)) {
                log.error("AUDIT [SECURITY_VIOLATION]: Public key in imported certificate does not match HSM key for alias '{}'",
                        ca.getHsmKeyAlias());
                throw new BadRequestException("Public key in imported certificate does not match the key generated in the HSM.", "PKI-40033");
            }

            // 3. Verify BasicConstraints CA=true
            if (subCert.getBasicConstraints() < 0) {
                throw new BadRequestException("Imported certificate is not a CA certificate (basicConstraints CA=true required).", "PKI-40034");
            }

            // 4. Update CA Authority Record
            ca.setSubjectDn(subCert.getSubjectX500Principal().getName());
            ca.setIssuerDn(subCert.getIssuerX500Principal().getName());
            ca.setSerialNumber(subCert.getSerialNumber().toString(16).toLowerCase());
            ca.setCertificatePem(request.getCertificatePem().trim());
            ca.setChainPem(request.getCertificatePem().trim() + "\n" + request.getRootCertificatePem().trim());
            ca.setNotBefore(LocalDateTime.ofInstant(subCert.getNotBefore().toInstant(), ZoneOffset.UTC));
            ca.setNotAfter(LocalDateTime.ofInstant(subCert.getNotAfter().toInstant(), ZoneOffset.UTC));
            ca.setStatus(CaStatus.ACTIVE);

            CaAuthority updated = caAuthorityRepository.save(ca);

            log.info("AUDIT [CA_CERT_IMPORTED]: CA '{}' activated with Root-signed certificate. Serial: {}",
                    updated.getCaName(), updated.getSerialNumber());

            return mapToResponse(updated);

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to import CA certificate: {}", ex.getMessage(), ex);
            throw new BadRequestException("Invalid or untrusted CA certificate bundle: " + ex.getMessage(), "PKI-40035");
        }
    }

    /**
     * Initializes the default isolated test CA hierarchy if not already provisioned.
     * Enforces an offline test Root CA signing the subordinate Issuing CA with HSM keys.
     */
    @Transactional
    public void ensureDefaultTestCaHierarchy() {
        Optional<CaAuthority> existing = caAuthorityRepository.findFirstByStatus(CaStatus.ACTIVE);
        String defaultAlias = "issuing-ca-key-01";

        if (existing.isPresent()) {
            CaAuthority ca = existing.get();
            // Verify token key availability
            if (cryptoProvider.keyExists(ca.getHsmKeyAlias())) {
                return;
            }
            log.info("HSM key for existing CA '{}' is missing in token cache. Re-bootstrapping hierarchy...", ca.getCaName());
            caAuthorityRepository.delete(ca);
        }

        log.info("Bootstrapping default isolated test CA hierarchy (Acme Offline Root CA -> Enterprise Issuing CA 01)...");

        try {
            // 1. Generate Offline Test Root CA
            KeyPairGenerator rootKpg = KeyPairGenerator.getInstance("RSA", "BC");
            rootKpg.initialize(2048);
            KeyPair rootKeyPair = rootKpg.generateKeyPair();

            Instant now = Instant.now();
            Date rootNotBefore = Date.from(now.minus(Duration.ofMinutes(10)));
            Date rootNotAfter = Date.from(now.plus(Duration.ofDays(3650 * 2))); // 20 years

            X500Name rootDn = new X500Name("CN=Acme Offline Root CA, O=Acme Corp, C=US");
            BigInteger rootSerial = BigInteger.valueOf(1);

            X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(
                    rootDn, rootSerial, rootNotBefore, rootNotAfter, rootDn, rootKeyPair.getPublic()
            );

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(1)); // Root CA pathLen=1
            rootBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            rootBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(rootKeyPair.getPublic()));
            rootBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(rootKeyPair.getPublic()));

            ContentSigner rootSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootKeyPair.getPrivate());
            X509CertificateHolder rootHolder = rootBuilder.build(rootSigner);
            X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(rootHolder);
            String rootCertPem = certificateBuilderService.convertToPem(rootCert);

            // 2. Generate Subordinate Issuing CA Key directly in HSM token
            KeyPair subKeyPair = cryptoProvider.generateKeyPairOnToken(defaultAlias, "RSA", 2048);

            // 3. Root CA signs Subordinate Issuing CA Certificate
            X500Name subDn = new X500Name("CN=Enterprise Issuing CA 01, O=Acme Corp, C=US");
            BigInteger subSerial = BigInteger.valueOf(2);
            Date subNotBefore = Date.from(now.minus(Duration.ofMinutes(10)));
            Date subNotAfter = Date.from(now.plus(Duration.ofDays(3650))); // 10 years

            X509v3CertificateBuilder subBuilder = new JcaX509v3CertificateBuilder(
                    rootDn, subSerial, subNotBefore, subNotAfter, subDn, subKeyPair.getPublic()
            );

            subBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0)); // Subordinate pathLen=0
            subBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.cRLSign));
            subBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(subKeyPair.getPublic()));
            subBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(rootKeyPair.getPublic()));

            X509CertificateHolder subHolder = subBuilder.build(rootSigner);
            X509Certificate subCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(subHolder);

            // Verify Subordinate signature and key correspondence
            subCert.verify(rootCert.getPublicKey());
            String subCertPem = certificateBuilderService.convertToPem(subCert);

            // 4. Persist in ca_authorities
            CaAuthority ca = new CaAuthority();
            ca.setCaName("Enterprise Issuing CA 01");
            ca.setSubjectDn("CN=Enterprise Issuing CA 01, O=Acme Corp, C=US");
            ca.setIssuerDn("CN=Acme Offline Root CA, O=Acme Corp, C=US");
            ca.setSerialNumber(subSerial.toString(16).toLowerCase());
            ca.setHsmKeyAlias(defaultAlias);
            ca.setHsmSlotId("1");
            ca.setAlgorithm("RSA-2048");
            ca.setCertificatePem(subCertPem);
            ca.setChainPem(subCertPem + "\n" + rootCertPem);
            ca.setNotBefore(LocalDateTime.ofInstant(subNotBefore.toInstant(), ZoneOffset.UTC));
            ca.setNotAfter(LocalDateTime.ofInstant(subNotAfter.toInstant(), ZoneOffset.UTC));
            ca.setStatus(CaStatus.ACTIVE);
            ca.setCrlValidityHours(72);

            caAuthorityRepository.save(ca);

            log.info("AUDIT [CA_BOOTSTRAPPED]: Test hierarchy established. Active CA 'Enterprise Issuing CA 01' backed by token key '{}'",
                    defaultAlias);

        } catch (Exception ex) {
            log.error("Failed to bootstrap test CA hierarchy: {}", ex.getMessage(), ex);
            throw new HsmOperationException("Failed to bootstrap default test CA hierarchy: " + ex.getMessage(), ex, "PKI-50001");
        }
    }

    private CaResponse mapToResponse(CaAuthority ca) {
        CaResponse response = new CaResponse();
        response.setId(ca.getId());
        response.setCaName(ca.getCaName());
        response.setStatus(ca.getStatus().name());
        response.setSubjectDn(ca.getSubjectDn());
        response.setIssuerDn(ca.getIssuerDn());
        response.setSerialNumber(ca.getSerialNumber());
        response.setAlgorithm(ca.getAlgorithm());
        response.setHsmSlotId(ca.getHsmSlotId());
        response.setHsmKeyAlias(ca.getHsmKeyAlias());
        response.setNotBefore(ca.getNotBefore());
        response.setNotAfter(ca.getNotAfter());
        response.setCrlValidityHours(ca.getCrlValidityHours());
        return response;
    }

    private int extractKeySize(String algorithm) {
        if (algorithm == null) return 2048;
        if (algorithm.contains("4096")) return 4096;
        if (algorithm.contains("3072")) return 3072;
        if (algorithm.contains("2048")) return 2048;
        if (algorithm.contains("256")) return 256;
        if (algorithm.contains("384")) return 384;
        return 2048;
    }
}
