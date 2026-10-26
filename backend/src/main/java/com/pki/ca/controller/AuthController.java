package com.pki.ca.controller;

import com.pki.ca.dto.request.LoginRequest;
import com.pki.ca.dto.request.RefreshTokenRequest;
import com.pki.ca.dto.request.RegisterRequest;
import com.pki.ca.dto.response.AuthResponse;
import com.pki.ca.dto.response.UserProfileResponse;
import com.pki.ca.dto.response.UserResponse;
import com.pki.ca.security.UserPrincipal;
import com.pki.ca.service.AuthService;
import com.pki.ca.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication, token refresh, session management, and self-registration")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT access & refresh tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange valid refresh token for fresh JWT access token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Retrieve current authenticated user profile, roles, and permissions")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserProfileResponse profile = userService.getCurrentUserProfile(principal);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate active session and refresh tokens")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Operation(summary = "Self-registration for public End Entities (ROLE_END_ENTITY only)")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.registerEndEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
