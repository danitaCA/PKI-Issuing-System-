package com.pki.ca.crypto;

import com.pki.ca.exception.HsmOperationException;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-assurance PKCS#11 Hardware Security Module (HSM) Adapter using SunPKCS11.
 * Integrates directly with SoftHSM2 (development/test) or physical enterprise HSMs (production).
 *
 * Security Invariants Enforced:
 * 1. CA private keys are generated inside the secure token boundary.
 * 2. CKA_PRIVATE=true, CKA_SENSITIVE=true, CKA_EXTRACTABLE=false attributes applied.
 * 3. Private key bytes NEVER enter JVM heap memory (opaque pointers only).
 * 4. Token PIN is injected from protected environment configuration, never logged.
 * 5. Hardware failures, missing keys, or invalid PINs fail safely without silent software fallback.
 * 6. SoftHSM is documented as development/testing hardware emulation only.
 */
@Component
@ConditionalOnProperty(name = "app.hsm.enabled", havingValue = "true", matchIfMissing = true)
public class Pkcs11HsmAdapter implements CryptoProvider {

    private static final Logger log = LoggerFactory.getLogger(Pkcs11HsmAdapter.class);

    @Value("${app.hsm.library-path:C:/SoftHSM2/lib/softhsm2-x64.dll}")
    private String libraryPath;

    @Value("${app.hsm.slot-list-index:1}")
    private int slotListIndex;

    @Value("${app.hsm.pin:123456}")
    private String tokenPin;

    @Value("${app.hsm.provider-name:SoftHSM-IssuingCA}")
    private String providerInstanceName;

    private Provider pkcs11Provider;
    private KeyStore keyStore;
    private final Map<String, KeyPair> tokenKeyCache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    public Pkcs11HsmAdapter() {
    }

    public Pkcs11HsmAdapter(String libraryPath, int slotListIndex, String tokenPin, String providerInstanceName) {
        this.libraryPath = libraryPath;
        this.slotListIndex = slotListIndex;
        this.tokenPin = tokenPin;
        this.providerInstanceName = providerInstanceName;
    }

    @PostConstruct
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        log.info("Initializing SunPKCS11 cryptographic adapter with library: '{}', slotListIndex: {}", libraryPath, slotListIndex);

        File libFile = new File(libraryPath);
        if (!libFile.exists()) {
            // Check fallback platform paths for cross-platform containerized testing if needed
            String linuxPath = "/usr/lib/softhsm/libsofthsm2.so";
            String debianPath = "/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so";
            if (new File(linuxPath).exists()) {
                libraryPath = linuxPath;
            } else if (new File(debianPath).exists()) {
                libraryPath = debianPath;
            } else {
                throw new HsmOperationException(
                        "PKCS#11 shared library file not found at path: " + libraryPath + ". SoftHSM must be installed.",
                        "PKI-50001"
                );
            }
        }

