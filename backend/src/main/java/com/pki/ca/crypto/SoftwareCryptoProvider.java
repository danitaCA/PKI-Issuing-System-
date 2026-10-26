package com.pki.ca.crypto;

import com.pki.ca.exception.HsmOperationException;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Software-based Cryptographic Provider for isolated unit tests and environments
 * where hardware tokens (or SoftHSM) are explicitly disabled (app.hsm.enabled=false).
 */
@Component
@ConditionalOnProperty(name = "app.hsm.enabled", havingValue = "false")
public class SoftwareCryptoProvider implements CryptoProvider {

    private static final Logger log = LoggerFactory.getLogger(SoftwareCryptoProvider.class);

    private static final Map<String, KeyPair> keyStore = new ConcurrentHashMap<>();

    @Override
    public boolean isHsmAvailable() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "BouncyCastleSoftwareFallback-TestHarness";
    }

    @Override
    public Provider getProvider() {
        return Security.getProvider("BC");
    }

    @Override
    public KeyPair generateKeyPairOnToken(String alias, String algorithm, int keySize) {
        log.info("Generating software test key pair. Alias: '{}', Algorithm: '{}', KeySize: {}", alias, algorithm, keySize);
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm, "BC");
            kpg.initialize(keySize);
            KeyPair kp = kpg.generateKeyPair();
            keyStore.put(alias, kp);
            return kp;
        } catch (Exception ex) {
            throw new HsmOperationException("Software key generation failed: " + ex.getMessage(), ex, "PKI-50005");
        }
    }

    @Override
    public boolean keyExists(String alias) {
        return keyStore.containsKey(alias);
    }

    @Override
    public PublicKey getPublicKey(String alias) {
        KeyPair kp = keyStore.get(alias);
        if (kp == null) {
            throw new HsmOperationException("Key alias not found: " + alias, "PKI-50003");
        }
        return kp.getPublic();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        KeyPair kp = keyStore.get(alias);
        if (kp == null) {
            throw new HsmOperationException("Key alias not found: " + alias, "PKI-50003");
        }
        return kp.getPrivate();
    }

    @Override
    public ContentSigner createContentSigner(String alias, String signatureAlgorithm) {
        PrivateKey privateKey = getPrivateKey(alias);
        try {
            return new JcaContentSignerBuilder(signatureAlgorithm)
                    .setProvider("BC")
                    .build(privateKey);
        } catch (Exception ex) {
            throw new HsmOperationException("Failed to create software content signer: " + ex.getMessage(), ex, "PKI-50006");
        }
    }

    @Override
    public boolean verifySignature(PublicKey publicKey, byte[] data, byte[] signature, String signatureAlgorithm) {
        try {
            Signature verifier = Signature.getInstance(signatureAlgorithm, "BC");
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception ex) {
            log.error("Software signature verification error: {}", ex.getMessage());
            return false;
        }
    }

    public void registerKey(String alias, KeyPair keyPair) {
        keyStore.put(alias, keyPair);
    }
}
