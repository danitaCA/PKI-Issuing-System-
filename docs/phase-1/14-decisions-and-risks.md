# Architecture Decision Records (ADRs) & Risk Register
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-ADR-014
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Architecture Baseline
- **Version:** 1.0.0
- **Classification:** Engineering Governance

---

## 1. Architecture Decision Records (ADRs)

### ADR-001: Modular Monolith vs. Microservices for Initial CA Architecture
- **Status:** **Accepted**
- **Context:** An enterprise Issuing CA manages critical cryptographic signing operations, transactional audit logging, and certificate lifecycle state. Designing the system as distributed microservices introduces network latency, distributed transaction complexity (2PC / Sagas during HSM signing), and an expanded attack surface.
- **Decision:** Architect the system as a **Hexagonal Modular Monolith** in Spring Boot 3. Domain boundaries (`ca`, `ra`, `crypto`, `validation`, `audit`, `security`) are enforced through package encapsulation and clean service interfaces.
- **Consequences:**
  - *Positive:* Atomic database transactions wrapping HSM signing and audit logging; single deployment artifact; simple local debugging and testing; minimal attack surface.
  - *Negative:* All modules scale together in MVP; horizontal scaling requires stateless configuration and central database/HSM pooling.

---

### ADR-002: SoftHSM2 via SunPKCS11 for Development & CI Cryptographic Parity
- **Status:** **Accepted**
- **Context:** Physical HSMs (e.g., Thales Luna, Utimaco, AWS CloudHSM) cost thousands of dollars and cannot run inside developer workstations, Docker Compose, or ephemeral CI runners. However, developing against mock software keys risks major deployment rework when interfacing with PKCS#11 C-APIs in production.
- **Decision:** Use **SoftHSM2** via Java’s native `SunPKCS11` provider for development, local Docker environments, and automated testing. 
- **Consequences:**
  - *Positive:* 100% API and workflow parity with commercial hardware HSMs; tests execute against real PKCS#11 tokens; zero code change required when swapping to physical HSMs in production.
  - *Negative:* SoftHSM2 does not provide physical tamper resistance; documentation must explicitly label SoftHSM as a development/test emulator.

---

### ADR-003: MySQL 8.x with Flyway for Relational Consistency and Audit Integrity
- **Status:** **Accepted**
- **Context:** The system requires absolute consistency for certificate serial numbers, request approval state machines, and relational constraints between certificates and revocations. Schema evolution must be strictly reproducible.
- **Decision:** Utilize **MySQL 8.x (InnoDB engine)** with **Flyway** migration scripts.
- **Consequences:**
  - *Positive:* Full ACID transactions; rock-solid foreign key constraints; high-performance B-Tree indexes on hexadecimal serial numbers; automated database version control.
  - *Negative:* Schema changes require versioned SQL migration scripts; requires MySQL 8 runtime container.

---

### ADR-004: Dual-Control Separation of Duties for Certificate Issuance
- **Status:** **Accepted**
- **Context:** If a single user can create an enrollment request, approve it, and trigger issuance, an insider or compromised account can issue rogue certificates undetected.
- **Decision:** Implement **Tiered Separation of Duties**:
  1. Standard profiles enforce **Self-Approval Prohibition**: `requesterId != reviewerId`.
  2. High-assurance profiles (intermediate CAs, code signing) enforce **Dual Approval**: explicit sign-off by both an RA Operator and a Security Officer before HSM signing is authorized.
- **Consequences:**
  - *Positive:* Eliminates single-operator rogue certificate generation; complies with NIST SP 800-57 dual-control mandates.
  - *Negative:* Requires at least two distinct user accounts in operational workflows.

---

### ADR-005: Inclusion of RFC 6960 OCSP Responder in Phase 7
- **Status:** **Accepted**
- **Context:** Modern browsers and relying systems increasingly rely on real-time OCSP responses rather than downloading large monolithic CRL files. The project brief requested evaluating whether OCSP should be included in MVP.
- **Decision:** Include a **lightweight, high-performance RFC 6960 binary OCSP Responder** in Phase 7 alongside validation services.
- **Consequences:**
  - *Positive:* Provides modern, real-time revocation checking; compliant with RFC 6960; satisfies enterprise zero-trust requirements.
  - *Negative:* Adds ASN.1 binary request/response codec handling in Phase 7.

