# Threat Model & Security Requirements
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-SEC-009
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / Security Baseline
- **Version:** 1.0.0
- **Classification:** Highly Confidential / Security Architecture

---

## 1. Cryptographic Key Boundaries & Core Security Invariants

In a Public Key Infrastructure, the security of the entire ecosystem rests upon strict cryptographic isolation and unambiguous key ownership boundaries.

```
+=============================================================================+
|                      CRYPTOGRAPHIC KEY ISOLATION ENCLAVE                    |
|                                                                             |
|  +-------------------------------------+   +-----------------------------+  |
|  | END ENTITY CLIENT BOUNDARY          |   | ISSUING CA HSM BOUNDARY     |  |
|  | - Client generates Private Key      |   | - Token Slot 0 (PIN-gated)  |  |
|  | - Client stores key in local safe   |   | - CA Private Signing Key    |  |
|  | - NEVER sent to CA or web server    |   |   CKA_EXTRACTABLE = FALSE   |  |
|  | - Only PKCS#10 CSR is transmitted   |   |   CKA_SENSITIVE   = TRUE    |  |
|  +------------------+------------------+   |   CKA_PRIVATE     = TRUE    |  |
|                     |                      | - NEVER enters JVM Heap     |  |
|                     | Public Key + Sig     | - NEVER stored in Database  |  |
|                     v                      +--------------+--------------+  |
|  +-------------------------------------+                  |                 |
|  | CA APPLICATION APPLICATION SERVER   |                  |                 |
|  | - Ingests and verifies CSR          |                  | JNI / PKCS#11   |
|  | - Evaluates profile policy          |                  v                 |
|  | - Requests HSM signature via handle +------------------+--------------+  |
|  | - Stores X.509 Certificate in MySQL | Cryptographic Engine Sign Request  |
+=============================================================================+
```

### 1.1 Invariant SEC-INV-1: End Entity Key Sovereignty
- The End Entity private key is generated **strictly on the client system**.
- The CA application has **zero exposure** to the client's private key.
- Submission of private keys in enrollment requests is prohibited and blocked by input validation.
- The applicant proves possession of their private key through the cryptographic digital signature embedded within the PKCS#10 CSR.

### 1.2 Invariant SEC-INV-2: CA Private Key Confinement
- The Issuing CA private signing key is generated directly inside the PKCS#11 hardware token boundary.
- The key is marked with attributes:
  - `CKA_EXTRACTABLE = CK_FALSE` (Cannot be wrapped or exported in plaintext)
  - `CKA_SENSITIVE = CK_TRUE` (Key value cannot be revealed via query)
  - `CKA_PRIVATE = CK_TRUE` (Requires authenticated user PIN to access)
- At no point during execution do the CA private key bytes enter host CPU registers, JVM heap memory, application logs, or database rows.
- The application holds only an opaque `CK_OBJECT_HANDLE` handle.

### 1.3 Invariant SEC-INV-3: SoftHSM2 Boundary Realism
- SoftHSM2 is utilized exclusively as a **functional and API-level emulator** for development, local testing, and CI pipelines.
- SoftHSM2 stores token data on the host filesystem and **does NOT provide physical tamper resistance, side-channel attack protection, or FIPS 140-2/3 physical certification**.
- The software architecture must treat SoftHSM2 as functionally identical to a hardware HSM via PKCS#11 while clearly designating production hardware HSM deployment as mandatory for real-world enterprise trust.

### 1.4 Invariant SEC-INV-4: Controlled Test Anchor
- All testing, demonstrations, and development environments shall utilize an internally generated, isolated Test Root CA and Test Issuing CA.
- Under no circumstances shall real-world production CA keys, public trust anchors, or commercial certificates be utilized in development environments.

---

## 2. Asset Identification & Classification

