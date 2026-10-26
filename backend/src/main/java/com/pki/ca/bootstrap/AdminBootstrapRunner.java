package com.pki.ca.bootstrap;

import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.bootstrap-admin.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.security.bootstrap-admin.username:ca_admin}")
    private String bootstrapUsername;

    @Value("${app.security.bootstrap-admin.email:ca-admin@pki.internal.corp}")
    private String bootstrapEmail;

    @Value("${app.security.bootstrap-admin.password:}")
    private String bootstrapPassword;

    @Value("${app.security.bootstrap-admin.full-name:PKI System Administrator}")
    private String bootstrapFullName;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            logger.info("Bootstrap initialization is disabled by configuration.");
            return;
        }

        long adminCount = userRepository.countByRoleName(RoleName.ROLE_CA_ADMIN);
        if (adminCount > 0) {
            logger.info("CA Administrator already provisioned (found {} admin accounts). Skipping bootstrap initialization.", adminCount);
            return;
        }

        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            logger.warn("Bootstrap admin is enabled but no BOOTSTRAP_ADMIN_PASSWORD provided. Skipping initial account creation.");
            return;
        }

        logger.info("No CA Administrator detected in repository. Initializing bootstrap administrator: '{}'", bootstrapUsername);

        Role adminRole = roleRepository.findByName(RoleName.ROLE_CA_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_CA_ADMIN, "CA Administrator")));

        Role endEntityRole = roleRepository.findByName(RoleName.ROLE_END_ENTITY)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_END_ENTITY, "End Entity")));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        roles.add(endEntityRole);

        User adminUser = new User(
                bootstrapUsername,
                bootstrapEmail,
                passwordEncoder.encode(bootstrapPassword),
                bootstrapFullName
        );
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRoles(roles);

        userRepository.save(adminUser);
        logger.info("Initial CA Administrator '{}' provisioned successfully with roles {}. Credentials should be rotated immediately.",
                bootstrapUsername, roles.stream().map(r -> r.getName().name()).toList());
    }
}
