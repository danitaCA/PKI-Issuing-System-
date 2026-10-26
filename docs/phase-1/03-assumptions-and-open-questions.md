# Assumptions, Trade-offs, and Open Questions
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-ASSUMP-003
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Active Working Document
- **Version:** 1.0.0
- **Classification:** Internal Engineering

---

## 1. Workspace Inspection & Initial State Assessment

### 1.1 Workspace Finding
Upon initiating Phase 1, the local workspace directory (`c:\Users\Administrator\OneDrive\Desktop\PKI Issuing CA System`) was inspected and determined to be completely empty. No prior source code, build scripts (`pom.xml`, `package.json`), Dockerfiles, configuration files, README, or uploaded SRS diagrams existed in the directory.

### 1.2 Baseline Strategy
Because this is a clean greenfield project, the architecture and requirements baseline is established directly from the comprehensive project charter, customer technology direction, and standard PKI engineering requirements (RFC 5280, RFC 6960, NIST SP 800-57, FIPS 140-2/3). 
No legacy code or conflicting conventions must be maintained, enabling a clean, modern, and rigorously structured system design.

---

## 2. Technical & Architectural Assumptions

| ID | Category | Assumption Statement | Rationale & Architectural Impact |
| :--- | :--- | :--- | :--- |
| **ASM-01** | **PKI Hierarchy** | The system acts as a **Subordinate (Issuing) CA**, not a self-signed Root CA. | In production, Root CAs must remain offline in physical safes. The issuing CA generates a CSR during initial setup, which is signed by the offline Root CA. The imported intermediate cert and Root CA cert form the trust chain. |
| **ASM-02** | **End Entity Key Generation** | End Entity private keys are **never** generated on or sent to the CA server. | Server-side key generation introduces high cryptographic liability and violates the non-repudiation property of digital signatures. End entities must generate their own keys locally and submit only PKCS#10 CSRs containing their public key. |
| **ASM-03** | **Hardware Security Module** | SoftHSM2 is used for local development, CI, and testing; physical HSMs are used in production. | SoftHSM provides an identical PKCS#11 C-API. Java's `SunPKCS11` provider abstracts this completely. Moving to a commercial physical HSM (e.g., Thales Luna, Utimaco, AWS CloudHSM) requires only updating slot number, library path, and PIN in configuration. |
| **ASM-04** | **Deployment Architecture** | The initial system will be deployed as a **Modular Monolith** rather than distributed microservices. | A modular monolith avoids premature network complexity, distributed transactions (two-phase commits during signing and auditing), and latency, while maintaining strict logical layer decoupling. |
| **ASM-05** | **Database Selection** | MySQL 8.x (InnoDB engine) is the authoritative persistent store for metadata, requests, certificates, CRL records, and audit events. | ACID transactions, row-level locking, strong foreign keys, and binary logging satisfy RPO/RTO goals. Private keys are excluded from the database schema entirely. |
| **ASM-06** | **CRL Distribution Point** | Published CRL files are served via standard HTTP GET endpoints directly from the application or an associated static reverse proxy. | Relying on LDAP for CRL publication is outdated. RFC 5280 Section 4.2.1.13 recommends direct HTTP URLs for optimal client caching and interoperability. |
| **ASM-07** | **OCSP Service Boundary** | The OCSP responder is included within the same application codebase as a high-performance protocol endpoint, sharing the certificate repository. | Bundling the OCSP responder in Phase 7 provides unified revocation state evaluation without introducing a secondary synchronized database cluster for the MVP. |
| **ASM-08** | **Time Synchronization** | The underlying host OS and containers synchronize time via Network Time Protocol (NTP) to within 100 milliseconds of UTC. | Accurate system clocks are mandatory for X.509 validity checking, CRL `thisUpdate`/`nextUpdate`, and audit event ordering. |

---

## 3. Security & Engineering Trade-offs

### 3.1 Single Approval vs. Dual Approval for Issuance
- **Trade-off:** Requiring two independent human approvals (e.g., RA Operator + Security Officer) before signing increases operational latency versus allowing a single RA Operator to approve standard requests.
- **Decision:** **Tiered Approval Model**. Standard internal TLS server and client certificates require single RA Operator approval (with self-approval forbidden). High-assurance certificates (e.g., Subordinate CAs, Code Signing, Wildcard certificates) require dual approval (`ROLE_RA_OPERATOR` + `ROLE_SECURITY_OFFICER`).

