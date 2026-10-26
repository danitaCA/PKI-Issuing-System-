# REST API Contracts & Protocol Specifications
## PKI Issuing Certificate Authority System

- **Document ID:** DOC-API-007
- **Phase:** Phase 1 — Requirements Analysis, System Design, Architecture, and Development Planning
- **Status:** Approved / API Specification Baseline
- **Version:** 1.0.0
- **Classification:** Highly Confidential / Engineering Specification

---

## 1. Global API Standards & Conventions

### 1.1 Base URL & Versioning
- Standard REST API base path: `/api/v1`
- Specialized binary protocol paths: `/api/v1/ocsp` (RFC 6960) and `/api/v1/crl/download` (RFC 5280)
- System observability path: `/actuator`

### 1.2 Content Types & Encodings
- Standard REST Request/Response: `application/json;charset=UTF-8`
- Certificate / CSR downloads: `application/x-pem-file`, `application/pkix-cert`, or `application/x-pkcs7-certificates`
- OCSP Transactions: `application/ocsp-request` and `application/ocsp-response`
- CRL Downloads: `application/pkix-crl` or `application/x-pem-file`

### 1.3 Standard Error Format (RFC 7807 Problem Details)
All 4xx and 5xx responses return a consistent RFC 7807 JSON structure:
```json
{
  "type": "https://pki.internal.corp/errors/INVALID_CSR_SIGNATURE",
  "title": "Invalid CSR Signature",
  "status": 400,
  "detail": "The proof-of-possession signature verification on the submitted PKCS#10 CSR failed.",
  "instance": "/api/v1/enrollment/requests",
  "timestamp": "2026-10-20T12:00:00Z",
  "errorCode": "PKI-40012",
  "invalidParams": [
    {
      "field": "csrPem",
      "reason": "Cryptographic signature does not match embedded public key"
    }
  ]
}
```

### 1.4 Standard Pagination & Sorting Schema
Paginated `GET` endpoints accept query parameters:
- `page`: 0-indexed integer (default `0`)
- `size`: integer between 1 and 100 (default `20`)
- `sort`: string format `fieldName,asc|desc` (default `id,desc`)

Standard Paginated Response Envelope:
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1420,
  "totalPages": 71,
  "isLast": false
}
```

---

## 2. Authentication & Session Endpoints

### 2.1 Authenticate & Obtain Tokens
- **Method / Path:** `POST /api/v1/auth/login`
- **Purpose:** Authenticate user credentials and issue short-lived JWT access token and refresh token.
- **Authorization:** Permitted to all (Rate-limited: 5 requests/min per IP).
- **Request Body:**
```json
{
  "username": "ra_operator_1",
  "password": "SecurePassword123!"
}
```
- **Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "refreshToken": "7f8b9a1c-3d2e-4f5a-8b9c-0d1e2f3a4b5c",
  "user": {
    "id": 12,
    "username": "ra_operator_1",
    "fullName": "Jane Doe",
    "roles": ["ROLE_RA_OPERATOR"]
  }
}
```
- **Errors:** `401 Unauthorized` (Invalid credentials), `423 Locked` (Account temporarily locked).
- **Audit Event:** `AUTH_LOGIN_SUCCESS` or `AUTH_LOGIN_FAILURE`.

