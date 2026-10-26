# Ten-Phase Development Roadmap & Implementation Plan
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-ROAD-012
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Execution Baseline
- **Version:** 1.0.0
- **Classification:** Project Management & Technical Planning

---

## 1. Roadmap Strategy & Critical Path

The development plan structures the implementation across **ten distinct, disciplined phases**. It respects the critical engineering path: core data persistence, identity governance, and cryptographic key protection must be established and verified before building advanced administration, automated revocation, or external integrations.

```mermaid
gantt
    title PKI Issuing CA System 10-Phase Roadmap
    dateFormat  YYYY-MM-DD
    section Baseline
    Phase 1: Requirements, Arch & Design (Current)  :done, p1, 2026-10-20, 2026-10-21
    section Foundation
    Phase 2: Project Setup, DB & RBAC Auth         :active, p2, after p1, 5d
    section Core PKI Pipeline
    Phase 3: RA Registration & CSR Processing      :p3, after p2, 5d
    Phase 4: CA Issuance & Certificate Profiles    :p4, after p3, 5d
    Phase 5: Renewal, Revocation & Repository      :p5, after p4, 5d
    section Cryptographic Hardening
    Phase 6: SoftHSM & Cryptographic Protection   :p6, after p5, 6d
    section Validation Services
    Phase 7: Validation, CRL & OCSP Services       :p7, after p6, 6d
    section Governance & Operations
    Phase 8: Administration, Audit & Notifications :p8, after p7, 5d
    section Hardening & Delivery
    Phase 9: Integration, Security & DR Testing    :p9, after p8, 6d
    Phase 10: Deployment, Docs & Demonstration     :p10, after p9, 4d
```

### Critical Path Dependency Chain
`Phase 1 (Design)` -> `Phase 2 (DB & Auth)` -> `Phase 3 (CSR Validation)` -> `Phase 4 (Profiles & Software Sign)` -> `Phase 5 (Lifecycle & Revocation)` -> `Phase 6 (SoftHSM PKCS#11 Integration)` -> `Phase 7 (CRL & OCSP Validation)` -> `Phase 8 (Audit & Admin)` -> `Phase 9 (Hardening & DR)` -> `Phase 10 (Demo)`.

---

## 2. Phase-by-Phase Execution Specifications

### Phase 1: Requirements, Architecture, and Design [CURRENT PHASE]
- **Status:** **Completed / Design Baseline Established**.
- **Prerequisites:** Workspace inspection, customer technology brief.
- **Scope:** Complete architectural blueprint, SRS, threat model, normalized data model, API contracts, RBAC specification, workflows, acceptance criteria, and roadmap.
- **Deliverables:**
  - 14 markdown documents in `docs/phase-1/`.
  - Mermaid architecture, deployment, ERD, sequence, and state machine diagrams in `docs/phase-1/diagrams/`.
  - Comprehensive `README.md` at workspace root.
- **Exit Criteria:** Architecture and data model validated against all requirements; zero application code generated in Phase 1.
- **Scope Marker:** **Foundational Core**.

---