| Asset ID | Asset Name | Description | Sensitivity | Primary Threat |
| :--- | :--- | :--- | :--- | :--- |
| **AST-01** | **CA Private Key** | Asymmetric private key used to sign certificates and CRLs. | **CRITICAL** | Unauthorized extraction, misuse for rogue signing. |
| **AST-02** | **HSM PIN / Token Secret** | Authentication credentials granting access to PKCS#11 token. | **CRITICAL** | Exposure in configuration files, memory dumps, or git. |
| **AST-03** | **Audit Trail** | Append-only repository of security and lifecycle events. | **HIGH** | Out-of-band tampering, deletion to conceal malicious actions. |
| **AST-04** | **Database Records** | Certificates, requests, revocations, and user credentials. | **HIGH** | SQL injection, unauthorized modification of revocation status. |
| **AST-05** | **User Credentials** | User passwords, JWT secrets, and refresh tokens. | **HIGH** | Credential stuffing, brute-force theft, replay attacks. |
| **AST-06** | **Published CRL Files** | Signed public revocation lists served via HTTP. | **MEDIUM** | Denial of service, DNS spoofing, cache poisoning. |

---

## 3. STRIDE Threat Analysis Matrix

The system architecture was evaluated using the **STRIDE** methodology (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege):

| Threat ID | STRIDE Category | Target Asset | Threat Scenario Description | Inherent Risk | Mitigating Architecture Controls | Residual Risk | Verification Test ID |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **THR-01** | **Spoofing** | End Entity CSR | Attacker submits a CSR with a public key belonging to another entity (Public Key Substitution Attack). | **HIGH** | Mandatory proof-of-possession signature verification on CSR before acceptance. System verifies applicant holds matching private key. | Low | `TEST-SEC-001` |
| **THR-02** | **Elevation of Privilege** | RA Approval | Malicious user submits an enrollment request and approves it using their own account. | **HIGH** | Separation of Duties enforcement: `request.requester_id != authenticated_user.id`. Self-approval attempts return HTTP 403 Forbidden. | Very Low | `TEST-SEC-002` |
| **THR-03** | **Information Disclosure** | CA Private Key | Application vulnerability (e.g. heap dump, log exposure) leaks CA private key. | **CRITICAL** | Key is generated inside HSM with `CKA_EXTRACTABLE=false`. Java code only receives opaque PKCS#11 handle. Database schema has zero private key fields. | Negligible | `TEST-SEC-003` |
| **THR-04** | **Tampering** | Issued Certificates | Attacker alters certificate attributes (e.g. Subject DN, expiration) directly in the database. | **HIGH** | Relying parties independently verify the HSM cryptographic signature on the certificate. Database edits break the signature, rendering the certificate untrusted. | Very Low | `TEST-SEC-004` |
| **THR-05** | **Repudiation** | RA Operator Decisions | Rogue operator approves an unauthorized certificate and denies having done so. | **HIGH** | Every review action records reviewer user ID, role, client IP, timestamp, and review comment into immutable audit log with chained HMAC. | Very Low | `TEST-SEC-005` |
| **THR-06** | **Tampering** | Audit Trail | Rogue DBA modifies `audit_events` table to remove traces of unauthorized activities. | **HIGH** | Each audit row embeds `HMAC-SHA256(prev_hash + row_data, secret)`. Any out-of-band edit breaks the hash chain, detected by Auditor verification tool. | Low | `TEST-SEC-006` |
| **THR-07** | **Denial of Service** | OCSP / CRL Services | Flooding the OCSP responder or CRL endpoint to prevent relying party verification. | **HIGH** | Rate limiting at reverse proxy and API filter; in-memory caching of revocation entries; lightweight binary ASN.1 response encoding. | Medium | `TEST-SEC-007` |
| **THR-08** | **Spoofing** | User Login | Brute-force guessing of administrative passwords. | **HIGH** | BCrypt password hashing (cost factor 12); account lockout after 5 consecutive failures; 15-minute token expiry. | Low | `TEST-SEC-008` |
| **THR-09** | **Information Disclosure** | CSR / Token Transit | Interception of CSRs or JWT bearer tokens over unencrypted network traffic. | **HIGH** | Mandatory TLS 1.3 / TLS 1.2 with forward secrecy; HSTS header enabled; secure SameSite cookies. | Very Low | `TEST-SEC-009` |
| **THR-10** | **Tampering** | Serial Numbers | Attacker predicts certificate serial numbers to execute hash collision attacks (e.g., MD5/SHA1-style chosen-prefix collision). | **HIGH** | Serial numbers generated using `SecureRandom` with a minimum of 64 bits of unpredictable entropy as mandated by RFC 5280 and CA/B Forum. | Very Low | `TEST-SEC-010` |
| **THR-11** | **Elevation of Privilege** | Certificate Profile | Requester crafts CSR requesting `cA=true` or arbitrary EKUs to become an intermediate CA. | **CRITICAL** | Profile engine overwrites requested extensions; `BasicConstraints(cA=false)` strictly enforced on all end entity certificates. | Negligible | `TEST-SEC-011` |
| **THR-12** | **Denial of Service** | HSM Session Drop | Transient HSM failure causes application thread starvation. | **MEDIUM** | Circuit breaker pattern isolates HSM calls; thread pool timeout prevents connection exhaustion; auto-reconnect heartbeat. | Low | `TEST-SEC-012` |