### 2.2 Refresh Access Token
- **Method / Path:** `POST /api/v1/auth/refresh`
- **Purpose:** Exchange a valid refresh token for a fresh 15-minute access token.
- **Authorization:** Permitted to all.
- **Request Body:**
```json
{
  "refreshToken": "7f8b9a1c-3d2e-4f5a-8b9c-0d1e2f3a4b5c"
}
```
- **Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```
- **Audit Event:** `AUTH_TOKEN_REFRESH`.

### 2.3 User Logout
- **Method / Path:** `POST /api/v1/auth/logout`
- **Purpose:** Invalidate active refresh tokens and terminate session.
- **Authorization:** Authenticated user.
- **Response `204 No Content`**
- **Audit Event:** `AUTH_LOGOUT`.

### 2.4 Current User Context
- **Method / Path:** `GET /api/v1/auth/me`
- **Purpose:** Returns authenticated identity, roles, and allowed permissions.
- **Authorization:** Authenticated user.
- **Response `200 OK`**

---

## 3. CA Management Endpoints

### 3.1 Get CA Status & Metadata
- **Method / Path:** `GET /api/v1/ca`
- **Purpose:** Retrieve operational status, validity, serial, and algorithms of the Issuing CA.
- **Authorization:** Authenticated user.
- **Response `200 OK`:**
```json
{
  "id": 1,
  "caName": "Enterprise Issuing CA 01",
  "status": "ACTIVE",
  "subjectDn": "CN=Enterprise Issuing CA 01, O=Acme Corp, C=US",
  "issuerDn": "CN=Acme Offline Root CA, O=Acme Corp, C=US",
  "serialNumber": "3a7b8c9d0e1f2a3b4c5d",
  "algorithm": "RSA-4096",
  "hsmSlotId": "0",
  "hsmKeyAlias": "issuing-ca-key-01",
  "notBefore": "2026-01-01T00:00:00Z",
  "notAfter": "2036-01-01T00:00:00Z",
  "crlValidityHours": 72
}
```

### 3.2 Download CA Certificate
- **Method / Path:** `GET /api/v1/ca/certificate`
- **Purpose:** Retrieve public X.509 certificate of the Issuing CA.
- **Authorization:** Permitted to all.
- **Response `200 OK`:** Format: `application/x-pem-file`

### 3.3 Download CA Trust Chain
- **Method / Path:** `GET /api/v1/ca/chain`
- **Purpose:** Retrieve complete PEM bundle containing Issuing CA + Root CA.
- **Authorization:** Permitted to all.
- **Response `200 OK`:** Format: `application/x-pem-file`

### 3.4 Initialize CA Key in HSM
- **Method / Path:** `POST /api/v1/ca/init`
- **Purpose:** Trigger on-token CA asymmetric key generation in HSM and output Subordinate CA CSR.
- **Authorization:** `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "caName": "Enterprise Issuing CA 01",
  "subjectDn": "CN=Enterprise Issuing CA 01, O=Acme Corp, C=US",
  "algorithm": "RSA-4096",
  "hsmSlotId": "0",
  "hsmKeyAlias": "issuing-ca-key-01"
}
```
- **Response `201 Created`:**
```json
{
  "caId": 1,
  "status": "INITIALIZING",
  "caCsrPem": "-----BEGIN CERTIFICATE REQUEST-----\nMIIE4jCCAssCAQAw..."
}
```
- **Audit Event:** `CA_KEY_INITIALIZED` (Severity: HIGH).

### 3.5 Import Root-Signed Intermediate Certificate
- **Method / Path:** `POST /api/v1/ca/import-cert`
- **Purpose:** Import the certificate signed by the Root CA to activate the Issuing CA.
- **Authorization:** `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "caId": 1,
  "certificatePem": "-----BEGIN CERTIFICATE-----\nMIIF...",
  "rootCertificatePem": "-----BEGIN CERTIFICATE-----\nMIIF..."
}
```
- **Response `200 OK`:** Updated CA record with status `ACTIVE`.
- **Audit Event:** `CA_CERT_IMPORTED` (Severity: HIGH).

---

## 4. Certificate Profiles Endpoints

### 4.1 List Active Profiles
- **Method / Path:** `GET /api/v1/profiles`
- **Purpose:** Retrieve profiles available for enrollment requests.
- **Authorization:** Authenticated user.
- **Response `200 OK`:** List of profile objects.

### 4.2 Create or Clone Certificate Profile
- **Method / Path:** `POST /api/v1/profiles`
- **Purpose:** Define a new certificate policy template.
- **Authorization:** `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "profileCode": "INTERNAL_TLS_SERVER",
  "profileName": "Internal TLS Server Certificate",
  "profileType": "SERVER_AUTH",
  "defaultValidityDays": 365,
  "maxValidityDays": 730,
  "allowedKeyTypes": ["RSA-2048", "RSA-3072", "ECDSA-P256"],
  "keyUsages": ["digitalSignature", "keyEncipherment"],
  "extendedKeyUsages": ["1.3.6.1.5.5.7.3.1"],
  "requireSan": true,
  "allowWildcards": false
}
```
- **Response `201 Created`**
- **Audit Event:** `PROFILE_CREATED`.

---

## 5. Enrollment & RA Workflow Endpoints

### 5.1 Submit PKCS#10 CSR Enrollment Request
- **Method / Path:** `POST /api/v1/enrollment/requests`
- **Purpose:** Submit client-generated CSR and request identity attributes for RA approval.
- **Authorization:** `ROLE_END_ENTITY`, `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "profileCode": "TLS_SERVER",
  "validityDays": 365,
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----\nMIICvDCCAaQCAQAwdzELMAkGA1UEBhMCVVMx..."
}
```
- **Response `201 Created`:**
```json
{
  "requestId": 1042,
  "status": "PENDING_REVIEW",
  "subjectDn": "CN=api.internal.corp, O=Acme Corp, C=US",
  "sanEntries": {
    "dns": ["api.internal.corp", "api-backup.internal.corp"],
    "ip": ["10.0.12.5"]
  },
  "keyAlgorithm": "RSA",
  "keyLength": 2048,
  "fingerprint": "8f3b4c1a2e...",
  "submittedAt": "2026-10-20T12:00:00Z"
}
```
- **Validation Rules:**
  - `csrPem`: Valid PEM structure, valid proof-of-possession signature matching embedded public key.
  - Key parameters match assigned `profileCode`.
  - SAN entries conform to profile rules (no unauthorized wildcards).
- **Audit Event:** `ENROLLMENT_SUBMITTED`.

### 5.2 List Pending Enrollment Requests (RA Queue)
- **Method / Path:** `GET /api/v1/enrollment/requests`
- **Purpose:** Query queue of requests with filtering by status, profile, or requester.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`, `ROLE_SECURITY_OFFICER`, `ROLE_AUDITOR`.
- **Query Params:** `status=PENDING_REVIEW&page=0&size=20`
- **Response `200 OK`:** Paginated request summaries.

