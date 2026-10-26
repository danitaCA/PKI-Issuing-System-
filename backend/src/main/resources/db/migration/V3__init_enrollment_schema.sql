-- =============================================================================
-- Migration: V3__init_enrollment_schema.sql
-- Description: Phase 3 RA Workflow Schema - Profiles, Enrollment Requests, Reviews
-- Standards: RFC 2986 (PKCS#10), RFC 5280, NIST SP 800-57, MySQL 8.x / InnoDB
-- =============================================================================

-- Table: certificate_profiles
CREATE TABLE IF NOT EXISTS certificate_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_code VARCHAR(32) NOT NULL,
    profile_name VARCHAR(64) NOT NULL,
    profile_type VARCHAR(32) NOT NULL,
    default_validity_days INT NOT NULL,
    max_validity_days INT NOT NULL,
    allowed_key_types TEXT NOT NULL,
    key_usages TEXT NOT NULL,
    extended_key_usages TEXT NOT NULL,
    require_san BOOLEAN NOT NULL DEFAULT TRUE,
    allow_wildcards BOOLEAN NOT NULL DEFAULT FALSE,
    requires_dual_approval BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_profiles_code UNIQUE (profile_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_profiles_code ON certificate_profiles (profile_code);
CREATE INDEX idx_profiles_active ON certificate_profiles (is_active);

-- Seed Initial Baseline Profiles
INSERT INTO certificate_profiles (
    profile_code, profile_name, profile_type, default_validity_days, max_validity_days,
    allowed_key_types, key_usages, extended_key_usages, require_san, allow_wildcards,
    requires_dual_approval, is_active, created_at
) VALUES
    ('TLS_SERVER', 'Internal TLS Server Certificate', 'SERVER_AUTH', 365, 730,
     '["RSA-2048","RSA-3072","RSA-4096","ECDSA-P256","ECDSA-P384"]',
     '["digitalSignature","keyEncipherment"]', '["1.3.6.1.5.5.7.3.1"]',
     TRUE, FALSE, FALSE, TRUE, NOW()),
    ('TLS_CLIENT', 'Internal TLS Client Certificate', 'CLIENT_AUTH', 365, 730,
     '["RSA-2048","RSA-3072","ECDSA-P256"]',
     '["digitalSignature","keyAgreement"]', '["1.3.6.1.5.5.7.3.2"]',
     FALSE, FALSE, FALSE, TRUE, NOW()),
    ('CODE_SIGNING', 'Enterprise Code Signing Certificate', 'CODE_SIGNING', 365, 365,
     '["RSA-3072","RSA-4096","ECDSA-P384"]',
     '["digitalSignature"]', '["1.3.6.1.5.5.7.3.3"]',
     FALSE, FALSE, TRUE, TRUE, NOW()),
    ('DEVICE_IDENTITY', 'Secure Device Identity Certificate (802.1X)', 'CLIENT_AUTH', 730, 1095,
     '["RSA-2048","ECDSA-P256"]',
     '["digitalSignature","keyEncipherment"]', '["1.3.6.1.5.5.7.3.2"]',
     FALSE, FALSE, FALSE, TRUE, NOW())
ON DUPLICATE KEY UPDATE profile_name = VALUES(profile_name);

-- Table: enrollment_requests
CREATE TABLE IF NOT EXISTS enrollment_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    subject_dn VARCHAR(255) NOT NULL,
    san_entries TEXT NULL,
    csr_pem TEXT NOT NULL,
    public_key_algorithm VARCHAR(32) NOT NULL,
    key_length INT NOT NULL,
    public_key_fingerprint VARCHAR(64) NOT NULL,
    validity_days INT NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    rejection_comment TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_requests_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_requests_profile FOREIGN KEY (profile_id) REFERENCES certificate_profiles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_requests_requester ON enrollment_requests (requester_id);
CREATE INDEX idx_requests_status ON enrollment_requests (status);
CREATE INDEX idx_requests_fingerprint ON enrollment_requests (public_key_fingerprint);
CREATE INDEX idx_requests_created ON enrollment_requests (created_at);

-- Table: request_approvals
CREATE TABLE IF NOT EXISTS request_approvals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    review_stage VARCHAR(32) NOT NULL,
    reviewer_comment TEXT NULL,
    client_ip VARCHAR(45) NOT NULL,
    reviewed_at DATETIME NOT NULL,
    CONSTRAINT fk_approvals_request FOREIGN KEY (request_id) REFERENCES enrollment_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_approvals_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_approvals_request ON request_approvals (request_id);
CREATE INDEX idx_approvals_reviewer ON request_approvals (reviewer_id);
