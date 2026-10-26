package com.pki.ca.security;

import com.pki.ca.config.SecurityConfigurationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductionSecurityValidatorTest {

    private static final String STRONG_SECRET = "StrongProductionSecretKeyThatIsAtLeast32BytesLongAndRandom1234567890!";
    private static final String STRONG_PASSWORD = "StrongUniqueAdminPassword987!";

    @Test
    @DisplayName("Production startup fails if default development JWT secret is used")
    void validateSecurityConfiguration_failsInProductionWithDefaultSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", SecurityConfigurationValidator.INSECURE_DEV_JWT_SECRET);
        ReflectionTestUtils.setField(validator, "bootstrapAdminEnabled", false);
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://pki.company.com");

        assertThatThrownBy(validator::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use the default development JWT secret key");
    }

    @Test
    @DisplayName("Production startup fails if default bootstrap admin password is used")
    void validateSecurityConfiguration_failsInProductionWithDefaultBootstrapPassword() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", STRONG_SECRET);
        ReflectionTestUtils.setField(validator, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(validator, "bootstrapAdminPassword", SecurityConfigurationValidator.INSECURE_DEFAULT_BOOTSTRAP_PASSWORD);
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://pki.company.com");

        assertThatThrownBy(validator::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use default bootstrap admin credentials");
    }

    @Test
    @DisplayName("Production startup fails if wildcard CORS origin is configured")
    void validateSecurityConfiguration_failsInProductionWithWildcardCors() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", STRONG_SECRET);
        ReflectionTestUtils.setField(validator, "bootstrapAdminEnabled", false);
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://pki.company.com,*");

        assertThatThrownBy(validator::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard CORS origin ('*') is strictly forbidden");
    }

    @Test
    @DisplayName("Production startup succeeds when all secrets are externalized, strong, and CORS is safe")
    void validateSecurityConfiguration_succeedsWithStrongProductionConfig() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", STRONG_SECRET);
        ReflectionTestUtils.setField(validator, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(validator, "bootstrapAdminPassword", STRONG_PASSWORD);
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://pki.company.com,https://admin.pki.company.com");

        assertThatCode(validator::validateSecurityConfiguration).doesNotThrowAnyException();
    }
}