### Phase 2: Project Setup, Database Foundation, Authentication, and RBAC
- **Prerequisites:** Phase 1 sign-off.
- **Scope:** 
  - Maven multi-module or clean Spring Boot 3 structure setup (`pom.xml`) with Java 21 LTS.
  - Vue 3 + TypeScript + Vite project scaffolding in `frontend/`.
  - Docker Compose configuration for MySQL 8 and local runtime.
  - Flyway database migration scripts creating normalized schema (`V1__initial_schema.sql`, `V2__seed_roles_and_admin.sql`).
  - Spring Security configuration with `JwtAuthenticationFilter`, BCrypt encoder, and role hierarchy.
  - Endpoints: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`.
- **Deliverables:** Compiling backend, booting frontend, passing DB migrations, working JWT authentication, and RBAC guards.
- **Tests:** `TEST-UNIT-005` (Password BCrypt hashing), `TEST-INT-005` (JWT auth filter integration), `TEST-E2E-004` (Login UI flow).
- **Scope Marker:** **MVP Core**.

---

### Phase 3: RA Registration and CSR Processing
- **Prerequisites:** Phase 2 completion.
- **Scope:**
  - Integration of Bouncy Castle cryptographic provider (`bcpkix-jdk18on`).
  - `CsrParserService`: Parsing PKCS#10 CSRs in PEM/DER, verifying proof-of-possession digital signature.
  - Subject DN and SAN attribute extraction and validation.
  - Enrollment request API: `POST /api/v1/enrollment/requests`, `GET /api/v1/enrollment/requests/my`.
  - RA Operator queue: `GET /api/v1/enrollment/requests`, `POST /api/v1/ra/requests/{id}/approve`, `POST /api/v1/ra/requests/{id}/reject`.
  - Separation of duties enforcement: blocking self-approval.
  - Vue 3 views: CSR submission form, RA pending requests queue, request detail inspection modal.
- **Deliverables:** Working CSR upload, cryptographic signature verification, RA review and approval UI.
- **Tests:** `TEST-UNIT-001` (CSR parse & verify), `TEST-SEC-001` (CSR injection rejection), `TEST-SEC-002` (SoD self-approval block).
- **Scope Marker:** **MVP Core**.

---

### Phase 4: CA Issuance and Certificate Profiles
- **Prerequisites:** Phase 3 completion.
- **Scope:**
  - `CertificateProfileService`: Policy engine enforcing KeyUsage, EKU, validity clamping, and SAN requirements (`TLS_SERVER`, `TLS_CLIENT`, `CODE_SIGNING`, `SMIME`).
  - Software-based test signing provider (Bouncy Castle) to establish certificate generation baseline before HSM binding.
  - 128-bit CSPRNG serial number generator (`SecureRandom`).
  - `CaIssuanceService`: Assembling RFC 5280 X.509 v3 structures, signing, and saving to `certificates` table.
  - Endpoints: `POST /api/v1/ca/requests/{id}/issue`, `GET /api/v1/certificates/{serialNumber}`, `GET /api/v1/certificates/{serialNumber}/download`.
  - Vue 3 views: Certificate inventory, detail view, PEM/DER download buttons.
- **Deliverables:** Working profile enforcement, successful certificate generation, download endpoints.
- **Tests:** `TEST-UNIT-002` (Profile validation), `TEST-UNIT-003` (Serial entropy), `TEST-INT-002` (Issuance transaction).
- **Scope Marker:** **MVP Core**.

---

### Phase 5: Renewal, Revocation, and Certificate Repository
- **Prerequisites:** Phase 4 completion.
- **Scope:**
  - `RevocationService`: RFC 5280 revocation handling (`keyCompromise`, `superseded`, etc.).
  - Database tracking of revocation dates and invalidity dates.
  - Renewal workflow linking new requests to prior certificates.
  - Endpoints: `POST /api/v1/certificates/{serial}/renew`, `POST /api/v1/certificates/{serial}/revoke`, `GET /api/v1/certificates/my`.
  - Vue 3 views: Certificate renewal dialog, revocation modal with reason selection, personal certificate dashboard.
- **Deliverables:** Complete lifecycle transitions (Active -> Revoked / Renewed).
- **Tests:** `TEST-E2E-002` (Revocation flow), `TEST-SEC-008` (Revoked key reuse block).
- **Scope Marker:** **MVP Core**.

---

### Phase 6: SoftHSM Integration and Cryptographic Protection
- **Prerequisites:** Phase 5 completion.
- **Scope:**
  - `Pkcs11HsmAdapter`: SunPKCS11 configuration and dynamic registration.
  - SoftHSM2 container integration in Docker Compose with mounted token storage.
  - On-token CA asymmetric key generation (`CKA_EXTRACTABLE=false`, `CKA_SENSITIVE=true`).
  - CA Initialization ceremony endpoint (`POST /api/v1/ca/init`) and certificate import (`POST /api/v1/ca/import-cert`).
  - Migration of `CaIssuanceService` signing delegate to hardware/SoftHSM token slot 0.
  - HSM health monitoring, session reconnect, and circuit breaker.
- **Deliverables:** Zero CA private key material in Java heap or database; real PKCS#11 signing execution.
- **Tests:** `TEST-INT-001` (SoftHSM signing), `TEST-SEC-003` (Zero key leakage test), `TEST-SEC-012` (HSM circuit breaker).
- **Scope Marker:** **MVP Core (Mandatory Security Invariant)**.

---

### Phase 7: Validation, CRL, and Required OCSP Services
- **Prerequisites:** Phase 6 completion.
- **Scope:**
  - `CrlService`: Automated generation of RFC 5280 X.509 v2 CRLs signed by CA key in HSM.
  - Scheduled publication cron task writing binary `.crl` and PEM files to public directory.
  - Validation endpoint: `POST /api/v1/validation/verify-cert` verifying path, expiry, and revocation.
  - `OcspResponderService`: RFC 6960 compliant binary OCSP responder (`POST /api/v1/ocsp`, `GET /api/v1/ocsp/{request}`).
  - Endpoints: `GET /api/v1/crl/latest`, `POST /api/v1/crl/generate`.
  - Vue 3 views: CRL management dashboard, Certificate Validator test tool.
- **Deliverables:** Functioning CRL file distribution and real-time OCSP response engine.
- **Tests:** `TEST-INT-003` (CRL generation), `TEST-INT-004` (OCSP binary response), `TEST-PERF-03` (OCSP latency benchmark).
- **Scope Marker:** **MVP Core**.

---

### Phase 8: Administration, Audit, Reporting, and Notifications
- **Prerequisites:** Phase 7 completion.
- **Scope:**
  - `AuditService`: Append-only security audit event logger with chained `HMAC-SHA256` signatures.
  - Audit log query and export endpoints (`GET /api/v1/audit/events`, `GET /api/v1/audit/reports/export`).
  - Cryptographic audit chain verification tool (`GET /api/v1/audit/verify-chain`).
  - User management CRUD (`/api/v1/admin/users/**`).
  - System configuration store and Spring Boot Actuator metrics dashboard.
  - Notification hooks: Email/logging alerts for pending approvals and expiration warnings.
  - Vue 3 views: Auditor dashboard, User Administration console, System Metrics dashboard.
- **Deliverables:** Complete governance, compliance reporting, and user administration.
- **Tests:** `TEST-SEC-004` (Audit hash-chain tamper detection), `TEST-E2E-003` (Auditor workflow).
- **Scope Marker:** **MVP Core**.

---

### Phase 9: Integration Testing, Security Hardening, and Recovery Testing
- **Prerequisites:** Phase 8 completion.
- **Scope:**
  - End-to-end integration test execution across full Playwright and JUnit test suites.
  - Automated OWASP Top 10 security scanning, dependency vulnerability audit.
  - Performance and load testing (k6 / JMeter) asserting issuance and validation targets.
  - Disaster recovery testing: Database backup (`POST /api/v1/admin/backups/create`), container teardown, database restore, and audit verification.
- **Deliverables:** Complete test execution reports, security audit log, confirmed RPO/RTO metrics.
- **Tests:** `TEST-DR-001` (Disaster recovery verification), `TEST-PERF-01` (Load benchmark).
- **Scope Marker:** **Hardening & Quality Assurance**.

---

### Phase 10: Deployment, Documentation, and Demonstration
- **Prerequisites:** Phase 9 completion.
- **Scope:**
  - Multi-container Docker Compose production-like setup (Vue Nginx frontend + Spring Boot backend + MySQL 8 + SoftHSM2).
  - Comprehensive operations manual: CA key ceremony guide, HSM migration guide, disaster recovery playbook.
  - Interactive OpenAPI Swagger documentation finalized.
  - End-to-end recorded demonstration of all five stakeholder roles executing complete certificate lifecycles.
- **Deliverables:** Fully deployable, documented, demonstrated PKI Issuing CA platform ready for operational deployment.
- **Tests:** Production readiness deployment verification.
- **Scope Marker:** **Final Delivery**.

---

## 3. Scope Demarcation: MVP vs. Post-MVP Enhancements

| Feature / Capability | MVP Scope (Phases 1–8) | Post-MVP Scope (Future Iterations) |
| :--- | :---: | :---: |
| **5-Role RBAC & Web Portal** | Included | Included |
| **PKCS#10 CSR Verification** | Included | Included |
| **SoftHSM2 PKCS#11 Integration** | Included | Physical Hardware HSM (Luna/Utimaco) |
| **CA Certificate Issuance** | Included | Included |
| **Certificate Profiles (TLS/Code/Email)**| Included | Dynamic Profile Scripting Engine |
| **RFC 5280 CRL Generation & HTTP CDP**| Included | Partitioned / Delta CRLs |
| **RFC 6960 OCSP Responder** | Included | Multi-Region Geodistributed OCSP |
| **Chained HMAC-SHA256 Audit Trail** | Included | Blockchain / WORM Storage Integration |
| **MySQL Backup & Restore** | Included | Real-time Active-Active Multi-DC DB |
| **Time Stamping Authority (RFC 3161)**| Deferred | Included |
| **ACME Protocol (RFC 8555)** | Deferred | Included |
| **SCEP / EST Protocols (RFC 7030)** | Deferred | Included |
| **Active Directory / LDAP Sync** | Deferred | Included |
| **Physical Smart Card Personalization**| Deferred | Included |