### 5.3 Get Single Request Details
- **Method / Path:** `GET /api/v1/enrollment/requests/{id}`
- **Purpose:** Inspect CSR attributes, parsed extensions, and approval history.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`, `ROLE_AUDITOR` OR Request Owner.

### 5.4 RA Operator Approve Request
- **Method / Path:** `POST /api/v1/ra/requests/{id}/approve`
- **Purpose:** Record RA Operator human review and approval.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`.
- **Separation of Duties:** Enforces `request.requesterId != currentUserId`.
- **Request Body:**
```json
{
  "comment": "Identity verified against enterprise asset inventory host-1042."
}
```
- **Response `200 OK`:** Request updated to `APPROVED` (or automatically transitioned to `ISSUED` if auto-issuance policy is enabled).
- **Audit Event:** `RA_REQUEST_APPROVED`.

### 5.5 RA Operator Reject Request
- **Method / Path:** `POST /api/v1/ra/requests/{id}/reject`
- **Purpose:** Formally reject request with mandatory logged justification.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "rejectionReason": "INSUFFICIENT_DOMAIN_AUTHORITY",
  "comment": "Requester does not hold authorization for domain api-backup.internal.corp."
}
```
- **Response `200 OK`:** Request status updated to `REJECTED`.
- **Audit Event:** `RA_REQUEST_REJECTED`.

---

## 6. Certificate Issuance & Repository Endpoints

### 6.1 Cryptographic Issuance of Approved Request
- **Method / Path:** `POST /api/v1/ca/requests/{id}/issue`
- **Purpose:** Execute HSM digital signature for an approved request.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`.
- **Response `201 Created`:**
```json
{
  "certificateId": 501,
  "serialNumber": "7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c",
  "subjectDn": "CN=api.internal.corp, O=Acme Corp, C=US",
  "notBefore": "2026-10-20T12:05:00Z",
  "notAfter": "2027-10-20T12:05:00Z",
  "status": "ACTIVE",
  "fingerprintSha256": "3a9f1c7d...",
  "downloadUri": "/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download"
}
```
- **Audit Event:** `CERTIFICATE_ISSUED` (Severity: MEDIUM).

