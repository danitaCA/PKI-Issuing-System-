package com.pki.ca.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.dto.request.LoginRequest;
import com.pki.ca.dto.request.RefreshTokenRequest;
import com.pki.ca.dto.request.RegisterRequest;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import com.pki.ca.repository.UserTokenRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userTokenRepository.deleteAll();
        // Clean test users but keep bootstrap admin if exists
        userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals("test_ca_admin"))
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("Should authenticate valid user and return access & refresh tokens")
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_RA_OPERATOR).orElseThrow();
        User user = new User("ra_operator_test", "ra_test@pki.test", passwordEncoder.encode("ValidPassword123!"), "RA Operator");
        user.setRoles(Set.of(role));
        userRepository.save(user);

        LoginRequest request = new LoginRequest("ra_operator_test", "ValidPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("ra_operator_test"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_RA_OPERATOR"));
    }

    @Test
    @DisplayName("Should reject invalid password with 401 Unauthorized without revealing user existence")
    void shouldRejectInvalidPasswordWithoutUserEnumeration() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        User user = new User("end_entity_test", "ee_test@pki.test", passwordEncoder.encode("ValidPassword123!"), "End Entity");
        user.setRoles(Set.of(role));
        userRepository.save(user);

        LoginRequest request = new LoginRequest("end_entity_test", "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Failed"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password provided."));
    }

    @Test
    @DisplayName("Should lock account after 5 consecutive failed login attempts")
    void shouldLockAccountAfterFiveFailedAttempts() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        User user = new User("lockout_test_user", "lockout@pki.test", passwordEncoder.encode("ValidPassword123!"), "Lockout Target");
        user.setRoles(Set.of(role));
        userRepository.save(user);

        LoginRequest request = new LoginRequest("lockout_test_user", "WrongPassword123!");

        // 5 consecutive failed attempts
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should return 423 Locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.title").value("Account Locked"));

        User lockedUser = userRepository.findByUsername("lockout_test_user").orElseThrow();
        assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(lockedUser.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("Should exchange valid refresh token for a new access token")
    void shouldRefreshAccessToken() throws Exception {
        Role role = roleRepository.findByName(RoleName.ROLE_AUDITOR).orElseThrow();
        User user = new User("auditor_test", "auditor@pki.test", passwordEncoder.encode("AuditorPassword123!"), "Auditor");
        user.setRoles(Set.of(role));
        userRepository.save(user);

        // Login first
        LoginRequest loginRequest = new LoginRequest("auditor_test", "AuditorPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseBody).get("refreshToken").asText();

        // Refresh
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("auditor_test"));
    }

    @Test
    @DisplayName("Should self-register new End Entity with strictly ROLE_END_ENTITY")
    void shouldSelfRegisterEndEntity() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "new_developer",
                "developer@pki.test",
                "SecureDevPassword123!",
                "New Developer"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_developer"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_END_ENTITY"));

        User savedUser = userRepository.findByUsername("new_developer").orElseThrow();
        assertThat(savedUser.getRoles()).extracting(Role::getName).containsExactly(RoleName.ROLE_END_ENTITY);
    }
}