---

### ADR-006: Cryptographic Provider Abstraction Interface (`CryptoProvider`)
- **Status:** **Accepted**
- **Context:** Coupling business services directly to `sun.security.pkcs11` makes unit testing complex and prevents alternative cryptographic backends.
- **Decision:** Define a clean `CryptoProvider` Java interface. Business services invoke `cryptoProvider.signCertificate(...)`. The production implementation `Pkcs11HsmAdapter` delegates to SunPKCS11; a `BouncyCastleSoftwareFallback` provider is available exclusively for lightweight unit tests.
- **Consequences:**
  - *Positive:* Complete decoupling of business logic from hardware drivers; simplifies unit testing without requiring SoftHSM daemon in every small unit test.
  - *Negative:* Requires maintaining interface contracts across providers.

---

### ADR-007: Zero Private Key Storage in Relational Database Schema
- **Status:** **Accepted**
- **Context:** Storing CA private keys (even encrypted with AES) in database tables creates catastrophic exposure risk during database backups, SQL injection attacks, or log leaks.
- **Decision:** The relational database schema contains **ZERO columns or tables for private keys**. The CA private key exists exclusively within the HSM token boundary. End entity private keys are never transmitted to the CA.
- **Consequences:**
  - *Positive:* Database dumps, replication streams, and backups can never leak CA private keys; eliminates database-driven key compromise.
  - *Negative:* Server cannot restore CA keys from database backups alone; mandates physical HSM cloning ceremonies.

---

### ADR-008: Bouncy Castle JCA/JCE for ASN.1, PKCS#10, and X.509 Handling
- **Status:** **Accepted**
- **Context:** Standard Java OpenJDK lacks native high-level builders for PKCS#10 CSR parsing, X.509 v3 extension manipulation, and RFC 6960 OCSP packet construction.
- **Decision:** Integrate maintained **Bouncy Castle libraries** (`bcprov-jdk18on` and `bcpkix-jdk18on` version 1.78+).
- **Consequences:**
  - *Positive:* Industry-standard, fully compliant ASN.1 encoding/decoding; comprehensive support for all X.509 v3 extensions and CSR proof-of-possession verification.
  - *Negative:* Library dependency size (~12MB JARs); requires vigilance regarding dependency vulnerability advisories.

---

### ADR-009: Java 21 LTS Target with Spring Boot 3.3.4
- **Status:** **Accepted (Phase 2)**
- **Context:** Enterprise stability requires long-term support (LTS) Java runtime and production-grade framework support.
- **Decision:** Target Java 21 LTS with Spring Boot 3.3.4, Flyway 10, Spring Security 6.3, and JaCoCo 0.8.12.
- **Consequences:**
  - *Positive:* Access to modern Java language features (records, pattern matching, virtual threads), strict typing, and active security maintenance.
  - *Negative:* Build scripts must bind compiler explicitly to Java 21 toolchain in environments where multiple JDKs exist.

---

### ADR-010: Account Lockout Transaction Isolation Policy
- **Status:** **Accepted (Phase 2)**
- **Context:** When a user enters invalid credentials, Spring Security throws `BadCredentialsException` (an unchecked runtime exception). By default, Spring `@Transactional` triggers a rollback, which would discard increments to `failed_login_attempts` and prevent the account from locking after 5 consecutive failures.
- **Decision:** Configure `@Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})` on authentication services so state changes persist while still bubbling authentication failures to callers.
- **Consequences:**
  - *Positive:* Prevents brute-force credential stuffing by reliably committing lockout counters across failure boundaries.
  - *Negative:* Requires explicit declaration on authentication transaction boundaries.

---

