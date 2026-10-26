package com.pki.ca.crypto;

import org.bouncycastle.operator.ContentSigner;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;

/**
 * High-level cryptographic provider interface decoupling PKI business logic
 * from underlying hardware security modules (PKCS#11) and software keystores.
 */
public interface CryptoProvider {

    /**
     * Checks if the cryptographic provider and hardware token are reachable and operational.
     */
    boolean isHsmAvailable();

    /**
     * Returns the name/identifier of this cryptographic provider.
     */
    String getProviderName();

    /**
     * Generates an asymmetric key pair directly inside the secure token boundary.
     * The private key must be generated with CKA_PRIVATE=true, CKA_SENSITIVE=true, CKA_EXTRACTABLE=false.
     * At no point does the private key enter application memory.
     */
    KeyPair generateKeyPairOnToken(String alias, String algorithm, int keySize);

    /**
     * Checks if a key pair with the specified alias exists in the token.
     */
    boolean keyExists(String alias);

    /**
     * Retrieves the public key for the specified alias.
     */
    PublicKey getPublicKey(String alias);

    /**
     * Retrieves an opaque handle to the private key for signing operations.
     * The private key bytes are not extractable.
     */
    PrivateKey getPrivateKey(String alias);

    /**
     * Creates a Bouncy Castle ContentSigner backed by the token private key.
     */
    ContentSigner createContentSigner(String alias, String signatureAlgorithm);

    /**
     * Verifies a digital signature against the provided public key.
     */
    boolean verifySignature(PublicKey publicKey, byte[] data, byte[] signature, String signatureAlgorithm);

    /**
     * Returns the underlying JCA Security Provider.
     */
    Provider getProvider();
}
