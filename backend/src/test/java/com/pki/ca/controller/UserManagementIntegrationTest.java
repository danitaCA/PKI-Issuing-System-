package com.pki.ca.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.dto.request.CreateUserRequest;
import com.pki.ca.dto.request.LockUserRequest;
import com.pki.ca.dto.request.UpdateUserRequest;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import com.pki.ca.security.JwtTokenProvider;
import com.pki.ca.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired(required = false)
    private com.pki.ca.repository.EnrollmentRequestRepository enrollmentRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        if (enrollmentRepository != null) {
            enrollmentRepository.deleteAll();
        }
        userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals("test_ca_admin"))
                .forEach(userRepository::delete);

        Role adminRole = roleRepository.findByName(RoleName.ROLE_CA_ADMIN).orElseThrow();
        User admin = new User("admin_mgmt_user", "admin_mgmt@pki.test", passwordEncoder.encode("AdminPass123!"), "Admin Manager");
        admin.setRoles(Set.of(adminRole));
        admin = userRepository.save(admin);
        adminToken = tokenProvider.generateAccessToken(UserPrincipal.create(admin));
    }

    @Test
    @DisplayName("CA Administrator can provision a new RA Operator user")
    void caAdminCanProvisionNewUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "new_ra_operator",
                "new_ra@pki.test",
                "NewRaOperatorPass123!",
                "New RA Operator",
                Set.of(RoleName.ROLE_RA_OPERATOR)
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_ra_operator"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_RA_OPERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User savedUser = userRepository.findByUsername("new_ra_operator").orElseThrow();
        assertThat(savedUser.getFullName()).isEqualTo("New RA Operator");
    }

    @Test
    @DisplayName("Duplicate username or email provisioning is rejected with 409 Conflict")
    void duplicateUserCreationReturns409() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        User existing = new User("duplicate_user", "existing@pki.test", passwordEncoder.encode("Pass123!"), "Existing");
        existing.setRoles(Set.of(role));
        userRepository.save(existing);

        CreateUserRequest request = new CreateUserRequest(
                "duplicate_user",
                "different_email@pki.test",
                "Password123!",
                "Duplicate Target",
                Set.of(RoleName.ROLE_END_ENTITY)
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("CA Administrator can lock and unlock a user account")
    void caAdminCanLockAndUnlockUser() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        User target = new User("lock_target_user", "target@pki.test", passwordEncoder.encode("Pass123!"), "Target");
        target.setRoles(Set.of(role));
        target = userRepository.save(target);

        // Lock
        LockUserRequest lockRequest = new LockUserRequest(60, "Security Investigation");
        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        User lockedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);

        // Unlock
        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User unlockedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unlockedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
