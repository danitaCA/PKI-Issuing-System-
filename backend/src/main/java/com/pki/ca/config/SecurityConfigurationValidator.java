package com.pki.ca.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class SecurityConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfigurationValidator.class);
    public static final String INSECURE_DEV_JWT_SECRET = "DefaultDevSecretKeyMustBeOverriddenInProductionAndMustBeAtLeast32Bytes!";
    public static final String INSECURE_DEFAULT_BOOTSTRAP_PASSWORD = "AdminInitialPassword123!";

    private final Environment environment;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.bootstrap-admin.enabled:true}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.security.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${app.security.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    public SecurityConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecurityConfiguration() {
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        // Global check: JWT secret key must always be at least 32 bytes (256 bits)
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FATAL SECURITY CONFIGURATION: JWT secret must be at least 256 bits (32 bytes) long.");
        }

        if (isProduction) {
            logger.info("Verifying production security invariants...");

            // In production, default development JWT secret is strictly forbidden
            if (jwtSecret.contains("DefaultDevSecretKey") || jwtSecret.equals(INSECURE_DEV_JWT_SECRET)) {
                throw new IllegalStateException(
                        "FATAL SECURITY MISCONFIGURATION: Production deployment cannot use the default development JWT secret key. " +
                        "Configure a cryptographically secure, random 256-bit secret via the JWT_SECRET environment variable."
                );
            }

            // In production, default bootstrap password is strictly forbidden
            if (bootstrapAdminEnabled) {
                if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank() ||
                        bootstrapAdminPassword.equals(INSECURE_DEFAULT_BOOTSTRAP_PASSWORD) ||
                        bootstrapAdminPassword.equals("ChangeMe123!")) {
                    throw new IllegalStateException(
                            "FATAL SECURITY MISCONFIGURATION: Production deployment cannot use default bootstrap admin credentials. " +
                            "Configure a unique, strong password via the BOOTSTRAP_ADMIN_PASSWORD environment variable or disable bootstrap."
                    );
                }
            }

            // In production, CORS must not allow wildcard origins with credentials
            List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
            for (String origin : origins) {
                if (origin.trim().equals("*")) {
                    throw new IllegalStateException(
                            "FATAL SECURITY MISCONFIGURATION: Wildcard CORS origin ('*') is strictly forbidden when credentials are enabled."
                    );
                }
            }

            logger.info("Production security configuration validated successfully. Invariants enforced.");
        } else {
            logger.debug("Non-production profile active. Standard development safeguards in effect.");
        }
    }
}
