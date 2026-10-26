package com.pki.ca.service;

import com.pki.ca.dto.request.CreateUserRequest;
import com.pki.ca.dto.request.UpdateUserRequest;
import com.pki.ca.dto.response.PagedResponse;
import com.pki.ca.dto.response.UserProfileResponse;
import com.pki.ca.dto.response.UserResponse;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.ConflictException;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import com.pki.ca.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        List<UserResponse> content = page.getContent().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken.", "PKI-40901");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered.", "PKI-40902");
        }

        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new BadRequestException("Role '" + roleName + "' does not exist."));
            roles.add(role);
        }

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName()
        );
        user.setRoles(roles);

        User savedUser = userRepository.save(user);
        logger.info("AUDIT [USER_CREATED]: Provisioned new user '{}' (id: {}) with roles: {}",
                savedUser.getUsername(), savedUser.getId(), request.getRoles());
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Guard against demoting, disabling, or locking the last active CA Administrator
        if (user.hasRole(RoleName.ROLE_CA_ADMIN)) {
            boolean losingAdminRole = request.getRoles() != null && !request.getRoles().contains(RoleName.ROLE_CA_ADMIN);
            boolean becomingInactive = request.getStatus() != null && request.getStatus() != UserStatus.ACTIVE;
            if (losingAdminRole || becomingInactive) {
                long activeAdminCount = userRepository.countByRoleNameAndStatus(RoleName.ROLE_CA_ADMIN, UserStatus.ACTIVE);
                if (activeAdminCount <= 1) {
                    throw new BadRequestException("Cannot demote, lock, or disable the last remaining active CA Administrator.", "PKI-40030");
                }
            }
        }

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email '" + request.getEmail() + "' is already in use.", "PKI-40902");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
            if (request.getStatus() == UserStatus.ACTIVE) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> newRoles = new HashSet<>();
            for (RoleName roleName : request.getRoles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BadRequestException("Role '" + roleName + "' does not exist."));
                newRoles.add(role);
            }
            user.setRoles(newRoles);
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        User updatedUser = userRepository.save(user);
        logger.info("AUDIT [USER_UPDATED]: User '{}' (id: {}) updated. Status: {}, Roles: {}",
                updatedUser.getUsername(), updatedUser.getId(), updatedUser.getStatus(),
                updatedUser.getRoles().stream().map(r -> r.getName().name()).toList());
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    public UserResponse lockUser(Long id, Integer durationMinutes, String reason) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Guard against locking the last active CA Administrator
        if (user.hasRole(RoleName.ROLE_CA_ADMIN)) {
            long activeAdminCount = userRepository.countByRoleNameAndStatus(RoleName.ROLE_CA_ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new BadRequestException("Cannot administratively lock the last remaining active CA Administrator.", "PKI-40030");
            }
        }

        int duration = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : 30;
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(duration));

        User updatedUser = userRepository.save(user);
        logger.warn("AUDIT [USER_LOCKED]: User '{}' (id: {}) administratively locked for {} minutes. Reason: {}",
                updatedUser.getUsername(), updatedUser.getId(), duration, reason);
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    public UserResponse unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setStatus(UserStatus.ACTIVE);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        User updatedUser = userRepository.save(user);
        logger.info("AUDIT [USER_UNLOCKED]: User '{}' (id: {}) administratively unlocked.",
                updatedUser.getUsername(), updatedUser.getId());
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        Set<RoleName> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        List<String> permissions = evaluatePermissions(roleNames);

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleNames,
                permissions
        );
    }

    private List<String> evaluatePermissions(Set<RoleName> roleNames) {
        Set<String> perms = new LinkedHashSet<>();
        perms.add("auth:login");
        perms.add("auth:refresh");

        for (RoleName role : roleNames) {
            switch (role) {
                case ROLE_CA_ADMIN -> {
                    perms.add("ca:read");
                    perms.add("ca:init");
                    perms.add("ca:import");
                    perms.add("ca:config:write");
                    perms.add("profile:read");
                    perms.add("profile:write");
                    perms.add("enroll:submit");
                    perms.add("enroll:read:all");
                    perms.add("ra:review");
                    perms.add("ra:approve");
                    perms.add("ra:reject");
                    perms.add("cert:issue");
                    perms.add("cert:download:all");
                    perms.add("cert:renew:all");
                    perms.add("cert:revoke:all");
                    perms.add("crl:read");
                    perms.add("crl:generate");
                    perms.add("ocsp:query");
                    perms.add("admin:users:read");
                    perms.add("admin:users:write");
                    perms.add("admin:backup:run");
                    perms.add("admin:system:read");
                }
                case ROLE_RA_OPERATOR -> {
                    perms.add("ca:read");
                    perms.add("profile:read");
                    perms.add("enroll:submit");
                    perms.add("enroll:read:all");
                    perms.add("ra:review");
                    perms.add("ra:approve");
                    perms.add("ra:reject");
                    perms.add("cert:issue");
                    perms.add("cert:download:all");
                    perms.add("cert:renew:all");
                    perms.add("cert:revoke:all");
                    perms.add("crl:read");
                    perms.add("ocsp:query");
                }
                case ROLE_SECURITY_OFFICER -> {
                    perms.add("ca:read");
                    perms.add("profile:read");
                    perms.add("enroll:read:all");
                    perms.add("ra:review");
                    perms.add("cert:download:all");
                    perms.add("cert:revoke:all");
                    perms.add("crl:read");
                    perms.add("crl:generate");
                    perms.add("ocsp:query");
                    perms.add("audit:read");
                    perms.add("audit:export");
                    perms.add("admin:system:read");
                }
                case ROLE_AUDITOR -> {
                    perms.add("ca:read");
                    perms.add("profile:read");
                    perms.add("enroll:read:all");
                    perms.add("cert:download:all");
                    perms.add("crl:read");
                    perms.add("ocsp:query");
                    perms.add("audit:read");
                    perms.add("audit:export");
                    perms.add("admin:system:read");
                }
                case ROLE_END_ENTITY -> {
                    perms.add("profile:read");
                    perms.add("enroll:submit");
                    perms.add("enroll:read:own");
                    perms.add("cert:download:own");
                    perms.add("cert:renew:own");
                    perms.add("cert:revoke:own");
                    perms.add("crl:read");
                    perms.add("ocsp:query");
                }
            }
        }
        return new ArrayList<>(perms);
    }
}