### 3.2 SoftHSM in Docker vs. Native Host SoftHSM
- **Trade-off:** Running SoftHSM inside the backend Docker container ensures consistent developer environments across Windows, Linux, and macOS, but requires volume-mounting the token store.
- **Decision:** Containerized SoftHSM2 fixture with file-backed token storage mounted to a local persistent Docker volume. A native Windows/Linux host option is documented for development convenience.

### 3.3 JWT Stateless Tokens vs. Server-Side Sessions
- **Trade-off:** Pure stateless JWTs cannot be instantly revoked without a centralized blacklist, whereas server-side sessions require stateful storage.
- **Decision:** **Hybrid Token Architecture**. Short-lived JWT access tokens (15-minute lifespan) paired with cryptographically hashed, server-tracked refresh tokens stored in MySQL. This enables instant account lockout or token revocation while keeping normal request validation stateless.

### 3.4 Cryptographic Serial Number Strategy
- **Trade-off:** Sequential serial numbers make database indexing trivial but leak issuance volume and violate CA/Browser Forum randomness mandates.
- **Decision:** **Full 128-bit Random Serial Numbers**. Serial numbers are generated as 16-byte (128-bit) cryptographically secure pseudorandom positive big integers, prefixed with a 1-byte epoch marker to guarantee 100% uniqueness while preventing predictability attacks.

---

## 4. Open Questions & Stakeholder Decisions

The following items are documented for formal stakeholder alignment prior to or during Phase 2 implementation:

| Item # | Topic | Question / Decision Needed | Current Design Assumption | Resolution Gate |
| :--- | :--- | :--- | :--- | :--- |
| **OQ-01** | **Root CA Setup** | Will the customer provide an existing enterprise Root CA to sign the Subordinate CA CSR, or should Phase 1/2 include a script to generate a standalone Test Root CA? | Phase 2 will include a setup script/ceremony tool that generates a self-signed Test Root CA offline strictly for dev/test chain signing. | Phase 2 Kickoff |
| **OQ-02** | **Notification Channel** | What external notification mechanism is preferred for request alerts and expiration warnings (SMTP email, Slack/Teams webhooks, or database-only notifications)? | Abstracted `NotificationService` interface with default Console/DB logging and configurable SMTP email delivery. | Phase 8 |
| **OQ-03** | **Production HSM Vendor** | Which physical HSM hardware vendor is planned for production deployment (e.g., Thales Luna HSM, Utimaco CryptoServer, AWS CloudHSM, YubiHSM2)? | Designed against generic PKCS#11 v2.40 specification. SoftHSM2 used in development; configuration files isolated in `src/main/resources/pkcs11/`. | Phase 6 |
| **OQ-04** | **OCSP Responder Keying** | Should the OCSP responder sign responses using the Issuing CA's primary private key directly, or use a short-lived delegated OCSP signing certificate? | Support both: default to direct CA signing for MVP simplicity, with configuration switch to delegated OCSP key pair if required by security audit. | Phase 7 |
| **OQ-05** | **Profile Extensibility** | Should certificate profiles be dynamically editable via the Web UI in MVP, or defined via immutable code/database seed templates? | Default profiles seeded via Flyway migrations; Web UI allows activating/deactivating profiles and creating custom clones. | Phase 4 |
| **OQ-06** | **LDAP/Active Directory** | Is real-time enterprise directory identity verification required for Phase 1/MVP, or is database user authentication sufficient? | Database authentication with RBAC satisfies MVP; LDAP/AD authentication provider is architected as an optional Spring Security authentication provider in Phase 8+. | Phase 8 |

---

## 5. Potential Conflicts & Resolution Plan

### Conflict 1: Requirement for High Security vs. Greenfield Developer Ergonomics
- **Context:** Enterprise PKI requires physical HSMs, strict M-of-N ceremonies, and isolated network segments. Developers and CI pipelines cannot run physical HSMs.
- **Resolution:** Strict architectural abstraction (`CryptoProvider` interface). The exact same Java code runs against SoftHSM2 in Docker/local environments and against physical Thales/Utimaco HSMs in production without code recompilation.

### Conflict 2: Requirement for Fast Issuance vs. Cryptographic Audit Overhead
- **Context:** Computing HMAC-SHA256 chained hashes and persisting audit records synchronously can introduce a minor latency overhead during high-volume certificate issuance.
- **Resolution:** Transactional event publishing via Spring Application Events (`@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)`). Audit records are committed within the same relational transaction as the certificate issuance, guaranteeing zero audit loss with minimal overhead (sub-2ms database write).
