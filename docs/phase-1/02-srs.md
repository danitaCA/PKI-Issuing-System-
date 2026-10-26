# Software Requirements Specification (SRS)
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-SRS-002
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Requirements Baseline
- **Version:** 1.0.0
- **Classification:** Highly Confidential / Engineering Specification

---

## 1. Introduction & System Purpose

The PKI Issuing Certificate Authority (CA) System is an enterprise-grade, web-based cryptographic service responsible for issuing, managing, validating, and revoking X.509 v3 public key certificates. The system operates as a subordinate/issuing CA within a Public Key Infrastructure, anchoring its trust to an offline or organizational Root CA.

All requirements in this specification follow a standardized unique identifier convention:
- `FR-xxx`: Functional Requirements
- `CR-xxx`: Cryptographic Requirements
- `SR-xxx`: Security Requirements
- `HR-xxx`: Hardware Security Module (HSM) Requirements
- `VR-xxx`: Validation & Revocation Requirements
- `NFR-xxx`: Non-Functional Requirements
- `IR-xxx`: Integration Requirements
- `COMP-xxx`: Compliance & Standards Requirements
- `ALR-xxx`: Audit Logging & Reporting Requirements
- `BDR-xxx`: Backup & Disaster Recovery Requirements
- `AC-xxx`: Acceptance Criteria

---

## 2. Functional Requirements (FR)

### 2.1 CA Management
- **FR-001 (CA Initialization & Identity):** The system shall support subordinate CA initialization by generating an asymmetric key pair within the HSM, generating a PKCS#10 Certificate Signing Request (CSR) for the Issuing CA, and importing the signed X.509 intermediate CA certificate issued by the parent Root CA.
- **FR-002 (CA Status Monitoring):** The system shall display the operational state of the Issuing CA (Active, Degraded, Inactive, Expired, Suspended), certificate validity dates, serial number, subject DN, issuer DN, and current CRL generation schedule.
- **FR-003 (CA Key Lifecycle State):** The system shall track the cryptographic status of the active CA signing key (Initialized, Active, Retired, Compromised) without exposing key material.
- **FR-004 (Trust Anchor Chain Export):** The system shall provide endpoints and UI controls to download the complete certificate chain (Issuing CA certificate and Root CA certificate) in PEM and DER formats.

