-- =============================================================================
-- Migration: V4__init_issuance_schema.sql
-- Description: Phase 4 Issuance Schema - CA Authorities, Certificates, Issuance Attempts
-- Standards: RFC 5280, RFC 2986, NIST SP 800-57, PKCS#11, MySQL 8.x / InnoDB
-- =============================================================================

-- Table: ca_authorities
CREATE TABLE IF NOT EXISTS ca_authorities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_name VARCHAR(64) NOT NULL,
    subject_dn VARCHAR(255) NOT NULL,
    issuer_dn VARCHAR(255) NOT NULL,
    serial_number VARCHAR(64) NOT NULL,
    hsm_key_alias VARCHAR(64) NOT NULL,
    hsm_slot_id VARCHAR(32) NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    certificate_pem TEXT NOT NULL,
    chain_pem TEXT NOT NULL,
    not_before DATETIME NOT NULL,
    not_after DATETIME NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    crl_validity_hours INT NOT NULL DEFAULT 72,
    created_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ca_name UNIQUE (ca_name),
    CONSTRAINT uk_ca_serial UNIQUE (serial_number),
    CONSTRAINT uk_ca_hsm_key_alias UNIQUE (hsm_key_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ca_name ON ca_authorities (ca_name);
CREATE INDEX idx_ca_status ON ca_authorities (status);
CREATE INDEX idx_ca_serial ON ca_authorities (serial_number);

-- Table: certificates
CREATE TABLE IF NOT EXISTS certificates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    serial_number VARCHAR(64) NOT NULL,
    subject_dn VARCHAR(255) NOT NULL,
    issuer_dn VARCHAR(255) NOT NULL,
    san_entries TEXT NULL,
    not_before DATETIME NOT NULL,
    not_after DATETIME NOT NULL,
    certificate_pem TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    public_key_fingerprint VARCHAR(64) NOT NULL,
    fingerprint_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cert_request UNIQUE (request_id),
    CONSTRAINT uk_cert_serial UNIQUE (serial_number),
    CONSTRAINT uk_cert_fingerprint UNIQUE (fingerprint_sha256),
    CONSTRAINT fk_cert_ca FOREIGN KEY (ca_id) REFERENCES ca_authorities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cert_request FOREIGN KEY (request_id) REFERENCES enrollment_requests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cert_serial ON certificates (serial_number);
CREATE INDEX idx_cert_status ON certificates (status);
CREATE INDEX idx_cert_ca ON certificates (ca_id);
CREATE INDEX idx_cert_not_after ON certificates (not_after);
CREATE INDEX idx_cert_created_at ON certificates (created_at);

-- Table: issuance_attempts
CREATE TABLE IF NOT EXISTS issuance_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    ca_id BIGINT NOT NULL,
    allocated_serial_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(255) NULL,
    signed_certificate_pem TEXT NULL,
    initiated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_attempt_serial UNIQUE (allocated_serial_number),
    CONSTRAINT fk_attempt_request FOREIGN KEY (request_id) REFERENCES enrollment_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempt_ca FOREIGN KEY (ca_id) REFERENCES ca_authorities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempt_user FOREIGN KEY (initiated_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_attempt_request ON issuance_attempts (request_id);
CREATE INDEX idx_attempt_status ON issuance_attempts (status);
