package com.pki.ca.service;

import com.pki.ca.crypto.CryptoProvider;
import com.pki.ca.crypto.SerialNumberGenerator;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.entity.*;
import com.pki.ca.enums.ApprovalAction;
import com.pki.ca.enums.CertificateStatus;
import com.pki.ca.enums.IssuanceAttemptStatus;
import com.pki.ca.enums.RequestStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.HsmOperationException;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.*;
import com.pki.ca.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates secure, audit-compliant, HSM-backed certificate issuance.
 *
 * Security Invariants Enforced:
 * 1. Only persisted requests in state APPROVED can be issued.
 * 2. Separation of Duties (SoD) and dual-approval requirements revalidated authoritatively.
 * 3. Certificate profile constraints re-evaluated at issuance time.
 * 4. HSM signing operations tracked via durable IssuanceAttempt state machine.
 * 5. Idempotent execution: Duplicate requests return existing certificate without duplicate HSM signing.
 * 6. Partial failure recovery: Signed certificates before DB commits are reconciled safely.
 */
@Service
public class CaIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(CaIssuanceService.class);

    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final CertificateRepository certificateRepository;
    private final IssuanceAttemptRepository issuanceAttemptRepository;
    private final UserRepository userRepository;
    private final CaAuthorityService caAuthorityService;
    private final CertificateBuilderService certificateBuilderService;
    private final SerialNumberGenerator serialNumberGenerator;
    private final CryptoProvider cryptoProvider;
    private final CsrParserService csrParserService;
    private final ProfileValidationService profileValidationService;

    public CaIssuanceService(EnrollmentRequestRepository enrollmentRequestRepository,
                             CertificateRepository certificateRepository,
                             IssuanceAttemptRepository issuanceAttemptRepository,
                             UserRepository userRepository,
                             CaAuthorityService caAuthorityService,
                             CertificateBuilderService certificateBuilderService,
                             SerialNumberGenerator serialNumberGenerator,
                             CryptoProvider cryptoProvider,
                             CsrParserService csrParserService,
                             ProfileValidationService profileValidationService) {
        this.enrollmentRequestRepository = enrollmentRequestRepository;
        this.certificateRepository = certificateRepository;
        this.issuanceAttemptRepository = issuanceAttemptRepository;
        this.userRepository = userRepository;
        this.caAuthorityService = caAuthorityService;
        this.certificateBuilderService = certificateBuilderService;
        this.serialNumberGenerator = serialNumberGenerator;
        this.cryptoProvider = cryptoProvider;
        this.csrParserService = csrParserService;
        this.profileValidationService = profileValidationService;
    }

    /**
     * Executes authorized certificate issuance for an approved enrollment request.
     *
     * @param requestId        The enrollment request identifier.
     * @param issuerPrincipal  The authenticated operator initiating the issuance.
     * @param clientIp         Client network IP address for audit trail.
     * @return Full details of the newly issued certificate.
     */
    @Transactional
    public CertificateResponse issueCertificate(Long requestId, UserPrincipal issuerPrincipal, String clientIp) {
        log.info("Certificate issuance requested for Request ID: {} by Operator: '{}' (id: {}) from IP: {}",
                requestId, issuerPrincipal.getUsername(), issuerPrincipal.getId(), clientIp);

        // 1. Role Authorization Check
        boolean isAuthorized = issuerPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RA_OPERATOR") || a.getAuthority().equals("ROLE_CA_ADMIN"));
        if (!isAuthorized) {
            log.warn("AUDIT [ACCESS_DENIED]: User '{}' lacking ROLE_RA_OPERATOR/ROLE_CA_ADMIN attempted issuance on Request ID {}",
                    issuerPrincipal.getUsername(), requestId);
            throw new AccessDeniedException("Certificate issuance requires ROLE_RA_OPERATOR or ROLE_CA_ADMIN.");
        }

        User issuerUser = userRepository.findById(issuerPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Issuer user not found: " + issuerPrincipal.getId()));

        // 2. Load authoritative persisted request
        EnrollmentRequest request = enrollmentRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment request not found with ID: " + requestId));

        // 3. Idempotency Check: If already issued, return existing certificate record safely
        Optional<Certificate> existingCert = certificateRepository.findByEnrollmentRequestId(requestId);
        if (existingCert.isPresent()) {
            log.info("AUDIT [CERTIFICATE_ISSUED_IDEMPOTENT]: Request ID {} already issued with Serial: {}. Returning existing certificate.",
                    requestId, existingCert.get().getSerialNumber());
            if (request.getStatus() != RequestStatus.ISSUED) {
                request.setStatus(RequestStatus.ISSUED);
                enrollmentRequestRepository.save(request);
            }
            return mapToResponse(existingCert.get());
        }

        // 4. Request State Validation
        if (request.getStatus() != RequestStatus.APPROVED) {
            if (request.getStatus() == RequestStatus.PENDING_REVIEW || request.getStatus() == RequestStatus.PENDING_SECOND_APPROVAL) {
                log.warn("AUDIT [SECURITY_VIOLATION]: Attempt to issue unapproved request ID {} in status {}",
                        requestId, request.getStatus());
                throw new BadRequestException("Cannot issue certificate: Request ID " + requestId + " is not approved (current status: " + request.getStatus() + ").", "PKI-40031");
            } else if (request.getStatus() == RequestStatus.REJECTED || request.getStatus() == RequestStatus.CANCELLED) {
                throw new BadRequestException("Cannot issue certificate: Request ID " + requestId + " was " + request.getStatus() + ".", "PKI-40032");
            } else if (request.getStatus() == RequestStatus.ISSUED) {
                // Should have been caught by existingCert check, but guard defensively
                throw new BadRequestException("Certificate already issued for request ID: " + requestId, "PKI-40037");
            } else {
                throw new BadRequestException("Invalid request state for issuance: " + request.getStatus(), "PKI-40031");
            }
        }

        // 5. Authoritative Separation of Duties (SoD) & Approval History Revalidation
        revalidateApprovalHistory(request);

        // 6. Certificate Profile Revalidation at Issuance Time
        CertificateProfile profile = request.getProfile();
        if (profile == null || !profile.isActive()) {
            log.error("AUDIT [SECURITY_VIOLATION]: Request ID {} references inactive or missing profile", requestId);
            throw new BadRequestException("Certificate profile is no longer active. Approval invalidated.", "PKI-40036");
        }
        com.pki.ca.dto.internal.ParsedCsrDto parsedCsr = csrParserService.parseAndVerifyCsr(request.getCsrPem());
        profileValidationService.validateCsrAgainstProfile(profile, parsedCsr, request.getValidityDays());

        // 7. CA Readiness & Token Key Availability Check
        CaAuthority ca = caAuthorityService.getActiveCa();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(ca.getNotBefore()) || now.isAfter(ca.getNotAfter())) {
            throw new HsmOperationException("Active Issuing CA certificate is not currently valid (expired or not yet valid).", "PKI-50010");
        }
        if (!cryptoProvider.keyExists(ca.getHsmKeyAlias())) {
            throw new HsmOperationException("CA signing key for alias '" + ca.getHsmKeyAlias() + "' not available in HSM.", "PKI-50003");
        }

        // 8. Reconciliation of Interrupted Prior Attempts (Failure Recovery)
        Optional<IssuanceAttempt> priorAttempt = issuanceAttemptRepository.findTopByEnrollmentRequestIdOrderByCreatedAtDesc(requestId);
        if (priorAttempt.isPresent() && priorAttempt.get().getStatus() == IssuanceAttemptStatus.SIGNED) {
            log.info("Reconciling previous SIGNED attempt for request ID: {}. Persisting without re-signing.", requestId);
            return reconcileSignedAttempt(priorAttempt.get(), request, ca, issuerUser, clientIp);
        }

        // 9. Allocate Unique 128-bit Random Serial Number with Collision Retry Semantics
        String serialHex = allocateUniqueSerialNumber();
        BigInteger serialBigInt = new BigInteger(serialHex, 16);

        // 10. Durable Attempt Reservation
        IssuanceAttempt attempt = new IssuanceAttempt();
        attempt.setEnrollmentRequest(request);
        attempt.setCaAuthority(ca);
        attempt.setAllocatedSerialNumber(serialHex);
        attempt.setStatus(IssuanceAttemptStatus.RESERVED);
        attempt.setInitiatedBy(issuerUser);
        IssuanceAttempt savedAttempt = issuanceAttemptRepository.saveAndFlush(attempt);

        // 11. HSM Signature & Certificate Construction
        savedAttempt.setStatus(IssuanceAttemptStatus.SIGNING);
        savedAttempt = issuanceAttemptRepository.saveAndFlush(savedAttempt);

        X509Certificate x509Cert;
        String certPem;
        try {
            x509Cert = certificateBuilderService.buildAndSignCertificate(request, ca, serialBigInt);
            certPem = certificateBuilderService.convertToPem(x509Cert);

            savedAttempt.setSignedCertificatePem(certPem);
            savedAttempt.setStatus(IssuanceAttemptStatus.SIGNED);
            savedAttempt = issuanceAttemptRepository.saveAndFlush(savedAttempt);
        } catch (Exception ex) {
            savedAttempt.setStatus(IssuanceAttemptStatus.FAILED);
            savedAttempt.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : "Cryptographic signing failure");
            issuanceAttemptRepository.saveAndFlush(savedAttempt);
            log.error("AUDIT [CERTIFICATE_ISSUANCE_FAILED]: Request ID {}, Serial {}, Error: {}", requestId, serialHex, ex.getMessage());
            throw ex;
        }

        // 12. Persist Certificate Record & Update Request State
        try {
            byte[] certBytes = x509Cert.getEncoded();
            String fingerprintSha256 = certificateBuilderService.computeSha256Fingerprint(certBytes);
            String publicKeyPem = certificateBuilderService.convertToPem(x509Cert.getPublicKey());

            Certificate certificate = new Certificate();
            certificate.setCaAuthority(ca);
            certificate.setEnrollmentRequest(request);
            certificate.setSerialNumber(serialHex);
            certificate.setSubjectDn(x509Cert.getSubjectX500Principal().getName());
            certificate.setIssuerDn(x509Cert.getIssuerX500Principal().getName());
            certificate.setSanEntries(request.getSanEntries());
            certificate.setNotBefore(LocalDateTime.ofInstant(x509Cert.getNotBefore().toInstant(), ZoneOffset.UTC));
            certificate.setNotAfter(LocalDateTime.ofInstant(x509Cert.getNotAfter().toInstant(), ZoneOffset.UTC));
            certificate.setCertificatePem(certPem);
            certificate.setPublicKeyPem(publicKeyPem);
            certificate.setPublicKeyFingerprint(request.getPublicKeyFingerprint());
            certificate.setFingerprintSha256(fingerprintSha256);
            certificate.setStatus(CertificateStatus.ACTIVE);

            Certificate savedCertificate = certificateRepository.save(certificate);

            request.setStatus(RequestStatus.ISSUED);
            enrollmentRequestRepository.save(request);

            savedAttempt.setStatus(IssuanceAttemptStatus.COMPLETED);
            issuanceAttemptRepository.save(savedAttempt);

            log.info("AUDIT [CERTIFICATE_ISSUED]: Cert ID: {}, Serial: '{}', Request ID: {}, Subject: '{}', Issuer: '{}', Fingerprint: '{}', IssuedBy: '{}' (id: {}), Client IP: {}",
                    savedCertificate.getId(), savedCertificate.getSerialNumber(), request.getId(),
                    savedCertificate.getSubjectDn(), savedCertificate.getIssuerDn(), savedCertificate.getFingerprintSha256(),
                    issuerUser.getUsername(), issuerUser.getId(), clientIp);

            return mapToResponse(savedCertificate);

        } catch (Exception ex) {
            log.error("CRITICAL: Failed to persist certificate after successful HSM signing! Request ID: {}, Serial: {}",
                    requestId, serialHex, ex);
            // Attempt remains in SIGNED status for safe reconciliation on retry
            throw new HsmOperationException("Certificate was cryptographically signed on HSM but database persistence failed: "
                    + ex.getMessage(), ex, "PKI-50011");
        }
    }

    /**
     * Reconciles an attempt that reached SIGNED status before an application or database interruption.
     */
    private CertificateResponse reconcileSignedAttempt(IssuanceAttempt attempt, EnrollmentRequest request,
                                                       CaAuthority ca, User issuerUser, String clientIp) {
        try {
            String certPem = attempt.getSignedCertificatePem();
            byte[] pemBytes = certPem.getBytes();
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509", "BC");
            X509Certificate x509Cert = (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(pemBytes));

            Certificate savedCertificate;
            Optional<Certificate> existing = certificateRepository.findByEnrollmentRequestId(request.getId());
            if (existing.isPresent()) {
                savedCertificate = existing.get();
            } else {
                byte[] certBytes = x509Cert.getEncoded();
                String fingerprintSha256 = certificateBuilderService.computeSha256Fingerprint(certBytes);
                String publicKeyPem = certificateBuilderService.convertToPem(x509Cert.getPublicKey());

                Certificate certificate = new Certificate();
                certificate.setCaAuthority(ca);
                certificate.setEnrollmentRequest(request);
                certificate.setSerialNumber(attempt.getAllocatedSerialNumber());
                certificate.setSubjectDn(x509Cert.getSubjectX500Principal().getName());
                certificate.setIssuerDn(x509Cert.getIssuerX500Principal().getName());
                certificate.setSanEntries(request.getSanEntries());
                certificate.setNotBefore(LocalDateTime.ofInstant(x509Cert.getNotBefore().toInstant(), ZoneOffset.UTC));
                certificate.setNotAfter(LocalDateTime.ofInstant(x509Cert.getNotAfter().toInstant(), ZoneOffset.UTC));
                certificate.setCertificatePem(certPem);
                certificate.setPublicKeyPem(publicKeyPem);
                certificate.setPublicKeyFingerprint(request.getPublicKeyFingerprint());
                certificate.setFingerprintSha256(fingerprintSha256);
                certificate.setStatus(CertificateStatus.ACTIVE);

                savedCertificate = certificateRepository.save(certificate);
            }

            if (request.getStatus() != RequestStatus.ISSUED) {
                request.setStatus(RequestStatus.ISSUED);
                enrollmentRequestRepository.save(request);
            }

            attempt.setStatus(IssuanceAttemptStatus.COMPLETED);
            issuanceAttemptRepository.save(attempt);

            log.info("AUDIT [CERTIFICATE_RECONCILED]: Cert ID: {}, Serial: '{}', Request ID: {}, Client IP: {}",
                    savedCertificate.getId(), savedCertificate.getSerialNumber(), request.getId(), clientIp);

            return mapToResponse(savedCertificate);
        } catch (Exception ex) {
            log.error("Failed to reconcile signed attempt ID: {}", attempt.getId(), ex);
            throw new HsmOperationException("Reconciliation failure for signed certificate: " + ex.getMessage(), ex, "PKI-50011");
        }
    }

    /**
     * Enforces Separation of Duties (SoD) and checks that required approval signatures are present.
     */
    private void revalidateApprovalHistory(EnrollmentRequest request) {
        List<RequestApproval> approvals = request.getApprovals();
        if (approvals == null || approvals.isEmpty()) {
            throw new BadRequestException("Request has no approval records.", "PKI-40038");
        }

        // Verify that requester is not an approver (SoD-01)
        Long requesterId = request.getRequester().getId();
        boolean selfApproved = approvals.stream()
                .anyMatch(a -> a.getAction() == ApprovalAction.APPROVED && Objects.equals(a.getReviewer().getId(), requesterId));
        if (selfApproved) {
            log.error("AUDIT [SECURITY_VIOLATION]: Self-approval detected on request ID {} during issuance recheck", request.getId());
            throw new BadRequestException("Separation of duties violation: Request cannot be approved by requester.", "PKI-40301");
        }

        // If profile requires dual approval, verify two distinct approvers
        CertificateProfile profile = request.getProfile();
        if (profile != null && profile.isRequiresDualApproval()) {
            long approvedCount = approvals.stream()
                    .filter(a -> a.getAction() == ApprovalAction.APPROVED)
                    .map(a -> a.getReviewer().getId())
                    .distinct()
                    .count();
            if (approvedCount < 2) {
                log.error("AUDIT [SECURITY_VIOLATION]: Insufficient distinct approvals for dual-approval request ID {}", request.getId());
                throw new BadRequestException("Profile requires dual approval, but request has only " + approvedCount + " distinct approval(s).", "PKI-40030");
            }
        }
    }

    /**
     * Generates a 128-bit unpredictable random serial number with collision retries.
     */
    private String allocateUniqueSerialNumber() {
        for (int i = 0; i < 3; i++) {
            String candidate = serialNumberGenerator.generateSerialNumberHex();
            boolean collision = certificateRepository.existsBySerialNumber(candidate)
                    || issuanceAttemptRepository.findByAllocatedSerialNumber(candidate).isPresent();
            if (!collision) {
                return candidate;
            }
            log.warn("Serial number collision detected for candidate '{}', retrying...", candidate);
        }
        throw new HsmOperationException("Failed to allocate a unique serial number after 3 attempts.", "PKI-50009");
    }

    private CertificateResponse mapToResponse(Certificate cert) {
        CertificateResponse response = new CertificateResponse();
        response.setCertificateId(cert.getId());
        response.setRequestId(cert.getEnrollmentRequest().getId());
        response.setSerialNumber(cert.getSerialNumber());
        response.setSubjectDn(cert.getSubjectDn());
        response.setIssuerDn(cert.getIssuerDn());
        response.setSanEntries(cert.getSanEntries());
        response.setNotBefore(cert.getNotBefore());
        response.setNotAfter(cert.getNotAfter());
        response.setStatus(cert.getStatus().name());
        response.setFingerprintSha256(cert.getFingerprintSha256());
        response.setDownloadUri("/api/v1/certificates/" + cert.getSerialNumber() + "/download");
        response.setCertificatePem(cert.getCertificatePem());
        response.setCreatedAt(cert.getCreatedAt());
        return response;
    }
}
