# Requirements Traceability Matrix (RTM)
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-RTM-013
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Traceability Baseline
- **Version:** 1.0.0
- **Classification:** Engineering Governance

---

## 1. Traceability Architecture

The Requirements Traceability Matrix (RTM) establishes bidirectional traceability across the complete engineering lifecycle: from high-level stakeholder mandates and cryptographic standards through architectural components, API contracts, database entities, acceptance criteria, test cases, and scheduled development phases.

### Identifier Taxonomy
- `FR-xxx`: Functional Requirements
- `CR-xxx`: Cryptographic Requirements
- `SR-xxx`: Security Requirements
- `HR-xxx`: Hardware Security Module Requirements
- `VR-xxx`: Validation & Revocation Requirements
- `NFR-xxx`: Non-Functional Requirements
- `ALR-xxx`: Audit Logging Requirements
- `BDR-xxx`: Backup & Disaster Recovery Requirements
- `AC-xxx`: Acceptance Criteria
- `TEST-xxx`: Test Case Identifiers

---

## 2. Comprehensive Traceability Matrix

| Req ID | Requirement Statement & Category | Source Reference | Design Component / Architecture Layer | Primary API / Workflow | Acceptance Criterion | Test Case ID | Priority & Scope | Target Phase |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FR-001** | Subordinate CA initialization in HSM and CSR generation | RFC 5280, SRS §2.1 | `CaService`, `Pkcs11HsmAdapter` | `POST /api/v1/ca/init`, WF-10 | `AC-001` | `TEST-INT-001` | **Must / MVP** | Phase 6 |
| **FR-002** | CA operational state and validity monitoring | SRS §2.1 | `CaService`, `ca_authorities` | `GET /api/v1/ca`, WF-10 | `AC-001` | `TEST-UNIT-004` | **Must / MVP** | Phase 6 |
| **FR-004** | Trust chain download (Issuing CA + Root) in PEM/DER | RFC 5280, SRS §2.1 | `CaService`, `ca_authorities` | `GET /api/v1/ca/chain`, WF-5 | `AC-012` | `TEST-E2E-001` | **Must / MVP** | Phase 4 |
| **FR-010** | Accept PEM/DER PKCS#10 CSR submissions | RFC 2986, SRS §2.2 | `EnrollmentService`, `BouncyCastle` | `POST /api/v1/enrollment/requests`, WF-2 | `AC-003` | `TEST-UNIT-001` | **Must / MVP** | Phase 3 |
| **FR-011** | CSR proof-of-possession signature verification | RFC 2986, SRS §2.2 | `CsrParserService` | `POST /api/v1/enrollment/requests`, WF-3 | `AC-003` | `TEST-SEC-001` | **Must / MVP** | Phase 3 |
| **FR-012** | Parse and validate Subject DN and SANs (DNS, IP, Email)| RFC 5280, SRS §2.2 | `CsrParserService`, `san_entries` | `POST /api/v1/enrollment/requests`, WF-3 | `AC-005` | `TEST-UNIT-001` | **Must / MVP** | Phase 3 |
| **FR-013** | Enforce public key policy (reject RSA < 2048, weak curves) | NIST SP 800-57, SRS §2.2 | `CertificateProfileService` | `POST /api/v1/enrollment/requests`, WF-3 | `AC-004` | `TEST-SEC-001` | **Must / MVP** | Phase 3 |
| **FR-020** | RA authenticated pending work queue | SRS §2.3 | `RaApprovalService`, `enrollment_requests` | `GET /api/v1/enrollment/requests`, WF-4 | `AC-006` | `TEST-E2E-001` | **Must / MVP** | Phase 3 |
| **FR-021** | Enforce human RA approval before signing | SRS §2.3 | `RaApprovalService`, `request_approvals` | `POST /api/v1/ra/requests/{id}/approve`, WF-4 | `AC-006` | `TEST-INT-002` | **Must / MVP** | Phase 3 |
| **FR-022** | Self-approval prohibition (Separation of Duties) | SRS §2.3, Security Policy | `RaApprovalService`, SoD Policy | `POST /api/v1/ra/requests/{id}/approve`, WF-4 | `AC-007` | `TEST-SEC-002` | **Must / MVP** | Phase 3 |
| **FR-023** | Request rejection with mandatory justification | SRS §2.3 | `RaApprovalService` | `POST /api/v1/ra/requests/{id}/reject`, WF-4 | `AC-008` | `TEST-UNIT-006` | **Must / MVP** | Phase 3 |
| **FR-030** | Cryptographic certificate signing upon RA approval | RFC 5280, SRS §2.4 | `CaIssuanceService`, `Pkcs11HsmAdapter` | `POST /api/v1/ca/requests/{id}/issue`, WF-5 | `AC-009` | `TEST-INT-002` | **Must / MVP** | Phase 4 |
| **FR-031** | Cryptographic serial numbers with >= 64 bits entropy | RFC 5280 §4.1.2.2 | `SerialNumberGenerator`, `SecureRandom` | `CaIssuanceService`, WF-5 | `AC-010` | `TEST-UNIT-003` | **Must / MVP** | Phase 4 |
| **FR-032** | Multi-format certificate download (PEM, DER, PKCS#7) | RFC 5280, SRS §2.4 | `CertificateService` | `GET /api/v1/certificates/{s}/download`, WF-5 | `AC-012` | `TEST-E2E-001` | **Must / MVP** | Phase 4 |
| **FR-033** | Certificate renewal workflow with prior cert link | SRS §2.4 | `RenewalService`, `enrollment_requests` | `POST /api/v1/certificates/{s}/renew`, WF-6 | `AC-014` | `TEST-E2E-001` | **Must / MVP** | Phase 5 |
| **FR-034** | Certificate revocation with RFC 5280 reason codes | RFC 5280 §5.3.1 | `RevocationService`, `revocation_records` | `POST /api/v1/certificates/{s}/revoke`, WF-7 | `AC-015` | `TEST-E2E-002` | **Must / MVP** | Phase 5 |
| **FR-036** | Indexed certificate inventory and search interface | SRS §2.4 | `CertificateRepository`, MySQL B-Tree | `GET /api/v1/certificates`, WF-10 | `AC-012` | `TEST-INT-006` | **Must / MVP** | Phase 5 |
| **FR-040** | Configurable Certificate Profile templates | RFC 5280, SRS §2.5 | `CertificateProfileService`, `profiles` | `GET/POST /api/v1/profiles`, WF-10 | `AC-011` | `TEST-UNIT-002` | **Must / MVP** | Phase 4 |
| **FR-041** | Predefined profiles (TLS_SERVER, TLS_CLIENT, CODE, EMAIL)| RFC 5280, SRS §2.5 | Flyway Seed Migrations, `profiles` | Database Seeding | `AC-011` | `TEST-UNIT-002` | **Must / MVP** | Phase 4 |
| **FR-050** | RFC 5280 path and chain validation endpoint | RFC 5280 §6 | `ValidationService`, `BouncyCastle` | `POST /api/v1/validation/verify-cert`, WF-8 | `AC-019` | `TEST-INT-007` | **Must / MVP** | Phase 7 |
| **FR-060** | Generate RFC 5280 X.509 v2 CRLs signed in HSM | RFC 5280 §5 | `CrlService`, `Pkcs11HsmAdapter` | `GET /api/v1/crl/latest`, WF-7 | `AC-017` | `TEST-INT-003` | **Must / MVP** | Phase 7 |
| **FR-061** | Scheduled automated CRL publication before nextUpdate | RFC 5280, SRS §2.7 | `CrlScheduledTask`, `SYSTEM_SCHEDULER` | Background Cron, WF-7 | `AC-018` | `TEST-INT-003` | **Must / MVP** | Phase 7 |
| **FR-062** | On-demand immediate out-of-band CRL regeneration | SRS §2.7 | `CrlService` | `POST /api/v1/crl/generate`, WF-7 | `AC-017` | `TEST-SEC-008` | **Must / MVP** | Phase 7 |
| **FR-063** | HTTP CRL file distribution point (.crl and .pem) | RFC 5280 §4.2.1.13 | Reverse Proxy / Static File Store | `GET /crl/issuing-ca.crl` | `AC-017` | `TEST-INT-003` | **Must / MVP** | Phase 7 |
| **FR-065** | RFC 6960 binary OCSP responder | RFC 6960 | `OcspResponderService`, ASN.1 Codec | `POST /api/v1/ocsp`, WF-9 | `AC-020` | `TEST-INT-004` | **Must / MVP** | Phase 7 |
| **FR-070** | SunPKCS11 JCA provider integration | PKCS#11 v2.40 | `Pkcs11HsmAdapter`, `sun.security.pkcs11` | Cryptographic Subsystem | `AC-001` | `TEST-INT-001` | **Must / MVP** | Phase 6 |
| **FR-071** | On-token non-exportable CA key generation | FIPS 140-2/3, SRS §2.9 | `Pkcs11HsmAdapter`, CKA Attributes | `POST /api/v1/ca/init`, WF-10 | `AC-001` | `TEST-SEC-003` | **Must / MVP** | Phase 6 |
| **FR-072** | Zero CA private key in JVM heap, database, or logs | Security Policy, SRS §2.9 | Cryptographic Architecture | System-wide invariant | `AC-024` | `TEST-SEC-003` | **Must / MVP** | Phase 6 |
| **FR-074** | SoftHSM2 development/test parity | SRS §2.9, Tech Brief | Docker Compose SoftHSM2 Fixture | Dev/CI Runtime | `AC-001` | `TEST-INT-001` | **Must / MVP** | Phase 6 |
| **FR-080** | User and role administration CRUD | SRS §2.10 | `UserService`, `RoleService` | `GET/POST /api/v1/admin/users`, WF-10 | `AC-026` | `TEST-INT-005` | **Must / MVP** | Phase 8 |
| **FR-100** | Append-only security audit event repository | NIST SP 800-57, SRS §2.10 | `AuditService`, `audit_events` | System Event Bus, WF-10 | `AC-021` | `TEST-SEC-004` | **Must / MVP** | Phase 8 |
| **FR-101** | Audit trail HMAC-SHA256 hash chaining | SRS §2.10 | `AuditService` Chained Hasher | Database Persistence, WF-10 | `AC-021` | `TEST-SEC-004` | **Must / MVP** | Phase 8 |
| **FR-102** | Auditor role query, verification, and CSV export | SRS §2.10 | `AuditController` | `GET /api/v1/audit/**`, WF-10 | `AC-021` | `TEST-E2E-003` | **Must / MVP** | Phase 8 |
| **FR-110** | Encrypted database backup creation and restore | SRS §2.11 | `BackupService`, `backup_records` | `POST /api/v1/admin/backups/create`, WF-12 | `AC-025` | `TEST-DR-001` | **Must / MVP** | Phase 8 |
| **CR-002** | RSA key support (2048, 3072, 4096-bit) | NIST SP 800-57 | `CsrParserService`, `CaIssuanceService` | CSR & Issuance | `AC-004` | `TEST-UNIT-001` | **Must / MVP** | Phase 3 |
| **CR-003** | ECDSA curve support (secp256r1, secp384r1) | FIPS 186-5 | `CsrParserService`, `CaIssuanceService` | CSR & Issuance | `AC-004` | `TEST-UNIT-001` | **Must / MVP** | Phase 3 |
| **CR-006** | BasicConstraints cA=false for end entities | RFC 5280 §4.2.1.9 | `CertificateProfileService` | Issuance Pipeline | `AC-011` | `TEST-UNIT-002` | **Must / MVP** | Phase 4 |
| **CR-013** | Subject Alternative Name (SAN) support | RFC 5280 §4.2.1.6 | `CertificateProfileService` | Issuance Pipeline | `AC-005` | `TEST-UNIT-001` | **Must / MVP** | Phase 3 |
| **SR-001** | JWT access tokens (15m) + revocable refresh tokens (8h) | OWASP ASVS | `JwtAuthenticationFilter`, `user_tokens` | `POST /api/v1/auth/login`, WF-1 | `AC-027` | `TEST-INT-005` | **Must / MVP** | Phase 2 |
| **SR-002** | BCrypt password hashing (work factor >= 12) | OWASP ASVS | `PasswordEncoder`, `users` | User Creation & Login | `AC-028` | `TEST-UNIT-005` | **Must / MVP** | Phase 2 |
| **SR-004** | Account lockout after 5 failed login attempts | OWASP ASVS | `AuthenticationService` | `POST /api/v1/auth/login`, WF-1 | `AC-023` | `TEST-SEC-005` | **Must / MVP** | Phase 2 |
| **SR-007** | Resource ownership isolation for End Entities | Security Policy | JPA Repository Tenant Predicates | `GET /api/v1/certificates/my`, WF-5 | `AC-013` | `TEST-SEC-007` | **Must / MVP** | Phase 5 |
| **SR-014** | Token-bucket rate limiting on authentication and API | Security Policy | `RateLimitingFilter` | HTTP Transport Layer | `AC-029` | `TEST-SEC-006` | **Must / MVP** | Phase 2 |
| **VR-001** | RFC 5280 path validation | RFC 5280 §6 | `ValidationService` | `POST /api/v1/validation/verify-cert`, WF-8 | `AC-019` | `TEST-INT-007` | **Must / MVP** | Phase 7 |
| **NFR-PERF-01** | Issuance throughput >= 10 certs/sec (SoftHSM) | SRS §7.1 | Full Pipeline (HSM + JPA) | `POST /api/v1/ca/requests/{id}/issue` | `AC-009` | `TEST-PERF-01` | **Must / MVP** | Phase 9 |
| **NFR-PERF-03** | OCSP response latency <= 25 ms (p95) | SRS §7.1 | `OcspResponderService` | `POST /api/v1/ocsp` | `AC-020` | `TEST-PERF-03` | **Must / MVP** | Phase 9 |
| **BDR-002** | Recovery Point Objective (RPO) <= 1 hour | SRS §11 | MySQL Binary Logging & Hourly Dumps | Disaster Recovery Plan | `AC-025` | `TEST-DR-001` | **Must / MVP** | Phase 9 |
| **BDR-003** | Recovery Time Objective (RTO) <= 4 hours | SRS §11 | Containerized Restore Automation | Disaster Recovery Plan | `AC-025` | `TEST-DR-001` | **Must / MVP** | Phase 9 |
| **IR-001** | LDAP / Active Directory synchronization | Enterprise IR | `LdapAuthenticationProvider` | Authentication Subsystem | Deferred | Deferred | **Could / Post-MVP**| Post-MVP |
| **IR-004** | Time Stamping Authority (RFC 3161) | RFC 3161 | `TsaService`, Timestamp Token Signer | `POST /api/v1/tsa` | Deferred | Deferred | **Could / Post-MVP**| Post-MVP |
