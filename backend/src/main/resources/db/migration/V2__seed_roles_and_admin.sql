-- =============================================================================
-- Migration: V2__seed_roles_and_admin.sql
-- Description: Seed Primary 5 RBAC Roles & System Baseline Settings
-- =============================================================================

INSERT INTO roles (name, description) VALUES
    ('ROLE_CA_ADMIN', 'CA Administrator managing CA configuration, profiles, system settings, and user provisioning'),
    ('ROLE_RA_OPERATOR', 'Registration Authority Operator reviewing identity and CSR requests for approval or rejection'),
    ('ROLE_SECURITY_OFFICER', 'Security Officer monitoring security events, incident responses, and high-assurance approvals'),
    ('ROLE_AUDITOR', 'Auditor with strictly read-only access to audit logs, issuance history, and compliance reports'),
    ('ROLE_END_ENTITY', 'End Entity user or device submitting enrollment requests, managing renewal, and downloading certificates')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Seed Initial System Configurations
INSERT INTO system_configurations (config_key, config_value, data_type, is_encrypted, description, updated_at) VALUES
    ('auth.max_failed_attempts', '5', 'INTEGER', FALSE, 'Maximum consecutive failed login attempts before account lockout', NOW()),
    ('auth.lockout_duration_minutes', '30', 'INTEGER', FALSE, 'Duration in minutes for account lockout following threshold breach', NOW()),
    ('auth.jwt_expiration_minutes', '15', 'INTEGER', FALSE, 'JWT access token validity period in minutes', NOW()),
    ('auth.refresh_token_expiration_hours', '8', 'INTEGER', FALSE, 'Refresh token validity period in hours', NOW()),
    ('system.environment_name', 'DEVELOPMENT', 'STRING', FALSE, 'Operating environment identifier', NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