### 6.2 Query Issued Certificates
- **Method / Path:** `GET /api/v1/certificates`
- **Purpose:** Search certificates with filters: `status`, `commonName`, `notAfterBefore`, `serialNumber`.
- **Authorization:** `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`, `ROLE_SECURITY_OFFICER`, `ROLE_AUDITOR`.
- **Response `200 OK`:** Paginated certificates list.

### 6.3 Query My Certificates
- **Method / Path:** `GET /api/v1/certificates/my`
- **Purpose:** View certificates owned by authenticated End Entity.
- **Authorization:** `ROLE_END_ENTITY`.
- **Response `200 OK`:** Paginated certificates list filtered by current user.

### 6.4 Download Certificate Bundle
- **Method / Path:** `GET /api/v1/certificates/{serialNumber}/download`
- **Purpose:** Download X.509 certificate file in desired format.
- **Authorization:** Permitted to all.
- **Query Params:** `format=pem` (default), `format=der`, `format=p7b` (chain)
- **Response `200 OK`:** Binary or text attachment.

---

## 7. Renewal & Revocation Endpoints

### 7.1 Renew Certificate
- **Method / Path:** `POST /api/v1/certificates/{serialNumber}/renew`
- **Purpose:** Submit renewal request for active certificate nearing expiration.
- **Authorization:** `ROLE_END_ENTITY` (if owner), `ROLE_RA_OPERATOR`, `ROLE_CA_ADMIN`.
- **Request Body (Optional new CSR):**
```json
{
  "validityDays": 365,
  "newCsrPem": "-----BEGIN CERTIFICATE REQUEST-----\n..."
}
```
- **Response `201 Created`:** Creates new `EnrollmentRequest` linked to prior certificate.
- **Audit Event:** `CERTIFICATE_RENEWAL_REQUESTED`.

### 7.2 Revoke Certificate
- **Method / Path:** `POST /api/v1/certificates/{serialNumber}/revoke`
- **Purpose:** Immediately revoke active certificate with RFC 5280 reason code.
- **Authorization:** `ROLE_END_ENTITY` (if owner), `ROLE_RA_OPERATOR`, `ROLE_SECURITY_OFFICER`, `ROLE_CA_ADMIN`.
- **Request Body:**
```json
{
  "reasonCode": "keyCompromise",
  "invalidityDate": "2026-10-19T22:30:00Z",
  "comments": "Laptop holding private key was stolen in transit."
}
```
- **Response `200 OK`:**
```json
{
  "serialNumber": "7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c",
  "status": "REVOKED",
  "revocationDate": "2026-10-20T12:15:00Z",
  "reasonCode": "keyCompromise"
}
```
- **Audit Event:** `CERTIFICATE_REVOKED` (Severity: HIGH).

---

## 8. CRL & Verification Endpoints

### 8.1 Download Latest CRL
- **Method / Path:** `GET /api/v1/crl/latest`
- **Purpose:** Retrieve active X.509 v2 CRL signed by CA.
- **Authorization:** Permitted to all.
- **Response `200 OK`:** Content-Type `application/pkix-crl` or `application/x-pem-file`.

### 8.2 Trigger On-Demand CRL Regeneration
- **Method / Path:** `POST /api/v1/crl/generate`
- **Purpose:** Force out-of-band CRL regeneration and publication.
- **Authorization:** `ROLE_CA_ADMIN`, `ROLE_SECURITY_OFFICER`.
- **Response `200 OK`:** Summary of generated CRL (CRL number, entry count, validity window).
- **Audit Event:** `CRL_MANUALLY_GENERATED`.