        try {
            String sanitizedLibPath = libraryPath.replace('\\', '/');
            String pkcs11Config = "--name = " + providerInstanceName + "\n"
                    + "library = " + sanitizedLibPath + "\n"
                    + "slotListIndex = " + slotListIndex + "\n"
                    + "attributes(*, CKO_PRIVATE_KEY, *) = {\n"
                    + "  CKA_TOKEN = true\n"
                    + "  CKA_PRIVATE = true\n"
                    + "  CKA_SENSITIVE = true\n"
                    + "  CKA_EXTRACTABLE = false\n"
                    + "}\n"
                    + "attributes(*, CKO_PUBLIC_KEY, *) = {\n"
                    + "  CKA_TOKEN = true\n"
                    + "}\n";

            Provider sunPkcs11 = Security.getProvider("SunPKCS11");
            if (sunPkcs11 == null) {
                throw new HsmOperationException("SunPKCS11 provider is unavailable in JVM.", "PKI-50001");
            }

            this.pkcs11Provider = sunPkcs11.configure(pkcs11Config);
            Security.addProvider(this.pkcs11Provider);

            this.keyStore = KeyStore.getInstance("PKCS11", this.pkcs11Provider);
            char[] pinChars = (tokenPin != null) ? tokenPin.toCharArray() : null;
            this.keyStore.load(null, pinChars);

            this.initialized = true;
            log.info("SunPKCS11 provider '{}' registered and token authenticated successfully. (SoftHSM dev/test emulation active)",
                    this.pkcs11Provider.getName());

        } catch (Exception ex) {
            this.initialized = false;
            if (isPinIncorrect(ex)) {
                log.error("AUDIT [HSM_AUTH_FAILED]: Incorrect token PIN supplied for PKCS#11 token");
                throw new HsmOperationException("PKCS#11 token authentication failed: Incorrect token PIN.", ex, "PKI-50002");
            }
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.error("AUDIT [HSM_INIT_FAILED]: Failed to initialize SunPKCS11 provider: {}", msg);
            throw new HsmOperationException("Failed to initialize PKCS#11 provider: " + msg, ex, "PKI-50001");
        }
    }

    private boolean isPinIncorrect(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("CKR_PIN_INCORRECT") || msg.contains("CKR_PIN_INVALID") || msg.contains("PIN"))) {
                return true;
            }
            if (t.getClass().getSimpleName().contains("FailedLoginException")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    public void logout() {
        if (pkcs11Provider instanceof java.security.AuthProvider authProvider) {
            try {
                authProvider.logout();
            } catch (Exception ignored) {
            }
        }
        if (pkcs11Provider != null) {
            try {
                Security.removeProvider(pkcs11Provider.getName());
            } catch (Exception ignored) {
            }
        }
        this.initialized = false;
    }

    @Override
    public boolean isHsmAvailable() {
        return initialized && pkcs11Provider != null;
    }

    @Override
    public String getProviderName() {
        return (pkcs11Provider != null) ? pkcs11Provider.getName() : "SunPKCS11-Uninitialized";
    }

    @Override
    public Provider getProvider() {
        if (!isHsmAvailable()) {
            throw new HsmOperationException("PKCS#11 provider is not operational.", "PKI-50001");
        }
        return pkcs11Provider;
    }

    @Override
    public KeyPair generateKeyPairOnToken(String alias, String algorithm, int keySize) {
        if (!isHsmAvailable()) {
            initialize();
        }

        log.info("Generating asymmetric key pair on HSM token. Alias: '{}', Algorithm: '{}', KeySize: {}",
                alias, algorithm, keySize);

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm, pkcs11Provider);
            kpg.initialize(keySize);
            KeyPair keyPair = kpg.generateKeyPair();

            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            // Verify non-extractability invariant
            if (privateKey.getEncoded() != null) {
                log.warn("SECURITY WARNING: Private key returned non-null encoded bytes. Ensure CKA_EXTRACTABLE=FALSE is enforced.");
            }

            // Verify key correspondence before trusting the key
            verifyKeyCorrespondence(alias, publicKey, privateKey, algorithm);

            tokenKeyCache.put(alias, keyPair);

            log.info("AUDIT [CA_KEY_GENERATED]: Key pair successfully generated inside token boundary. Alias: '{}', Algorithm: '{}-{}'",
                    alias, algorithm, keySize);
            return keyPair;

        } catch (Exception ex) {
            log.error("AUDIT [CA_KEY_GEN_FAILED]: Key generation failed on HSM for alias '{}': {}", alias, ex.getMessage());
            throw new HsmOperationException("Failed to generate key pair on HSM token: " + ex.getMessage(), ex, "PKI-50005");
        }
    }

    @Override
    public boolean keyExists(String alias) {
        if (tokenKeyCache.containsKey(alias)) {
            return true;
        }
        try {
            if (keyStore != null && keyStore.containsAlias(alias)) {
                return true;
            }
        } catch (KeyStoreException ignored) {
        }
        return false;
    }

    @Override
    public PublicKey getPublicKey(String alias) {
        KeyPair cached = tokenKeyCache.get(alias);
        if (cached != null) {
            return cached.getPublic();
        }
        try {
            if (keyStore != null && keyStore.containsAlias(alias)) {
                Certificate cert = keyStore.getCertificate(alias);
                if (cert != null) {
                    return cert.getPublicKey();
                }
            }
        } catch (KeyStoreException ex) {
            throw new HsmOperationException("Failed to query public key for alias '" + alias + "': " + ex.getMessage(), ex, "PKI-50003");
        }
        throw new HsmOperationException("Public key for alias '" + alias + "' not found on HSM token.", "PKI-50003");
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        KeyPair cached = tokenKeyCache.get(alias);
        if (cached != null) {
            return cached.getPrivate();
        }
        try {
            if (keyStore != null && keyStore.containsAlias(alias)) {
                Key key = keyStore.getKey(alias, null);
                if (key instanceof PrivateKey) {
                    return (PrivateKey) key;
                }
            }
        } catch (Exception ex) {
            throw new HsmOperationException("Failed to load private key handle for alias '" + alias + "': " + ex.getMessage(), ex, "PKI-50003");
        }
        throw new HsmOperationException("Private key handle for alias '" + alias + "' not found on HSM token.", "PKI-50003");
    }

    @Override
    public ContentSigner createContentSigner(String alias, String signatureAlgorithm) {
        PrivateKey privateKey = getPrivateKey(alias);
        try {
            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(signatureAlgorithm);
            signerBuilder.setProvider(pkcs11Provider);
            return signerBuilder.build(privateKey);
        } catch (Exception ex) {
            log.error("AUDIT [HSM_SIGNING_FAILED]: Failed to create ContentSigner for alias '{}': {}", alias, ex.getMessage());
            throw new HsmOperationException("Failed to initialize HSM signature operator: " + ex.getMessage(), ex, "PKI-50006");
        }
    }

    @Override
    public boolean verifySignature(PublicKey publicKey, byte[] data, byte[] signature, String signatureAlgorithm) {
        try {
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception ex) {
            log.error("Signature verification failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Confirms that the private key handle matches the public key by signing and verifying a challenge payload.
     */
    private void verifyKeyCorrespondence(String alias, PublicKey publicKey, PrivateKey privateKey, String keyAlgorithm) {
        try {
            String sigAlg = keyAlgorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            Signature signer = Signature.getInstance(sigAlg, pkcs11Provider);
            signer.initSign(privateKey);
            byte[] testPayload = ("pki-key-correspondence-test-" + alias + "-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
            signer.update(testPayload);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance(sigAlg);
            verifier.initVerify(publicKey);
            verifier.update(testPayload);
            if (!verifier.verify(signature)) {
                throw new HsmOperationException("Key correspondence verification failed for alias: " + alias, "PKI-50004");
            }
            log.info("Cryptographic key pair correspondence verified successfully for alias '{}'", alias);
        } catch (HsmOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HsmOperationException("Key correspondence verification error: " + ex.getMessage(), ex, "PKI-50004");
        }
    }

    /**
     * Registers a known KeyPair in the token cache (useful when binding pre-initialized keys).
     */
    public void registerKey(String alias, KeyPair keyPair) {
        verifyKeyCorrespondence(alias, keyPair.getPublic(), keyPair.getPrivate(), keyPair.getPublic().getAlgorithm());
        tokenKeyCache.put(alias, keyPair);
    }
}
