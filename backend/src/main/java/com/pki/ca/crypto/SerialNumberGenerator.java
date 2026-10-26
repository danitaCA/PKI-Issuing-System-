package com.pki.ca.crypto;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Generates cryptographically secure, unpredictable, positive serial numbers
 * conforming to RFC 5280 Section 4.1.2.2 and CA/Browser Forum Baseline Requirements.
 *
 * Requirements Enforced:
 * - Minimum 64 bits (RFC 5280) and 128 bits (RFC 5280 / CABF) of entropy.
 * - Positivity: serial > 0 (strictly positive BigInteger, sign bit 0).
 * - Max length: 20 octets (160 bits). 128 bits = 16 octets, well within bounds.
 * - Formatted as 32-character lowercase hexadecimal string.
 */
@Component
public class SerialNumberGenerator {

    private final SecureRandom secureRandom;

    public SerialNumberGenerator() {
        this.secureRandom = new SecureRandom();
    }

    public SerialNumberGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Generates a 128-bit positive random serial number.
     *
     * @return Positive BigInteger with 128 bits of cryptographic entropy.
     */
    public BigInteger generateSerialNumber() {
        // Generate 128 bits of random data
        BigInteger serial = new BigInteger(128, secureRandom).abs();

        // Enforce strictly positive integer (RFC 5280 Section 4.1.2.2)
        if (serial.signum() <= 0) {
            serial = BigInteger.ONE;
        }

        return serial;
    }

    /**
     * Generates a 128-bit positive random serial number formatted as a 32-character lowercase hex string.
     */
    public String generateSerialNumberHex() {
        BigInteger serial = generateSerialNumber();
        String hex = serial.toString(16).toLowerCase();
        // Zero-pad to 32 characters (16 bytes = 128 bits)
        if (hex.length() < 32) {
            hex = "0".repeat(32 - hex.length()) + hex;
        }
        return hex;
    }
}
