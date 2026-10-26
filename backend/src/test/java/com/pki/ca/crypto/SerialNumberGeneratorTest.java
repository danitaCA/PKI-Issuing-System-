package com.pki.ca.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SerialNumberGeneratorTest {

    private final SerialNumberGenerator generator = new SerialNumberGenerator();

    @Test
    @DisplayName("Should generate strictly positive serial numbers conforming to RFC 5280")
    void shouldGenerateStrictlyPositiveSerialNumbers() {
        for (int i = 0; i < 500; i++) {
            BigInteger serial = generator.generateSerialNumber();
            // RFC 5280 Section 4.1.2.2: Serial must be positive integer
            assertThat(serial.signum()).isEqualTo(1);
            // Length must not exceed 20 octets (160 bits). 128 bits = 16 octets.
            assertThat(serial.bitLength()).isLessThanOrEqualTo(128);
        }
    }

    @Test
    @DisplayName("Should generate formatted 32-character hexadecimal lowercase strings")
    void shouldGenerateFormattedHexStrings() {
        for (int i = 0; i < 100; i++) {
            String hex = generator.generateSerialNumberHex();
            assertThat(hex).hasSize(32);
            assertThat(hex).matches("^[0-9a-f]{32}$");
        }
    }

    @Test
    @DisplayName("Should guarantee uniqueness with 128 bits of entropy across 1000 generations")
    void shouldGuaranteeUniquenessAcrossGenerations() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String serialHex = generator.generateSerialNumberHex();
            boolean added = generated.add(serialHex);
            assertThat(added)
                    .withFailMessage("Collision detected for serial number: " + serialHex)
                    .isTrue();
        }
        assertThat(generated).hasSize(1000);
    }
}
