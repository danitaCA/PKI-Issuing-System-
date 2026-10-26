package com.pki.ca.controller;

import com.pki.ca.dto.request.CreateUserRequest;
import com.pki.ca.dto.request.LockUserRequest;
import com.pki.ca.dto.request.UpdateUserRequest;
import com.pki.ca.dto.response.PagedResponse;
import com.pki.ca.dto.response.UserResponse;
import com.pki.ca.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('CA_ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "User Administration", description = "CA Administrator endpoints for account and role provisioning")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all registered users (Paginated)")
    public ResponseEntity<PagedResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = (sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortField));
        PagedResponse<UserResponse> users = userService.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @Operation(summary = "Provision a new user account with assigned RBAC roles")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user attributes, status, or assigned roles")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "Administratively lock a user account")
    public ResponseEntity<UserResponse> lockUser(
            @PathVariable Long id,
            @RequestBody(required = false) LockUserRequest request) {
        Integer duration = (request != null) ? request.getDurationMinutes() : 30;
        String reason = (request != null) ? request.getReason() : "Administrative Lock";
        UserResponse response = userService.lockUser(id, duration, reason);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Administratively unlock a user account")
    public ResponseEntity<UserResponse> unlockUser(@PathVariable Long id) {
        UserResponse response = userService.unlockUser(id);
        return ResponseEntity.ok(response);
    }
}