### 8.3 Verify Certificate Path & Status
- **Method / Path:** `POST /api/v1/validation/verify-cert`
- **Purpose:** Test validation endpoint evaluating chain, validity, and revocation.
- **Authorization:** Permitted to all.
- **Request Body:**
```json
{
  "certificatePem": "-----BEGIN CERTIFICATE-----\n..."
}
```
- **Response `200 OK`:**
```json
{
  "valid": true,
  "status": "GOOD",
  "chainValidated": true,
  "expirationStatus": "ACTIVE",
  "revocationStatus": "NOT_REVOKED",
  "notBefore": "2026-10-20T12:05:00Z",
  "notAfter": "2027-10-20T12:05:00Z"
}
```

---

## 9. RFC 6960 OCSP Responder Protocol Endpoint

- **Method / Paths:**
  - `POST /api/v1/ocsp` (Standard binary OCSP request in body)
  - `GET /api/v1/ocsp/{urlEncodedBase64Request}` (RFC 6960 GET convention)
- **Headers:**
  - Request: `Content-Type: application/ocsp-request`, `Accept: application/ocsp-response`
  - Response: `Content-Type: application/ocsp-response`
- **Status Codes:**
  - `200 OK` with binary `OCSPResponse` payload.
  - `400 Bad Request` if payload is not valid ASN.1 DER `OCSPRequest`.
- **Response Content:** Signed `BasicOCSPResponse` indicating `good`, `revoked` (with revocation date), or `unknown`.

---

## 10. Audit & Reporting Endpoints

### 10.1 Query Audit Trail
- **Method / Path:** `GET /api/v1/audit/events`
- **Purpose:** Paginated search of immutable audit log with filters (`action`, `principal`, `fromDate`, `toDate`, `outcome`).
- **Authorization:** `ROLE_AUDITOR`, `ROLE_SECURITY_OFFICER`.
- **Response `200 OK`:** Paginated array of audit records with HMAC checksums.

### 10.2 Verify Audit Log Hash-Chain
- **Method / Path:** `GET /api/v1/audit/verify-chain`
- **Purpose:** Cryptographically verify the HMAC chain across all or a range of audit records.
- **Authorization:** `ROLE_AUDITOR`.
- **Response `200 OK`:**
```json
{
  "chainIntact": true,
  "recordsVerified": 15420,
  "firstSequence": 1,
  "lastSequence": 15420,
  "verificationTimestamp": "2026-10-20T12:30:00Z"
}
```

### 10.3 Export Audit Report
- **Method / Path:** `GET /api/v1/audit/reports/export`
- **Purpose:** Export audit trail to CSV or JSON file.
- **Authorization:** `ROLE_AUDITOR`.
- **Query Params:** `format=csv&fromDate=2026-01-01&toDate=2026-10-20`
- **Response `200 OK`:** Downloadable report file.

---

## 11. Administration & System Diagnostics Endpoints

### 11.1 User Management
- `GET /api/v1/admin/users`: List users (`ROLE_CA_ADMIN`)
- `POST /api/v1/admin/users`: Create user (`ROLE_CA_ADMIN`)
- `PUT /api/v1/admin/users/{id}`: Update user / assign roles (`ROLE_CA_ADMIN`)
- `POST /api/v1/admin/users/{id}/lock`: Lock user account (`ROLE_CA_ADMIN`)

### 11.2 Trigger Database Backup
- **Method / Path:** `POST /api/v1/admin/backups/create`
- **Purpose:** Trigger encrypted database dump.
- **Authorization:** `ROLE_CA_ADMIN`.
- **Response `202 Accepted`:** Backup initiated with tracking ID.
- **Audit Event:** `DATABASE_BACKUP_INITIATED`.

### 11.3 System Diagnostics & Health
- `GET /actuator/health`: Liveness, readiness, MySQL connection, and SoftHSM/HSM slot responsive status.
- `GET /api/v1/system/hsm/status`: Detailed token slot, manufacturer, and key alias status (`ROLE_CA_ADMIN`, `ROLE_SECURITY_OFFICER`).
