package com.pki.ca.crypto;

import com.pki.ca.exception.HsmOperationException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Hardware Security Module (SoftHSM2) Integration Test.
 *
 * Verifies live SunPKCS11 provider integration, on-token asymmetric key generation,
 * unextractable key attributes, cryptographic signing delegation to token boundary,
 * and fail-safe exception behavior without software fallback.
 */
class SoftHsmPkcs11IntegrationTest {

    private static String softhsmLib;

    @BeforeAll
    static void checkSoftHsmInstalled() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        String winPath = "C:/SoftHSM2/lib/softhsm2-x64.dll";
        String linuxPath = "/usr/lib/softhsm/libsofthsm2.so";
        String debianPath = "/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so";

        if (new File(winPath).exists()) {
            softhsmLib = winPath;
        } else if (new File(linuxPath).exists()) {
            softhsmLib = linuxPath;
        } else if (new File(debianPath).exists()) {
            softhsmLib = debianPath;
        }

        // Only run if SoftHSM is installed on test host
        Assumptions.assumeTrue(softhsmLib != null, "SoftHSM2 library not found on host. Skipping live PKCS#11 test.");
    }

    @Test
    @DisplayName("Should initialize live SunPKCS11 provider and generate unextractable key inside SoftHSM token")
    void shouldGenerateUnextractableKeyInSoftHsmToken() {
        Pkcs11HsmAdapter adapter = new Pkcs11HsmAdapter(
                softhsmLib,
                1, // slotListIndex 1: PkiIssuingCaTestToken
                "123456",
                "SoftHSM-LiveIntegrationTest"
        );
        try {
            adapter.initialize();

            assertThat(adapter.isHsmAvailable()).isTrue();
            assertThat(adapter.getProviderName()).contains("SunPKCS11");

            // Generate on-token key pair
            String testAlias = "integration-test-ca-key-" + System.currentTimeMillis();
            KeyPair keyPair = adapter.generateKeyPairOnToken(testAlias, "RSA", 2048);

            assertThat(keyPair).isNotNull();
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            assertThat(privateKey).isNotNull();
            assertThat(publicKey).isNotNull();

            // Security Invariant: Private key handle MUST be internal PKCS#11 pointer with null encoded bytes
            assertThat(privateKey.getClass().getName()).contains("P11Key");
            assertThat(privateKey.getEncoded())
                    .withFailMessage("Private key returned non-null encoded bytes! Key must be unextractable.")
                    .isNull();

            // Explicit Key Extraction Tests: Verify that every JCA export path is blocked
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            assertThatThrownBy(() -> kf.getKeySpec(privateKey, java.security.spec.RSAPrivateCrtKeySpec.class))
                    .as("Extracting RSA private CRT spec from token key must fail")
                    .isInstanceOf(Exception.class);

            assertThatThrownBy(() -> kf.getKeySpec(privateKey, java.security.spec.PKCS8EncodedKeySpec.class))
                    .as("Extracting PKCS8EncodedKeySpec from token key must fail")
                    .isInstanceOf(Exception.class);

            // Perform test signing via token ContentSigner
            ContentSigner contentSigner = adapter.createContentSigner(testAlias, "SHA256withRSA");
            assertThat(contentSigner).isNotNull();

            // Sign test payload directly with token private key
            byte[] payload = "test-token-payload-verification".getBytes(StandardCharsets.UTF_8);
            Signature sig = Signature.getInstance("SHA256withRSA", adapter.getProvider());
            sig.initSign(privateKey);
            sig.update(payload);
            byte[] signatureBytes = sig.sign();

            // Verify with public key
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            assertThat(verifier.verify(signatureBytes)).isTrue();
        } catch (Exception ex) {
            throw new RuntimeException("Live token signature test failed", ex);
        } finally {
            adapter.logout();
        }
    }

    @Test
    @DisplayName("Should fail safely with HsmOperationException and PKI-50002 when incorrect PIN is provided")
    void shouldFailSafelyOnIncorrectTokenPin() {
        // Use slotListIndex 0 (PKIToken) which has not been logged in this test run
        Pkcs11HsmAdapter adapter = new Pkcs11HsmAdapter(
                softhsmLib,
                0,
                "wrong-pin-999999", // Incorrect PIN
                "SoftHSM-WrongPinTest"
        );

        try {
            assertThatThrownBy(adapter::initialize)
                    .isInstanceOf(HsmOperationException.class)
                    .satisfies(ex -> {
                        HsmOperationException hex = (HsmOperationException) ex;
                        assertThat(hex.getErrorCode()).isEqualTo("PKI-50002");
                        assertThat(hex.getMessage()).contains("Incorrect token PIN");
                    });
        } finally {
            adapter.logout();
        }
    }

    @Test
    @DisplayName("Should throw HsmOperationException PKI-50003 when requesting non-existent key alias")
    void shouldThrowExceptionOnMissingKeyAlias() {
        Pkcs11HsmAdapter adapter = new Pkcs11HsmAdapter(
                softhsmLib,
                1,
                "123456",
                "SoftHSM-MissingKeyTest"
        );
        try {
            adapter.initialize();

            assertThatThrownBy(() -> adapter.getPrivateKey("non-existent-key-alias-999"))
                    .isInstanceOf(HsmOperationException.class)
                    .satisfies(ex -> {
                        HsmOperationException hex = (HsmOperationException) ex;
                        assertThat(hex.getErrorCode()).isEqualTo("PKI-50003");
                    });
        } finally {
            adapter.logout();
        }
    }

    @Test
    @DisplayName("Should fail safely with HsmOperationException and PKI-50001 when non-existent library path is provided")
    void shouldFailSafelyOnNonExistentLibraryPath() {
        Pkcs11HsmAdapter adapter = new Pkcs11HsmAdapter(
                "C:/non/existent/path/libsofthsm.dll",
                1,
                "123456",
                "SoftHSM-InvalidLibTest"
        );
        assertThatThrownBy(adapter::initialize)
                .isInstanceOf(HsmOperationException.class)
                .satisfies(ex -> {
                    HsmOperationException hex = (HsmOperationException) ex;
                    assertThat(hex.getErrorCode()).isEqualTo("PKI-50001");
                });
    }

    @Test
    @DisplayName("Should fail safely with HsmOperationException when invalid slot index is requested")
    void shouldFailSafelyOnInvalidSlotIndex() {
        Pkcs11HsmAdapter adapter = new Pkcs11HsmAdapter(
                softhsmLib,
                9999, // Non-existent slot list index
                "123456",
                "SoftHSM-InvalidSlotTest"
        );
        assertThatThrownBy(adapter::initialize)
                .isInstanceOf(HsmOperationException.class)
                .satisfies(ex -> {
                    HsmOperationException hex = (HsmOperationException) ex;
                    assertThat(hex.getErrorCode()).isEqualTo("PKI-50001");
                });
    }
}