### ADR-011: Hybrid Dual-Token Strategy, sessionStorage Threat Model & Invalidation
- **Status:** **Accepted (Phase 2)**
- **Context:** Pure stateless JWT cannot be revoked before expiration without a global blacklist, whereas purely stateful server sessions add database roundtrip overhead to every API request. Furthermore, client-side token storage choices (`localStorage` vs `sessionStorage` vs `HttpOnly` cookies) carry distinct threat vectors regarding Cross-Site Scripting (XSS) and Cross-Site Request Forgery (CSRF).
- **Decision:**
  1. **Token Hierarchy:** Issue short-lived HMAC-SHA256 JWT access tokens (15 minutes) for low-overhead stateless verification on protected routes. Pair with persisted opaque refresh tokens in `user_tokens`.
  2. **Single-Use Rotation & Replay Detection:** Refresh tokens rotate on every invocation. If a revoked/stale token is replayed, the system assumes token compromise and terminates all active sessions for that user.
  3. **Storage Strategy:** Store active tokens in browser `sessionStorage`. Unlike `localStorage` (which persists indefinitely across tabs, windows, and machine reboots), `sessionStorage` is isolated strictly to the active browser tab and is destroyed immediately upon tab close, reducing exposure on shared workstation terminals.
  4. **XSS Countermeasures:**
     - Zero usage of raw HTML directives (`v-html`) across the entire Vue 3 codebase; all dynamic attributes and user identity fields are rendered via compiler-escaped mustache interpolations (`{{ }}`).
     - Strict HTTP security headers configured in `SecurityConfig`: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and strict CORS whitelist prohibiting wildcard origins (`*`) with credentials.
- **Consequences:**
  - *Positive:* Fast, stateless authorization on high-frequency API endpoints; strong session revocation controls; defense-in-depth against XSS-driven token exfiltration.
  - *Negative:* Access tokens remain valid for their remaining lifespan (<= 15 minutes) unless explicitly coupled with a memory cache blacklist.

---

## 2. Project Risk Register & Mitigation Matrix

| Risk ID | Risk Description | Category | Prob. | Impact | Inherent Score | Mitigation Strategy | Contingency Plan | Risk Owner |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- | :--- | :--- |
| **RSK-01** | **SoftHSM2 Platform Incompatibilities** | Technical | Medium | High | **HIGH** | Containerize SoftHSM2 in Docker with pre-initialized token slot and standard `libsofthsm2.so` paths. | Provide native Linux/Windows local installation guide and software fallback provider for unit tests. | Tech Lead |
| **RSK-02** | **HSM Session Dropping Under Load** | Operational | Medium | High | **HIGH** | Implement connection pooling, automatic session re-authentication, and a Resilience4j circuit breaker on PKCS#11 calls. | Isolate signing operations; return HTTP 503 with Retry-After header during reconnection. | Crypto Lead |
| **RSK-03** | **Audit Trail Database Mutation** | Security | Low | Critical| **HIGH** | Implement HMAC-SHA256 cryptographic hash-chaining; revoke SQL `UPDATE`/`DELETE` grants for application DB user. | Auditor verification tool flags modified sequence ID; restore audit record from encrypted read-only replica. | Security Architect |
| **RSK-04** | **Serial Number Entropy Collision** | Cryptographic | Very Low| Critical| **HIGH** | Utilize `SecureRandom` generating 128-bit random big integers; enforce MySQL `UNIQUE` constraint on `serial_number`. | System retries serial generation if collision detected before signing. | PKI Engineer |
| **RSK-05** | **Separation of Duties Bypass** | Governance | Low | High | **HIGH** | Hardcoded check in `RaApprovalService` verifying `requesterId != reviewerId`; automated security regression tests in CI. | Block approval at controller level; raise immediate security incident alert. | Security Architect |
| **RSK-06** | **Large CRL File Generation Bottleneck**| Performance | Medium | Medium | **MEDIUM** | Indexed database queries on active revocations; cached CRL files served as static files via Nginx. | Implement partitioned CRLs or rely on real-time OCSP queries for high-volume deployments. | Backend Architect |
| **RSK-07** | **Vue 3 / Backend Contract Desynchronization**| Development | Medium | Medium | **MEDIUM** | SpringDoc OpenAPI 3.0 contract generation; TypeScript interfaces generated directly from OpenAPI schema. | Run automated contract verification tests in CI build pipeline. | Full Stack Lead |
| **RSK-08** | **Time Drift Causing Premature Expiry** | Infrastructure| Low | High | **HIGH** | Enforce NTP synchronization across host OS and Docker containers; monitor time drift in health actuator. | Alert administrators when NTP drift exceeds 500ms; clamp certificate validity windows safely. | DevOps Engineer |
