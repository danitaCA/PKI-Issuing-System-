package com.pki.ca.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private String raOperatorToken;
    private String securityOfficerToken;
    private String auditorToken;
    private String endEntityToken;

    @BeforeEach
    void setUp() {
        if (enrollmentRepository != null) {
            enrollmentRepository.deleteAll();
        }
        userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals("test_ca_admin"))
                .forEach(userRepository::delete);

        adminToken = createTokenForUser("admin_test_user", RoleName.ROLE_CA_ADMIN);
        raOperatorToken = createTokenForUser("ra_test_user", RoleName.ROLE_RA_OPERATOR);
        securityOfficerToken = createTokenForUser("sec_test_user", RoleName.ROLE_SECURITY_OFFICER);
        auditorToken = createTokenForUser("auditor_test_user", RoleName.ROLE_AUDITOR);
        endEntityToken = createTokenForUser("ee_test_user", RoleName.ROLE_END_ENTITY);
    }

    private String createTokenForUser(String username, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(username, username + "@pki.test", passwordEncoder.encode("Pass123!"), username);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);
        return tokenProvider.generateAccessToken(UserPrincipal.create(user));
    }

    @Test
    @DisplayName("Unauthenticated request to admin endpoint must return 401 Unauthorized")
    void unauthenticatedRequestToAdminMustReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("ROLE_END_ENTITY accessing admin user management must return 403 Forbidden")
    void endEntityAccessingAdminMustReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + endEntityToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("ROLE_RA_OPERATOR accessing admin user management must return 403 Forbidden")
    void raOperatorAccessingAdminMustReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + raOperatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ROLE_SECURITY_OFFICER accessing admin user management must return 403 Forbidden")
    void securityOfficerAccessingAdminMustReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + securityOfficerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ROLE_AUDITOR accessing admin user management must return 403 Forbidden")
    void auditorAccessingAdminMustReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ROLE_CA_ADMIN accessing admin user management must return 200 OK")
    void caAdminAccessingAdminMustReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("Authenticated user querying /api/v1/auth/me receives their own profile and evaluated permissions")
    void authenticatedUserReceivesOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + raOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ra_test_user"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_RA_OPERATOR"))
                .andExpect(jsonPath("$.permissions").isArray());
    }
}
