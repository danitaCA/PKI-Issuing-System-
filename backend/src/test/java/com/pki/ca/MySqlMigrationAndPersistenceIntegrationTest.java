package com.pki.ca;

import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.Socket;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
public class MySqlMigrationAndPersistenceIntegrationTest {

    private static boolean mysqlReachable = false;

    @BeforeAll
    static void checkMySqlPort() {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/pki_test_disposable_db?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=1000",
                "pki_app_user", "AppPassSecure123!")) {
            mysqlReachable = true;
        } catch (Exception e) {
            mysqlReachable = false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        checkMySqlPort();
        if (mysqlReachable) {
            registry.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/pki_test_disposable_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
            registry.add("spring.datasource.username", () -> "pki_app_user");
            registry.add("spring.datasource.password", () -> "AppPassSecure123!");
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
        }
    }

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private RoleRepository roleRepository;

    @Autowired(required = false)
    private com.pki.ca.repository.CertificateProfileRepository profileRepository;

    @Test
    @DisplayName("Verify Flyway migrations V1, V2, V3 run on real MySQL 8 and verify persistence")
    void testFlywayAndPersistenceOnRealMySql() {
        assumeTrue(mysqlReachable, "MySQL 8 container is not reachable on localhost:3306; skipping MySQL integration test.");

        assertThat(flyway).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(roleRepository).isNotNull();

        // 1. Verify Flyway info on MySQL 8
        var applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();
        assertThat(applied).anyMatch(m -> m.getVersion().getVersion().equals("1"));
        assertThat(applied).anyMatch(m -> m.getVersion().getVersion().equals("2"));
        assertThat(applied).anyMatch(m -> m.getVersion().getVersion().equals("3"));

        // 2. Verify seeded roles on MySQL 8
        for (RoleName roleName : RoleName.values()) {
            assertThat(roleRepository.findByName(roleName)).isPresent();
        }

        // 3. Verify seeded profiles on MySQL 8
        if (profileRepository != null) {
            assertThat(profileRepository.existsByProfileCode("TLS_SERVER")).isTrue();
            assertThat(profileRepository.existsByProfileCode("CODE_SIGNING")).isTrue();
        }

        // 4. Test disposable entity persistence on MySQL 8
        String testUsername = "mysql_disposable_user";
        userRepository.findByUsername(testUsername).ifPresent(userRepository::delete);

        Role auditorRole = roleRepository.findByName(RoleName.ROLE_AUDITOR).orElseThrow();
        User testUser = new User(testUsername, "mysql.test@pki.local", "hashedPassword123", "MySQL Tester");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRoles(Set.of(auditorRole));

        User saved = userRepository.save(testUser);
        assertThat(saved.getId()).isNotNull();

        User retrieved = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getUsername()).isEqualTo(testUsername);
        assertThat(retrieved.hasRole(RoleName.ROLE_AUDITOR)).isTrue();
        assertThat(retrieved.getVersion()).isEqualTo(0);

        // Clean up disposable entity
        userRepository.delete(retrieved);
        assertThat(userRepository.findByUsername(testUsername)).isEmpty();
    }
}
