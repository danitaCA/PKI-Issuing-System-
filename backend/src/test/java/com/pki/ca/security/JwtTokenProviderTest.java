package com.pki.ca.security;

import com.pki.ca.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "TestSecretKeyForTestingJwtTokenProviderMustBeAtLeast32BytesLong!";
    private final long expirationMs = 60000; // 1 minute

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, expirationMs, "pki-test-issuer");
    }

    @Test
    @DisplayName("Should generate valid JWT token with user claims and verify correctly")
    void shouldGenerateAndValidateToken() {
        UserPrincipal principal = new UserPrincipal(
                100L,
                "ra_operator_test",
                "ra@pki.test",
                "hashedPass",
                "Test RA Operator",
                UserStatus.ACTIVE,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_RA_OPERATOR"))
        );

        String token = tokenProvider.generateAccessToken(principal);

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("ra_operator_test");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(100L);
        assertThat(tokenProvider.getRolesFromToken(token)).containsExactly("ROLE_RA_OPERATOR");
    }

    @Test
    @DisplayName("Should reject tampered token signature")
    void shouldRejectTamperedToken() {
        UserPrincipal principal = new UserPrincipal(
                101L,
                "auditor_test",
                "auditor@pki.test",
                "hashedPass",
                "Test Auditor",
                UserStatus.ACTIVE,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        String token = tokenProvider.generateAccessToken(principal);
        String tamperedToken = token + "xyz";

        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject token signed by same key but with an unexpected issuer")
    void shouldRejectTokenWithInvalidIssuer() {
        // Create another token provider sharing the secret key but with a foreign issuer
        JwtTokenProvider rogueIssuerProvider = new JwtTokenProvider(secret, expirationMs, "rogue-external-system");

        UserPrincipal principal = new UserPrincipal(
                102L,
                "foreign_user",
                "foreign@pki.test",
                "hashedPass",
                "Foreign User",
                UserStatus.ACTIVE,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_END_ENTITY"))
        );

        String foreignToken = rogueIssuerProvider.generateAccessToken(principal);

        // Verification on our tokenProvider must fail because the issuer claim does not match
        assertThat(tokenProvider.validateToken(foreignToken)).isFalse();
    }
}
