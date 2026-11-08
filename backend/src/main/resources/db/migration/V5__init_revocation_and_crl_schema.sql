-- =============================================================================
-- Migration: V5__init_revocation_and_crl_schema.sql
-- Description: Phase 5 Schema - Revocation Records, CRL Storage, Monotonic CRL Sequences,
--              Certificate Lifecycle & Renewal Linkages, Audit Event Log
-- Standards: RFC 5280, RFC 6960, NIST SP 800-57, MySQL 8.x / InnoDB & H2 Compatibility
-- =============================================================================

-- 1. Extend certificates table with lifecycle, revocation, and renewal columns
ALTER TABLE certificates ADD COLUMN revoked_at DATETIME NULL;
ALTER TABLE certificates ADD COLUMN revocation_reason VARCHAR(50) NULL;
ALTER TABLE certificates ADD COLUMN revocation_invalidity_date DATETIME NULL;
ALTER TABLE certificates ADD COLUMN renewed_from_serial VARCHAR(64) NULL;
ALTER TABLE certificates ADD COLUMN renewed_to_serial VARCHAR(64) NULL;

CREATE INDEX idx_cert_revoked_at ON certificates (revoked_at);
CREATE INDEX idx_cert_renewed_from ON certificates (renewed_from_serial);

-- 2. Extend enrollment_requests with renewal linkage
ALTER TABLE enrollment_requests ADD COLUMN renewed_certificate_serial VARCHAR(64) NULL;

-- 3. Table: revocation_records (Authoritative log of all revocation events)
CREATE TABLE IF NOT EXISTS revocation_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    certificate_id BIGINT NOT NULL,
    serial_number VARCHAR(64) NOT NULL,
    ca_id BIGINT NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    crl_reason_code INT NOT NULL,
    revocation_date DATETIME NOT NULL,
    invalidity_date DATETIME NULL,
    initiated_by BIGINT NOT NULL,
    comments TEXT NULL,
    client_ip VARCHAR(45) NULL,
    created_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_revocation_serial UNIQUE (serial_number),
    CONSTRAINT fk_revocation_cert FOREIGN KEY (certificate_id) REFERENCES certificates (id) ON DELETE RESTRICT,
    CONSTRAINT fk_revocation_ca FOREIGN KEY (ca_id) REFERENCES ca_authorities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_revocation_user FOREIGN KEY (initiated_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_revocation_ca ON revocation_records (ca_id);
CREATE INDEX idx_revocation_date ON revocation_records (revocation_date);
CREATE INDEX idx_revocation_reason ON revocation_records (reason_code);

-- 4. Table: crl_records (Durable storage of signed X.509 v2 CRL artifacts)
CREATE TABLE IF NOT EXISTS crl_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_id BIGINT NOT NULL,
    crl_number BIGINT NOT NULL,
    this_update DATETIME NOT NULL,
    next_update DATETIME NOT NULL,
    revoked_entries_count INT NOT NULL DEFAULT 0,
    crl_der_bytes LONGBLOB NOT NULL,
    crl_pem TEXT NOT NULL,
    signature_algorithm VARCHAR(32) NOT NULL,
    crl_scope VARCHAR(32) NOT NULL DEFAULT 'FULL',
    generated_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_crl_ca_number UNIQUE (ca_id, crl_number),
    CONSTRAINT fk_crl_ca FOREIGN KEY (ca_id) REFERENCES ca_authorities (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_crl_ca_update ON crl_records (ca_id, this_update);
CREATE INDEX idx_crl_number ON crl_records (crl_number);

-- 5. Table: crl_sequences (Atomic monotonic sequence counter & concurrency lock per CA)
CREATE TABLE IF NOT EXISTS crl_sequences (
    ca_id BIGINT PRIMARY KEY,
    last_crl_number BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_crl_seq_ca FOREIGN KEY (ca_id) REFERENCES ca_authorities (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table: audit_events (Persistent security and lifecycle audit log)
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    principal_username VARCHAR(100) NOT NULL,
    principal_role VARCHAR(64) NULL,
    client_ip VARCHAR(45) NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL,
    prev_hash VARCHAR(64) NULL,
    record_hash VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_created ON audit_events (created_at);
CREATE INDEX idx_audit_type ON audit_events (event_type);
