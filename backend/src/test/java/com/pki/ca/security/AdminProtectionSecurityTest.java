package com.pki.ca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.dto.request.LockUserRequest;
import com.pki.ca.dto.request.UpdateUserRequest;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminProtectionSecurityTest {

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

    @Autowired(required = false)
    private com.pki.ca.repository.EnrollmentRequestRepository enrollmentRepository;

    private User loneAdmin;

    @BeforeEach
    void setUp() {
        if (enrollmentRepository != null) {
            enrollmentRepository.deleteAll();
        }
        // Clean up users to leave exactly one active CA admin
        userRepository.deleteAll();

        Role adminRole = roleRepository.findByName(RoleName.ROLE_CA_ADMIN).orElseThrow();
        loneAdmin = new User("sole_admin", "sole.admin@pki.local", passwordEncoder.encode("Password123!"), "Sole Administrator");
        loneAdmin.setStatus(UserStatus.ACTIVE);
        loneAdmin.setRoles(Set.of(adminRole));
        loneAdmin = userRepository.save(loneAdmin);
    }

    @Test
    @WithMockUser(username = "sole_admin", roles = {"CA_ADMIN"})
    @DisplayName("Sole active CA Administrator cannot be stripped of CA_ADMIN role")
    void soleAdmin_cannotDemoteThemselves() throws Exception {
        Role eeRole = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();

        UpdateUserRequest updateReq = new UpdateUserRequest();
        updateReq.setRoles(Set.of(eeRole.getName())); // Attempting to strip ROLE_CA_ADMIN

        mockMvc.perform(put("/api/v1/admin/users/" + loneAdmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40030"))
                .andExpect(jsonPath("$.detail").value("Cannot demote, lock, or disable the last remaining active CA Administrator."));

        // Verify database state was untouched
        User reloaded = userRepository.findById(loneAdmin.getId()).orElseThrow();
        assertThat(reloaded.hasRole(RoleName.ROLE_CA_ADMIN)).isTrue();
    }

    @Test
    @WithMockUser(username = "sole_admin", roles = {"CA_ADMIN"})
    @DisplayName("Sole active CA Administrator cannot be administratively locked")
    void soleAdmin_cannotBeLocked() throws Exception {
        LockUserRequest lockReq = new LockUserRequest(60, "Emergency test lock");

        mockMvc.perform(post("/api/v1/admin/users/" + loneAdmin.getId() + "/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40030"))
                .andExpect(jsonPath("$.detail").value("Cannot administratively lock the last remaining active CA Administrator."));

        // Verify status remains ACTIVE
        User reloaded = userRepository.findById(loneAdmin.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = "sole_admin", roles = {"CA_ADMIN"})
    @DisplayName("Locking an administrator is permitted when another active administrator exists")
    void admin_canBeLockedWhenAnotherAdminExists() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_CA_ADMIN).orElseThrow();

        // Create a second active administrator
        User secondAdmin = new User("second_admin", "second@pki.local", passwordEncoder.encode("Password123!"), "Second Admin");
        secondAdmin.setStatus(UserStatus.ACTIVE);
        secondAdmin.setRoles(Set.of(adminRole));
        secondAdmin = userRepository.save(secondAdmin);

        LockUserRequest lockReq = new LockUserRequest(30, "Maintenance lock");

        // Locking secondAdmin must now succeed because loneAdmin remains active
        mockMvc.perform(post("/api/v1/admin/users/" + secondAdmin.getId() + "/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        User reloaded = userRepository.findById(secondAdmin.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}
