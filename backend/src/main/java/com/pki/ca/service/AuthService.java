package com.pki.ca.service;

import com.pki.ca.dto.request.LoginRequest;
import com.pki.ca.dto.request.RefreshTokenRequest;
import com.pki.ca.dto.request.RegisterRequest;
import com.pki.ca.dto.response.AuthResponse;
import com.pki.ca.dto.response.UserResponse;
import com.pki.ca.entity.Role;
import com.pki.ca.entity.User;
import com.pki.ca.entity.UserToken;
import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import com.pki.ca.exception.AccountLockedException;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.ConflictException;
import com.pki.ca.repository.RoleRepository;
import com.pki.ca.repository.UserRepository;
import com.pki.ca.repository.UserTokenRepository;
import com.pki.ca.security.JwtTokenProvider;
import com.pki.ca.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserTokenRepository userTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.jwt.refresh-expiration-ms:28800000}")
    private long refreshExpirationMs;

    @Value("${app.security.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout.duration-minutes:30}")
    private int lockoutDurationMinutes;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserTokenRepository userTokenRepository,
            JwtTokenProvider tokenProvider,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userTokenRepository = userTokenRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Check if account is currently locked
            if (user.getStatus() == UserStatus.LOCKED) {
                if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
                    logger.warn("Login attempt on locked account: {}", user.getUsername());
                    throw new AccountLockedException("Account is locked until " + user.getLockedUntil());
                } else {
                    // Lock expired; reset state
                    user.setStatus(UserStatus.ACTIVE);
                    user.setFailedAttempts(0);
                    user.setLockedUntil(null);
                    userRepository.save(user);
                }
            }
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            // Reset failed attempts on success
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getFailedAttempts() > 0) {
                    user.setFailedAttempts(0);
                    user.setLockedUntil(null);
                    userRepository.save(user);
                }
            }

            String accessToken = tokenProvider.generateAccessToken(principal);
            long expiresInSeconds = tokenProvider.getExpirationMs() / 1000;

            // Generate opaque refresh token and store SHA-256 hash
            String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
            String tokenHash = hashToken(rawRefreshToken);
            LocalDateTime expiresAt = LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000);

            User userEntity = userRepository.findById(principal.getId()).orElseThrow();
            UserToken userToken = new UserToken(userEntity, tokenHash, expiresAt);
            userTokenRepository.save(userToken);

            UserResponse userResponse = UserResponse.fromEntity(userEntity);
            logger.info("AUDIT [AUTH_SUCCESS]: User '{}' (id: {}) successfully logged in. Issued access & refresh tokens.", principal.getUsername(), principal.getId());
            return new AuthResponse(accessToken, expiresInSeconds, rawRefreshToken, userResponse);

        } catch (BadCredentialsException ex) {
            // Track consecutive failure and enforce lockout
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);

                if (attempts >= maxFailedAttempts) {
                    user.setStatus(UserStatus.LOCKED);
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
                    logger.warn("AUDIT [ACCOUNT_LOCKOUT]: Account '{}' locked for {} minutes due to {} consecutive failed attempts",
                            user.getUsername(), lockoutDurationMinutes, attempts);
                } else {
                    logger.warn("AUDIT [AUTH_FAILURE]: Bad credentials for user '{}'. Failed attempt count: {}", user.getUsername(), attempts);
                }
                userRepository.save(user);
            } else {
                logger.warn("AUDIT [AUTH_FAILURE]: Authentication failed for non-existent subject");
            }
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional(noRollbackFor = {BadRequestException.class})
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        UserToken userToken = userTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or unverified refresh token.", "PKI-40020"));

        User user = userToken.getUser();

        // Token Replay Detection: If an already-revoked token is used, assume token theft!
        if (userToken.isRevoked()) {
            logger.error("AUDIT [TOKEN_REPLAY_DETECTED]: Revoked refresh token reused for user '{}'. Revoking all active tokens for account!",
                    user.getUsername());
            userTokenRepository.revokeAllTokensForUser(user);
            throw new BadRequestException("Compromised or already rotated refresh token presented. All active sessions have been terminated.", "PKI-40023");
        }

        if (userToken.isExpired()) {
            logger.warn("AUDIT [TOKEN_EXPIRED]: Expired refresh token presented for user '{}'", user.getUsername());
            throw new BadRequestException("Refresh token has expired. Please re-authenticate.", "PKI-40021");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            logger.warn("AUDIT [REFRESH_DENIED]: User account '{}' is not ACTIVE (status: {})", user.getUsername(), user.getStatus());
            throw new BadRequestException("User account is not in an active state.", "PKI-40022");
        }

        // 1. Invalidate used refresh token (Rotation)
        userToken.setRevoked(true);
        userTokenRepository.save(userToken);

        // 2. Issue new rotated refresh token
        String newRawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawRefreshToken);
        LocalDateTime newExpiresAt = LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000);
        UserToken newRotatedToken = new UserToken(user, newTokenHash, newExpiresAt);
        userTokenRepository.save(newRotatedToken);

        // 3. Issue fresh access token
        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(principal);
        long expiresInSeconds = tokenProvider.getExpirationMs() / 1000;

        logger.info("AUDIT [TOKEN_REFRESH_SUCCESS]: Successfully rotated refresh token for user '{}'", user.getUsername());
        return new AuthResponse(newAccessToken, expiresInSeconds, newRawRefreshToken, UserResponse.fromEntity(user));
    }

    @Transactional
    public void logout(UserPrincipal principal) {
        if (principal != null) {
            User user = userRepository.findById(principal.getId()).orElse(null);
            if (user != null) {
                userTokenRepository.revokeAllTokensForUser(user);
                logger.info("AUDIT [LOGOUT]: User '{}' logged out. All refresh tokens revoked.", user.getUsername());
            }
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public UserResponse registerEndEntity(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken.", "PKI-40901");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered.", "PKI-40902");
        }

        Role endEntityRole = roleRepository.findByName(RoleName.ROLE_END_ENTITY)
                .orElseThrow(() -> new BadRequestException("Default role ROLE_END_ENTITY is not configured."));

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName()
        );
        // Enforce strictly ROLE_END_ENTITY; public self-registration cannot elevate roles
        user.setRoles(Set.of(endEntityRole));

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