### 2.2 Identity Enrollment & CSR Processing
- **FR-010 (PKCS#10 CSR Submission):** The system shall accept standard PEM-encoded and DER-encoded PKCS#10 CSRs submitted via the web portal or REST API.
- **FR-011 (CSR Proof-of-Possession Verification):** The system shall cryptographically verify the signature on the PKCS#10 CSR before accepting the request, proving that the requester holds the private key matching the public key in the CSR.
- **FR-012 (CSR Attribute Parsing):** The system shall parse and validate Subject Distinguished Name (DN) components (Common Name, Organization, Organizational Unit, Country, State, Locality) and Subject Alternative Names (SANs) including DNS Names, IP Addresses, and RFC822 Email addresses.
- **FR-013 (End Entity Key Policy Enforcement):** The system shall reject CSRs submitted with disallowed public key types (e.g., RSA keys < 2048 bits, unsupported elliptic curves, or insecure parameter sets).
- **FR-014 (Requester Association):** The system shall bind each enrollment request to an authenticated End Entity or an administrative requester identity with an immutable timestamp.

### 2.3 Registration Authority (RA) Workflow & Approval
- **FR-020 (Queued Request Management):** The system shall maintain an authenticated RA work queue displaying pending enrollment requests with search, filtering, and detail inspection capabilities.
- **FR-021 (Dual-Control Approval Enforcement):** The system shall enforce that an enrollment request requires explicit review and approval by an authorized RA Operator (`ROLE_RA_OPERATOR`) or CA Administrator (`ROLE_CA_ADMIN`) prior to cryptographic signing.
- **FR-022 (Self-Approval Prohibition):** The system shall prevent an RA Operator from approving an enrollment request that they submitted themselves (Separation of Duties).
- **FR-023 (Request Rejection):** The system shall enable an RA Operator to reject an enrollment request with a mandatory structured rejection reason and notes, updating the request state to `REJECTED` and notifying the requester.
- **FR-024 (Request Inspection & Modification Constraints):** The system shall allow an RA Operator to restrict or modify requested SANs or validity periods only within the bounds permitted by the selected Certificate Profile, without altering the applicant's public key.

### 2.4 Certificate Lifecycle: Issuance, Retrieval, Renewal, Revocation
- **FR-030 (Cryptographic Issuance):** Upon final RA approval, the system shall invoke the HSM-backed cryptographic provider to sign the X.509 v3 certificate according to the assigned Certificate Profile and generate an immutable certificate record.
- **FR-031 (Serial Number Uniqueness):** The system shall generate a cryptographically random, positive serial number containing at least 64 bits of entropy (up to 20 octets total length) in accordance with RFC 5280 Section 4.1.2.2, enforcing absolute uniqueness in the certificate database.
- **FR-032 (Certificate Download & Packaging):** The system shall allow authorized users to download issued certificates in PEM, DER, and PKCS#7 chain bundles (`.p7b`).
- **FR-033 (Certificate Renewal Request):** The system shall support certificate renewal submissions for existing active certificates. Renewal may reuse existing subject identity parameters or submit an updated CSR with a new key pair.
- **FR-034 (Certificate Revocation Request):** The system shall allow the certificate owner, RA Operator, Security Officer, or CA Administrator to submit a revocation request specifying an RFC 5280 revocation reason code (`unspecified`, `keyCompromise`, `cACompromise`, `affiliationChanged`, `superseded`, `cessationOfOperation`, `certificateHold`).
- **FR-035 (Revocation Processing):** Upon authorization, the system shall update the certificate state to `REVOKED`, record the revocation date, reason code, and invalidity date, and immediately mark the certificate for inclusion in the next CRL publication.
- **FR-036 (Certificate Search & Inventory):** The system shall provide an indexed search interface across all issued, expired, and revoked certificates filtered by serial number, Common Name, SAN, status, issuer, and validity range.

### 2.5 Certificate Profiles & Policy Engine
- **FR-040 (Configurable Profile Templates):** The system shall support configurable Certificate Profiles defining allowable key types, key usages, extended key usages, validity periods, subject DN schemas, and extension criticality.
- **FR-041 (Standard Default Profiles):** The system shall provide built-in, immutable system profiles:
  - `TLS_SERVER`: Server authentication, Digital Signature, Key Encipherment / Key Agreement, SAN required.
  - `TLS_CLIENT`: Client authentication, Digital Signature, Key Agreement.
  - `CODE_SIGNING`: Code signing EKU, Digital Signature, strictly bounded validity.
  - `SMIME`: Email protection, Digital Signature, Key Encipherment, RFC822 SAN.
- **FR-042 (Validity Duration Clamping):** The system shall clamp requested certificate validity periods so they never exceed the remaining validity period of the Issuing CA certificate or the maximum limit set by the profile.

### 2.6 Validation Services & Revocation Checking
- **FR-050 (Path & Chain Validation):** The system shall provide a validation endpoint that validates an X.509 certificate against the Issuing CA trust anchor, verifying certificate signature, validity window (NotBefore / NotAfter), basic constraints, and revocation status.
- **FR-051 (Revocation Status Evaluation):** The system shall evaluate whether a given certificate serial number is currently revoked, suspended, or active based on database records and published CRLs.

### 2.7 CRL Generation & Publication
- **FR-060 (RFC 5280 CRL Format):** The system shall generate RFC 5280 compliant X.509 v2 Certificate Revocation Lists (CRLs) signed by the Issuing CA private key in the HSM.
- **FR-061 (Scheduled CRL Generation):** The system shall execute an automated scheduled task to regenerate and publish fresh CRLs before the `nextUpdate` timestamp expires (e.g., daily generation with a 72-hour validity window).
- **FR-062 (On-Demand CRL Generation):** The system shall enable authorized administrators and security officers to trigger an immediate out-of-band CRL regeneration upon emergency revocation.
- **FR-063 (CRL File Distribution Point):** The system shall write published CRL files (DER and PEM) to a public-facing file distribution path accessible via standard HTTP GET requests matching the CRL Distribution Point (CDP) extension URI in issued certificates.

### 2.8 Online Certificate Status Protocol (OCSP)
- **FR-065 (RFC 6960 OCSP Responder):** The system shall implement an RFC 6960 compliant OCSP responder that processes binary OCSP requests via HTTP POST (`application/ocsp-request`) and HTTP GET (URL-encoded base64), returning signed OCSP responses (`application/ocsp-response`).
- **FR-066 (OCSP Signing Key):** The OCSP responder shall sign responses using either the Issuing CA private key or a dedicated authorized OCSP responder certificate issued by the CA containing the `id-kp-OCSPSigning` extended key usage.
- **FR-067 (OCSP Status Codes):** Responses shall accurately report `good`, `revoked`, or `unknown` with cryptographic timestamps (`thisUpdate`, `nextUpdate`).

### 2.9 Key Management & HSM Operations
- **FR-070 (PKCS#11 Integration):** The system shall interact with cryptographic tokens exclusively via the Java Cryptography Architecture (JCA) SunPKCS11 provider bridge.
- **FR-071 (On-Token CA Key Generation):** CA asymmetric signing keys shall be generated directly within the cryptographic token boundary, flagged with `CKA_PRIVATE=true`, `CKA_SENSITIVE=true`, and `CKA_EXTRACTABLE=false`.
- **FR-072 (Zero Private Key Exposure):** The system shall never serialize, log, transmit, cache, or store CA private key bytes in application memory, relational tables, or flat files.
- **FR-073 (HSM Session & Slot Management):** The system shall maintain authenticated PKCS#11 sessions with the designated token slot, handling session renewal, pin authentication, and automatic reconnection upon transient disconnects.
- **FR-074 (SoftHSM Development Parity):** The development and test environments shall utilize SoftHSM2 via the exact same SunPKCS11 configuration mechanism used by production hardware HSMs.

### 2.10 Administration, Auditing, & Monitoring
- **FR-080 (User & Role Administration):** CA Administrators shall be able to create, update, activate, and deactivate system user accounts and assign RBAC roles.
- **FR-081 (System Configuration Parameters):** The system shall manage global configuration parameters (CRL validity periods, default validity clamping, notification email addresses, session timeouts) via an audited configuration store.
- **FR-082 (Health & Metrics Endpoints):** The system shall expose Spring Boot Actuator health, readiness, liveness, and Prometheus metrics for operational observability.
- **FR-083 (HSM Connectivity Health Check):** The system shall provide an automated periodic health check verifying active HSM session responsiveness without performing unnecessary signing operations.
- **FR-090 (Email / Event Notification Hooks):** The system shall record and trigger notification events for request submissions, approval requirements, certificate issuance completion, pending expiration warnings (30/15/7 days), and revocation notices.
- **FR-100 (Immutable Audit Event Logging):** The system shall record every security-relevant event (authentication attempts, CSR submissions, approvals, rejections, signing operations, revocations, profile edits, system configuration changes, backup runs) into an append-only audit repository.
- **FR-101 (Audit Trail Integrity):** Each audit record shall include an incremental sequence number, timestamp, authenticated principal ID, client IP, action, outcome, and a cryptographic checksum (HMAC-SHA256) chaining to the previous record to guarantee tamper evidence.
- **FR-102 (Audit Reporting & Export):** Auditors shall have access to search, filter, and export audit trails to CSV and JSON formats.

### 2.11 Backup & Disaster Recovery
- **FR-110 (Database Backup Initiation):** The system shall provide automated and administrative on-demand database backup execution scripts generating encrypted MySQL dumps.
- **FR-111 (HSM Backup Protocol Documentation):** The system documentation shall define the air-gapped M-of-N split-knowledge smartcard backup procedures required for physical HSM partition restoration.

---

## 3. Cryptographic Requirements (CR)

| Requirement ID | Cryptographic Specification | Standards Reference | Scope / Policy |
| :--- | :--- | :--- | :--- |
| **CR-001** | **X.509 v3 Structure** | RFC 5280 | All issued certificates must strictly adhere to X.509 version 3 structure and DER encoding. |
| **CR-002** | **RSA Key Sizes** | NIST SP 800-57, FIPS 186-5 | CA Signing Key: RSA 3072 or 4096-bit.<br/>End Entity Keys: RSA 2048, 3072, or 4096-bit. Keys < 2048 bits strictly rejected. |
| **CR-003** | **ECDSA Curves** | SEC 2 / FIPS 186-5 | Supported curves: NIST P-256 (`secp256r1`) and NIST P-384 (`secp384r1`). |
| **CR-004** | **Signature Algorithms** | RFC 5754 / RFC 5280 | Approved signature algorithms:<br/>- `SHA256withRSA` / `SHA384withRSA` / `SHA512withRSA`<br/>- `SHA256withECDSA` / `SHA384withECDSA`<br/>MD5, SHA-1, and RSA-PSS without explicit parameter validation are strictly prohibited. |
| **CR-005** | **Cryptographic Serial Numbers** | RFC 5280 §4.1.2.2 | Positive integer up to 20 octets (160 bits). Must contain at least 64 bits of cryptographically secure pseudorandom entropy (via `SecureRandom`). |
| **CR-006** | **Basic Constraints Extension** | RFC 5280 §4.2.1.9 | - End Entity Certificates: `critical=true`, `cA=false`.<br/>- Issuing CA Certificate: `critical=true`, `cA=true`, `pathLenConstraint=0`. |
| **CR-007** | **Key Usage Extension** | RFC 5280 §4.2.1.3 | Critical extension. Allowed bits strictly enforced by profile:<br/>- TLS Server: `digitalSignature`, `keyEncipherment` (RSA only).<br/>- TLS Client: `digitalSignature`, `keyAgreement` (ECDSA).<br/>- Code Signing: `digitalSignature`. |
| **CR-008** | **Extended Key Usage (EKU)** | RFC 5280 §4.2.1.12 | Non-critical or critical depending on profile:<br/>- Server: `id-kp-serverAuth` (`1.3.6.1.5.5.7.3.1`)<br/>- Client: `id-kp-clientAuth` (`1.3.6.1.5.5.7.3.2`)<br/>- Code Sign: `id-kp-codeSigning` (`1.3.6.1.5.5.7.3.3`)<br/>- Email: `id-kp-emailProtection` (`1.3.6.1.5.5.7.3.4`)<br/>- OCSP: `id-kp-OCSPSigning` (`1.3.6.1.5.5.7.3.9`) |
| **CR-009** | **Subject Key Identifier (SKI)** | RFC 5280 §4.2.1.2 | Non-critical. 160-bit SHA-1 hash of the public key bit string as defined in RFC 5280 §4.2.1.2 Method 1. |
| **CR-010** | **Authority Key Identifier (AKI)** | RFC 5280 §4.2.1.1 | Non-critical. Contains keyIdentifier matching the Issuing CA certificate's SKI. |
| **CR-011** | **CRL Distribution Points (CDP)** | RFC 5280 §4.2.1.13 | Non-critical. Populated with absolute HTTP URI pointing to the CA's published CRL distribution point. |
| **CR-012** | **Authority Info Access (AIA)** | RFC 5280 §4.2.2.1 | Non-critical. Contains `id-ad-caIssuers` HTTP URI (CA cert download) and `id-ad-ocsp` HTTP URI (OCSP responder). |
| **CR-013** | **Subject Alternative Name (SAN)** | RFC 5280 §4.2.1.6 | Required on all TLS server certificates. Supports `dNSName`, `iPAddress`, and `rfc822Name`. Wildcard entries permitted only under explicit profile authorization. |
| **CR-014** | **CSR Proof-of-Possession** | RFC 2986 (PKCS#10) | Verification of applicant signature across `CertificationRequestInfo` structure using public key enclosed in CSR. |
| **CR-015** | **Entropy Sources** | NIST SP 800-90A | All cryptographic randomness generated using `java.security.SecureRandom` utilizing OS entropy pool (`/dev/urandom` / Windows CryptGenRandom). |

---

## 4. Security Requirements (SR)

### 4.1 Authentication & Session Management
- **SR-001 (Strong Authentication):** All web portal and REST API requests (except public CRL/OCSP/health endpoints) must be authenticated using modern JSON Web Tokens (JWT) signed with HMAC-SHA256 or asymmetric keys, or secure HttpOnly session cookies.
- **SR-002 (Password Security):** User passwords must be hashed using BCrypt (work factor >= 12) or Argon2id. Plaintext passwords must never be stored, displayed, or logged.
- **SR-003 (Session Invalidation & Expiration):** Bearer tokens must expire within 15 minutes of inactivity; refresh tokens must be cryptographically stored, bound to client IP/User-Agent, and expire within 8 hours. Explicit logout must invalidate active tokens.
- **SR-004 (Account Lockout & Brute Force Protection):** Five consecutive failed authentication attempts within a 15-minute window must temporarily lock the user account for 30 minutes and emit a high-priority security audit event.

### 4.2 Role-Based Access Control (RBAC) & Separation of Duties
- **SR-005 (Least Privilege):** Endpoints and domain services must enforce explicit role-based permissions (`@PreAuthorize("hasRole('...')")`). No identity shall possess default blanket permissions.
- **SR-006 (Separation of Approval and Issuance):** For security-sensitive profiles (e.g., intermediate CAs or high-assurance server certs), certificate issuance requires dual approval or administrative execution distinct from request submission.
- **SR-007 (Resource Ownership):** End Entities can query and download only their own certificates and requests. Cross-tenant or unauthorized entity data access must be blocked at the database query level.

### 4.3 Data Protection In Transit & At Rest
- **SR-008 (Transport Layer Security):** All external and internal HTTP traffic must be encrypted using TLS 1.3 or TLS 1.2 with forward secrecy cipher suites (ECDHE-RSA-AES256-GCM, ECDHE-ECDSA-AES256-GCM). Insecure protocols (SSL v3, TLS 1.0, TLS 1.1) must be disabled.
- **SR-009 (Database Data Protection):** MySQL database connections must enforce SSL/TLS encryption (`useSSL=true&requireSSL=true`). Sensitive configuration parameters (e.g., external API credentials, SMTP secrets) must be encrypted at rest using AES-256-GCM.
- **SR-010 (Zero CA Key In Database):** The database schema must not contain any field or column designed to store CA private keys. CA private keys remain strictly on the HSM token.

### 4.4 Web Security Controls
- **SR-011 (Cross-Site Scripting [XSS] Prevention):** The Vue 3 frontend must rely on standard context-aware template interpolation; raw `v-html` is strictly prohibited. The backend must enforce Content-Security-Policy (CSP) headers.
- **SR-012 (Cross-Site Request Forgery [CSRF] Defense):** The backend must implement double-submit CSRF cookie tokens or rely on stateless Authorization headers with strict SameSite cookies.
- **SR-013 (Input Sanitization & Injection Defense):** All incoming parameters must be validated using Spring Bean Validation (`@Valid`, `@Pattern`, `@Size`) and passed via parameterized JPA/Hibernate queries to prevent SQL injection.
- **SR-014 (Rate Limiting & DoS Defense):** API endpoints (particularly authentication, enrollment, and validation) must enforce IP-based and user-based token bucket rate limiting (e.g., 60 requests/minute for general APIs, 5 requests/minute for login).

---

## 5. Hardware Security Module (HSM) Requirements (HR)

- **HR-001 (PKCS#11 API Standard):** The system shall interface with cryptographic hardware through standard PKCS#11 v2.20 / v2.40 interfaces via Java's `SunPKCS11` provider.
- **HR-002 (Non-Exportable CA Keys):** CA private keys generated within the token must have attributes `CKA_EXTRACTABLE=CK_FALSE`, `CKA_SENSITIVE=CK_TRUE`, and `CKA_PRIVATE=CK_TRUE`. Any attempt to export or wrap the CA private key in plaintext must fail.
- **HR-003 (Cryptographic Isolation):** Digital signature operations for certificates, CRLs, and OCSP responses must be executed internally within the HSM boundary. Key bytes must never enter CPU host memory.
- **HR-004 (PIN & Credential Isolation):** HSM user PINs must be supplied via secure environment variables or vault references at container startup and never written to application disk files or database tables.
- **HR-005 (SoftHSM v2 Emulation):** For development, CI, and staging environments, the system shall integrate with SoftHSM2. SoftHSM2 is recognized as an API-compliant emulator, not a certified physical tamper-resistant device.
- **HR-006 (Production HSM Portability):** The JCA/PKCS#11 abstraction layer must support zero-code-change migration from SoftHSM2 to production hardware HSMs (e.g., Thales Luna, Utimaco CryptoServer, YubiHSM2, Entrust nShield, AWS CloudHSM) via configuration file updates (`pkcs11.cfg`).

---

## 6. Validation & Revocation Requirements (VR)

- **VR-001 (RFC 5280 Path Validation):** The validation engine must verify candidate certificate signatures using the public key of the issuer, verify validity timestamps against system time or caller-supplied verification time, and inspect basic constraints and key usage flags.
- **VR-002 (High-Performance CRL Lookup):** Revocation checks against published CRLs must utilize an in-memory indexed cache or database index to achieve sub-millisecond status resolution.
- **VR-003 (OCSP Responder High Availability):** The OCSP responder component must be capable of running statelessly with horizontal replicas reading from a read-only database replica or distributed cache.
- **VR-004 (Authoritative Revocation Timestamps):** When a certificate is revoked, the exact revocation timestamp and RFC 5280 reason code must be preserved and rendered identically across both CRL entries and OCSP responses.

---

## 7. Non-Functional Requirements (NFR)

### 7.1 Performance & Throughput
- **NFR-001 (Issuance Throughput):** With SoftHSM2, the system shall process and sign at least 10 certificate issuance requests per second. With production hardware HSM, target throughput is >= 50 certificates per second.
- **NFR-002 (Validation Response Latency):** Certificate path and revocation validation queries via REST API must respond within 50 milliseconds (p95) under normal load (100 concurrent requests).
- **NFR-003 (OCSP Response Latency):** OCSP HTTP requests must respond within 25 milliseconds (p95) under sustained load of 500 requests per second.
- **NFR-004 (CRL Generation Duration):** Scheduled CRL generation for a revocation list containing up to 100,000 entries must complete within 10 seconds.

### 7.2 Availability & Reliability
- **NFR-005 (System Uptime Target):** The core CA management and validation services shall achieve 99.9% availability during business hours.
- **NFR-006 (Transaction Atomicity):** Certificate issuance and database persistence must be strictly atomic: if database write fails, the certificate is marked unusable; if HSM signing fails, the enrollment request state reverts to `APPROVED` with an error log and does not create an orphaned certificate.

### 7.3 Scalability & Maintainability
- **NFR-007 (Database Capacity):** The data model and indexes must support storage and retrieval of at least 1,000,000 issued certificates without query performance degradation.
- **NFR-008 (Modular Architecture):** The backend modular monolith must enforce clean package decoupling between Web, Domain, Service, Crypto, and Persistence tiers.
- **NFR-009 (Automated Test Coverage):** The codebase must maintain >= 80% line test coverage across domain logic, cryptographic services, and REST controllers.

---

## 8. Integration Requirements (IR)

- **IR-001 (LDAP / Active Directory [Deferred]):** Architecture must provide extension points (`IdentityLookupProvider`) to allow future phase integration with enterprise LDAP/AD for requester identity pre-validation.
- **IR-002 (SIEM Log Streaming [Deferred]):** Structured audit logs must be emitted in JSON format via standard loggers (Logback) to enable seamless ingestion by Splunk, ElasticSearch, or FluentBit.
- **IR-003 (RESTful API Integration):** External IT automation systems must be able to submit CSRs, track approvals, and retrieve certificates via documented OpenAPI 3.0 REST endpoints.

---

## 9. Compliance & Standards (COMP)

The system design aligns with and incorporates principles from the following formal standards:
- **COMP-001 (RFC 5280):** Internet X.509 Public Key Infrastructure Certificate and Certificate Revocation List (CRL) Profile.
- **COMP-002 (RFC 6960):** X.509 Internet Public Key Infrastructure Online Certificate Status Protocol - OCSP.
- **COMP-003 (RFC 2986 / PKCS#10):** Certification Request Syntax Specification Version 1.7.
- **COMP-004 (NIST SP 800-57):** Recommendation for Key Management (Part 1: General).
- **COMP-005 (FIPS 140-2 / FIPS 140-3):** Security Requirements for Cryptographic Modules (design requirements for HSM boundary, role separation, and physical protection in production).
- **COMP-006 (CA/Browser Forum Baseline Requirements):** Adopted industry best practices for serial number randomness (64 bits of entropy) and validity clamping.

---

## 10. Audit Logging & Reporting Requirements (ALR)

- **ALR-001 (Comprehensive Security Event Catalog):** The system shall log all events in the following categories:
  - User Authentication (Login success, login failure, logout, session expiration, token refresh).
  - RBAC & Authorization (Access denied, privilege escalation attempts).
  - CA Lifecycle (CA initialization, CA certificate import, key status change).
  - Enrollment & RA (CSR submission, validation error, RA approval, RA rejection).
  - Cryptographic Signing (Certificate issued, CRL generated, OCSP signing failure).
  - Revocation (Revocation requested, revocation approved, certificate unrevoked/unhold).
  - Profile & Config (Profile created, profile modified, system configuration updated).
  - Backup & Recovery (Backup created, restore initiated, restore failure).
- **ALR-002 (Audit Record Content):** Every log entry must record: `eventId`, `timestamp` (UTC ISO-8601), `principalId`, `principalRole`, `clientIpAddress`, `userAgent`, `action`, `resourceType`, `resourceId`, `outcome` (SUCCESS/FAILURE), `errorCode`, `details` (JSON payload), and `signatureHash`.
- **ALR-003 (Tamper-Evidence Chaining):** Each audit record's `signatureHash` shall be computed as `HMAC-SHA256(previousHash + recordPayload, auditHmacSecret)` creating an unbreakable cryptographic chain.
- **ALR-004 (Audit Immutability):** Audit records cannot be edited or deleted via any application endpoint or UI. The database user configured for standard app operations shall be restricted from `DELETE` or `UPDATE` operations on the `audit_events` table.
- **ALR-005 (Export & Retention):** The system shall support filtered export of audit logs to signed CSV/JSON files, supporting a default organizational retention policy of 7 years.

---

## 11. Backup & Disaster Recovery Requirements (BDR)

- **BDR-001 (Database Backup):** Automated daily full backups and continuous binary logging for point-in-time recovery of the MySQL database.
- **BDR-002 (Recovery Point Objective [RPO]):** The RPO for certificate records and audit logs shall be <= 1 hour.
- **BDR-003 (Recovery Time Objective [RTO]):** The RTO for restoring full CA verification and issuance capabilities shall be <= 4 hours.
- **BDR-004 (Air-Gapped Key Ceremony):** CA private key backup procedures shall mandate vendor-specific secure HSM cloning protocols utilizing M-of-N split smart cards held by designated Security Officers in tamper-evident physical envelopes.

---

## 12. Traceable Acceptance Criteria (AC)

- **AC-001 (CA Initialization):** Verified when an administrator initializes an Issuing CA, a key pair is generated in SoftHSM token slot 0, a valid CSR is exported, and importing the root-signed certificate transitions CA status to `ACTIVE`.
- **AC-002 (CSR Validation):** Verified when a valid RSA-2048 PKCS#10 CSR is successfully parsed, signature verified, and stored in state `PENDING_REVIEW`, while an RSA-1024 or malformed CSR is immediately rejected with HTTP 400.
- **AC-003 (RA Separation of Duties):** Verified when an RA Operator approves an enrollment request submitted by an End Entity, but cannot approve an enrollment request submitted under their own username (HTTP 403 Forbidden).
- **AC-004 (HSM Certificate Issuance):** Verified when approval triggers signing within SoftHSM, an X.509 v3 certificate with 64 bits of random serial entropy is produced, matching the assigned profile extensions, and stored in MySQL.
- **AC-005 (Certificate Revocation & CRL):** Verified when an active certificate is revoked with reason `keyCompromise`, the certificate status immediately becomes `REVOKED`, and the subsequent CRL generation includes the certificate's serial number, reason code, and revocation date.
- **AC-006 (Chain Validation):** Verified when `POST /api/v1/validation/verify-cert` accurately returns `VALID` for an active issued certificate, `REVOKED` for a revoked certificate, and `UNTRUSTED_ISSUER` for a certificate signed by an unknown root.
- **AC-007 (Audit Log Integrity):** Verified when attempts to alter an audit record directly in MySQL invalidate the cryptographic chain calculation when inspected by the Auditor verification tool.
- **AC-008 (Zero Key Leakage):** Verified via static analysis, heap inspection, and database inspection that no private key material is ever stored in tables, logged to files, or returned in API responses.
