package com.pki.ca;

import com.pki.ca.entity.Role;
import com.pki.ca.enums.RoleName;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.SystemConfigurationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationAndSchemaTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Autowired
    private com.pki.ca.repository.CertificateProfileRepository profileRepository;

    @Test
    @DisplayName("Should successfully apply Flyway migrations and seed the five primary roles")
    void shouldSeedFivePrimaryRoles() {
        List<Role> roles = roleRepository.findAll();

        assertThat(roles).hasSize(5);
        assertThat(roles.stream().map(Role::getName).toList())
                .containsExactlyInAnyOrder(
                        RoleName.ROLE_CA_ADMIN,
                        RoleName.ROLE_RA_OPERATOR,
                        RoleName.ROLE_SECURITY_OFFICER,
                        RoleName.ROLE_AUDITOR,
                        RoleName.ROLE_END_ENTITY
                );
    }

    @Test
    @DisplayName("Should seed baseline system configurations")
    void shouldSeedSystemConfigurations() {
        assertThat(systemConfigurationRepository.existsByConfigKey("auth.max_failed_attempts")).isTrue();
        assertThat(systemConfigurationRepository.existsByConfigKey("auth.lockout_duration_minutes")).isTrue();
        assertThat(systemConfigurationRepository.existsByConfigKey("auth.jwt_expiration_minutes")).isTrue();
    }

    @Test
    @DisplayName("Should seed Phase 3 baseline certificate profiles via Flyway V3")
    void shouldSeedCertificateProfiles() {
        assertThat(profileRepository.existsByProfileCode("TLS_SERVER")).isTrue();
        assertThat(profileRepository.existsByProfileCode("TLS_CLIENT")).isTrue();
        assertThat(profileRepository.existsByProfileCode("CODE_SIGNING")).isTrue();
        assertThat(profileRepository.existsByProfileCode("DEVICE_IDENTITY")).isTrue();
    }
}
