package com.pki.ca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ca.dto.request.LoginRequest;
import com.pki.ca.dto.request.RefreshTokenRequest;
import com.pki.ca.dto.response.AuthResponse;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RefreshTokenSecurityTest {

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

    private static final String TEST_USERNAME = "rotation_test_user";
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        userTokenRepository.deleteAll();
        userRepository.findByUsername(TEST_USERNAME).ifPresent(userRepository::delete);

        Role eeRole = roleRepository.findByName(RoleName.ROLE_END_ENTITY).orElseThrow();
        User user = new User(TEST_USERNAME, "rotation@pki.local", passwordEncoder.encode(TEST_PASSWORD), "Rotation User");
        user.setRoles(Set.of(eeRole));
        userRepository.save(user);
    }

    @Test
    @DisplayName("Refresh token must rotate on use and issue a fresh refresh token")
    void refreshToken_rotatesOnUse() throws Exception {
        // Step 1: Login to get initial tokens
        LoginRequest loginReq = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String refreshToken1 = auth1.getRefreshToken();
        assertThat(refreshToken1).isNotBlank();

        // Step 2: Use RefreshToken1 to refresh
        RefreshTokenRequest refreshReq1 = new RefreshTokenRequest(refreshToken1);
        MvcResult refreshResult1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse auth2 = objectMapper.readValue(refreshResult1.getResponse().getContentAsString(), AuthResponse.class);
        String refreshToken2 = auth2.getRefreshToken();

        // Refresh token must have rotated (new token string)
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);
    }

    @Test
    @DisplayName("Replaying a revoked or already rotated refresh token triggers replay detection and revokes active sessions")
    void refreshToken_detectsReplayAndRevokesTokenFamily() throws Exception {
        // Step 1: Login
        LoginRequest loginReq = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String refreshToken1 = auth1.getRefreshToken();

        // Step 2: Rotate token once (valid transition)
        RefreshTokenRequest refreshReq1 = new RefreshTokenRequest(refreshToken1);
        MvcResult refreshResult1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth2 = objectMapper.readValue(refreshResult1.getResponse().getContentAsString(), AuthResponse.class);
        String refreshToken2 = auth2.getRefreshToken();

        // Step 3: Attacker replays the stale RefreshToken1
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40023"))
                .andExpect(jsonPath("$.detail").value("Compromised or already rotated refresh token presented. All active sessions have been terminated."));

        // Step 4: Verify that the legitimate user's active session (RefreshToken2) is now also invalidated due to replay protection
        RefreshTokenRequest refreshReq2 = new RefreshTokenRequest(refreshToken2);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PKI-40023"));
    }
}