---

## 4. Specialized PKI Vulnerability Mitigations

### 4.1 Rogue CA & Extension Injection Protection
- **Vulnerability:** An applicant embeds custom X.509 extensions inside the PKCS#10 CSR (e.g., `BasicConstraints: cA=TRUE, pathLenConstraint=0` or `ExtendedKeyUsage: anyExtendedKeyUsage`).
- **Mitigation:** The CA signing engine **strictly ignores** raw unapproved extensions in the CSR. The `CaIssuanceService` builds the final certificate extensions exclusively from the authorized `CertificateProfile` assigned by the RA Operator.

### 4.2 Serial Number Predictability & Entropy Requirements
- **Vulnerability:** Predictable sequential serial numbers enable chosen-prefix collision attacks against hash functions.
- **Mitigation:**
  - Length: Up to 20 octets (160 bits) positive big integer.
  - Entropy: Generated via `java.security.SecureRandom` seeded from the OS entropy pool (`/dev/urandom`).
  - Format: Prefix byte `0x01` (guarantees positive integer) + 15 bytes (120 bits) of pure CSPRNG entropy.

### 4.3 CRL Staleness & Replay Protection
- **Vulnerability:** Attackers intercept or replay expired CRLs to mask recently revoked certificates.
- **Mitigation:**
  - Strict publication schedule: CRL generated every 24 hours with a `nextUpdate` window of 72 hours.
  - Relying party verification engine rejects any CRL where `currentTime > nextUpdate` or `currentTime < thisUpdate`.

### 4.4 Public Key Reuse & Compromise Defense
- **Vulnerability:** An applicant attempts to obtain a new certificate for a public key known to be compromised or belonging to an active revoked certificate.
- **Mitigation:**
  - The system computes the SHA-256 fingerprint of the `SubjectPublicKeyInfo`.
  - The enrollment engine checks if this public key fingerprint exists in `revocation_records` with reason `keyCompromise`. If found, the request is permanently rejected.

---

## 5. Security Architecture Policy Checklist

- [x] **Zero Private Key Leakage:** CA private keys non-exportable from HSM; End Entity private keys never touch the CA.
- [x] **Separation of Duties:** No self-approval of enrollment requests; dual control for high-assurance profiles.
- [x] **Tamper-Evident Auditing:** Chained HMAC-SHA256 audit logging with read-only database grants.
- [x] **Rate Limiting:** IP and user token-bucket rate limiting on authentication and enrollment.
- [x] **Input Validation:** Strict parsing of ASN.1 CSRs and Bean Validation on all REST payloads.
- [x] **Secure Randomness:** `SecureRandom` utilized for serial numbers, salts, and tokens.
- [x] **Modern Cryptography:** RSA >= 2048-bit, ECDSA P-256/P-384, SHA-256/SHA-384 signatures only. Weak algorithms (MD5, SHA-1, DES) blocked.
- [x] **Controlled Test Anchor:** SoftHSM2 and local Test Root CA strictly isolated from production trust.
