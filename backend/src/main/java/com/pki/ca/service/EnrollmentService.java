package com.pki.ca.service;

import com.pki.ca.dto.internal.ParsedCsrDto;
import com.pki.ca.dto.request.ApproveRequestDto;
import com.pki.ca.dto.request.EnrollmentSubmissionRequest;
import com.pki.ca.dto.request.RejectRequestDto;
import com.pki.ca.dto.response.EnrollmentResponse;
import com.pki.ca.dto.response.PagedResponse;
import com.pki.ca.dto.response.RequestApprovalResponse;
import com.pki.ca.dto.response.SanEntriesDto;
import com.pki.ca.entity.CertificateProfile;
import com.pki.ca.entity.EnrollmentRequest;
import com.pki.ca.entity.RequestApproval;
import com.pki.ca.entity.User;
import com.pki.ca.enums.ApprovalAction;
import com.pki.ca.enums.RequestStatus;
import com.pki.ca.enums.ReviewStage;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.ConflictException;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.CertificateProfileRepository;
import com.pki.ca.repository.EnrollmentRequestRepository;
import com.pki.ca.repository.RequestApprovalRepository;
import com.pki.ca.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private static final Set<RequestStatus> ACTIVE_STATUSES = Set.of(
            RequestStatus.PENDING_REVIEW,
            RequestStatus.PENDING_SECOND_APPROVAL,
            RequestStatus.APPROVED
    );

    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final CertificateProfileRepository profileRepository;
    private final RequestApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final CsrParserService csrParserService;
    private final ProfileValidationService profileValidationService;

    public EnrollmentService(EnrollmentRequestRepository enrollmentRequestRepository,
                             CertificateProfileRepository profileRepository,
                             RequestApprovalRepository approvalRepository,
                             UserRepository userRepository,
                             CsrParserService csrParserService,
                             ProfileValidationService profileValidationService) {
        this.enrollmentRequestRepository = enrollmentRequestRepository;
        this.profileRepository = profileRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.csrParserService = csrParserService;
        this.profileValidationService = profileValidationService;
    }

    /**
     * Submits a PKCS#10 CSR enrollment request for an authenticated End Entity.
     */
    @Transactional
    public EnrollmentResponse submitEnrollmentRequest(EnrollmentSubmissionRequest request, Long requesterId, String clientIp) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", requesterId));

        CertificateProfile profile = profileRepository.findByProfileCode(request.getProfileCode().trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("CertificateProfile", "code", request.getProfileCode()));

        // Cryptographically parse CSR and verify Proof of Possession (PoP)
        ParsedCsrDto parsedCsr = csrParserService.parseAndVerifyCsr(request.getCsrPem());

        // Enforce duplicate submission protection (idempotency check)
        boolean hasDuplicate = enrollmentRequestRepository.existsByRequesterIdAndPublicKeyFingerprintAndStatusIn(
                requesterId,
                parsedCsr.getPublicKeyFingerprint(),
                ACTIVE_STATUSES
        );
        if (hasDuplicate) {
            log.warn("Rejected duplicate enrollment submission from requester '{}' for public key fingerprint '{}'",
                    requester.getUsername(), parsedCsr.getPublicKeyFingerprint());
            throw new ConflictException("An active enrollment request already exists for this public key fingerprint: " + parsedCsr.getPublicKeyFingerprint(), "PKI-40901");
        }

        // Validate CSR attributes against Certificate Profile policies
        int effectiveValidityDays = profileValidationService.validateCsrAgainstProfile(profile, parsedCsr, request.getValidityDays());

        // Persist enrollment request
        EnrollmentRequest enrollment = new EnrollmentRequest();
        enrollment.setRequester(requester);
        enrollment.setProfile(profile);
        enrollment.setStatus(RequestStatus.PENDING_REVIEW);
        enrollment.setSubjectDn(parsedCsr.getSubjectDn());
        enrollment.setSanEntries(parsedCsr.getSanEntries());
        enrollment.setCsrPem(request.getCsrPem().trim());
        enrollment.setPublicKeyAlgorithm(parsedCsr.getPublicKeyAlgorithm());
        enrollment.setKeyLength(parsedCsr.getKeyLength());
        enrollment.setPublicKeyFingerprint(parsedCsr.getPublicKeyFingerprint());
        enrollment.setValidityDays(effectiveValidityDays);

        EnrollmentRequest saved = enrollmentRequestRepository.save(enrollment);

        log.info("AUDIT [ENROLLMENT_SUBMITTED]: Request ID {}, Requester '{}' (id: {}), Profile '{}', Algorithm '{}-{}', Fingerprint '{}', Client IP: {}",
                saved.getId(), requester.getUsername(), requester.getId(), profile.getProfileCode(),
                saved.getPublicKeyAlgorithm(), saved.getKeyLength(), saved.getPublicKeyFingerprint(), clientIp);

        return mapToResponse(saved);
    }

    /**
     * Lists enrollment requests for RA operators with optional status filtering.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EnrollmentResponse> getRequestsForQueue(RequestStatus status, Pageable pageable) {
        Page<EnrollmentRequest> page;
        if (status != null) {
            page = enrollmentRequestRepository.findByStatus(status, pageable);
        } else {
            page = enrollmentRequestRepository.findAll(pageable);
        }
        List<EnrollmentResponse> responses = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return new PagedResponse<>(responses, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Lists enrollment requests owned by the authenticated End Entity.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EnrollmentResponse> getMyRequests(Long requesterId, Pageable pageable) {
        Page<EnrollmentRequest> page = enrollmentRequestRepository.findByRequesterId(requesterId, pageable);
        List<EnrollmentResponse> responses = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return new PagedResponse<>(responses, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Retrieves single enrollment request details with strict ownership and authorization checks.
     */
    @Transactional(readOnly = true)
    public EnrollmentResponse getRequestById(Long requestId, Long currentUserId, boolean isPrivilegedStaff) {
        EnrollmentRequest request = enrollmentRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("EnrollmentRequest", "id", requestId));

        if (!isPrivilegedStaff && !request.getRequester().getId().equals(currentUserId)) {
            log.warn("AUDIT [ACCESS_DENIED]: User id {} attempted to view unowned request ID {}", currentUserId, requestId);
            throw new AccessDeniedException("Access denied: You do not have permission to view this enrollment request.");
        }

        return mapToResponse(request);
    }

    /**
     * Formally approves an enrollment request adhering to Separation of Duties and Dual-Approval policy.
     */
    @Transactional
    public EnrollmentResponse approveRequest(Long requestId, Long reviewerId, ApproveRequestDto dto, String clientIp, Collection<String> reviewerRoles) {
        EnrollmentRequest request = enrollmentRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("EnrollmentRequest", "id", requestId));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewerId));

        // Enforce Rule SoD-01: No Self-Approval
        if (request.getRequester().getId().equals(reviewerId)) {
            log.warn("AUDIT [SECURITY_VIOLATION]: Self-approval attempt on Request ID {} by User '{}' (id: {})",
                    requestId, reviewer.getUsername(), reviewerId);
            throw new AccessDeniedException("Self-approval prohibited: Requester cannot approve their own enrollment request.");
        }

        String comment = dto != null ? dto.getComment() : null;

        // Evaluate State Transitions and Dual-Approval logic
        if (request.getStatus() == RequestStatus.PENDING_REVIEW) {
            if (request.getProfile().isRequiresDualApproval()) {
                // Stage 1 of Dual Approval (Requires RA Operator or CA Admin)
                request.setStatus(RequestStatus.PENDING_SECOND_APPROVAL);

                RequestApproval approval = new RequestApproval();
                approval.setRequest(request);
                approval.setReviewer(reviewer);
                approval.setAction(ApprovalAction.APPROVED);
                approval.setReviewStage(ReviewStage.INITIAL_REVIEW);
                approval.setReviewerComment(comment);
                approval.setClientIp(clientIp);
                request.addApproval(approval);

                EnrollmentRequest updated = enrollmentRequestRepository.save(request);

                log.info("AUDIT [RA_REQUEST_APPROVED]: Request ID {}, Reviewer '{}' (id: {}), Stage: INITIAL_REVIEW, Result: PENDING_SECOND_APPROVAL, Comment: '{}', Client IP: {}",
                        updated.getId(), reviewer.getUsername(), reviewerId, comment, clientIp);
                return mapToResponse(updated);
            } else {
                // Standard Single-Approval Profile
                request.setStatus(RequestStatus.APPROVED);

                RequestApproval approval = new RequestApproval();
                approval.setRequest(request);
                approval.setReviewer(reviewer);
                approval.setAction(ApprovalAction.APPROVED);
                approval.setReviewStage(ReviewStage.INITIAL_REVIEW);
                approval.setReviewerComment(comment);
                approval.setClientIp(clientIp);
                request.addApproval(approval);

                EnrollmentRequest updated = enrollmentRequestRepository.save(request);

                log.info("AUDIT [RA_REQUEST_APPROVED]: Request ID {}, Reviewer '{}' (id: {}), Stage: INITIAL_REVIEW, Result: APPROVED, Comment: '{}', Client IP: {}",
                        updated.getId(), reviewer.getUsername(), reviewerId, comment, clientIp);
                return mapToResponse(updated);
            }
        } else if (request.getStatus() == RequestStatus.PENDING_SECOND_APPROVAL) {
            // Stage 2 of Dual Approval: Must be reviewed by Security Officer or CA Admin
            boolean isAuthorizedForSecondStage = reviewerRoles.contains("ROLE_SECURITY_OFFICER") || reviewerRoles.contains("ROLE_CA_ADMIN");
            if (!isAuthorizedForSecondStage) {
                log.warn("AUDIT [ACCESS_DENIED]: User '{}' lacking ROLE_SECURITY_OFFICER/ROLE_CA_ADMIN attempted second approval on request ID {}",
                        reviewer.getUsername(), requestId);
                throw new AccessDeniedException("Second approval requires ROLE_SECURITY_OFFICER or ROLE_CA_ADMIN.");
            }

            // Enforce Rule SoD-03: Stage 2 approver must be a different individual than Stage 1 approver
            boolean alreadyApprovedByThisUser = request.getApprovals().stream()
                    .anyMatch(app -> app.getReviewer().getId().equals(reviewerId));
            if (alreadyApprovedByThisUser) {
                log.warn("AUDIT [SECURITY_VIOLATION]: User '{}' (id: {}) attempted second approval on request ID {} which they already approved in Stage 1",
                        reviewer.getUsername(), reviewerId, requestId);
                throw new BadRequestException("Dual-approval violation: The second approver must be distinct from the initial approver.", "PKI-40030");
            }

            request.setStatus(RequestStatus.APPROVED);

            RequestApproval approval = new RequestApproval();
            approval.setRequest(request);
            approval.setReviewer(reviewer);
            approval.setAction(ApprovalAction.APPROVED);
            approval.setReviewStage(ReviewStage.SECOND_APPROVAL);
            approval.setReviewerComment(comment);
            approval.setClientIp(clientIp);
            request.addApproval(approval);

            EnrollmentRequest updated = enrollmentRequestRepository.save(request);

            log.info("AUDIT [RA_REQUEST_APPROVED]: Request ID {}, Reviewer '{}' (id: {}), Stage: SECOND_APPROVAL, Result: APPROVED, Comment: '{}', Client IP: {}",
                    updated.getId(), reviewer.getUsername(), reviewerId, comment, clientIp);
            return mapToResponse(updated);
        } else {
            throw new BadRequestException("Request cannot be approved in current state: " + request.getStatus(), "PKI-40031");
        }
    }

    /**
     * Formally rejects an enrollment request with mandatory reason and justification.
     */
    @Transactional
    public EnrollmentResponse rejectRequest(Long requestId, Long reviewerId, RejectRequestDto dto, String clientIp) {
        EnrollmentRequest request = enrollmentRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("EnrollmentRequest", "id", requestId));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewerId));

        if (request.getStatus() != RequestStatus.PENDING_REVIEW && request.getStatus() != RequestStatus.PENDING_SECOND_APPROVAL) {
            throw new BadRequestException("Only pending requests can be rejected. Current status: " + request.getStatus(), "PKI-40031");
        }

        ReviewStage stage = (request.getStatus() == RequestStatus.PENDING_SECOND_APPROVAL)
                ? ReviewStage.SECOND_APPROVAL
                : ReviewStage.INITIAL_REVIEW;

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(dto.getRejectionReason().trim());
        request.setRejectionComment(dto.getComment() != null ? dto.getComment().trim() : null);

        RequestApproval rejection = new RequestApproval();
        rejection.setRequest(request);
        rejection.setReviewer(reviewer);
        rejection.setAction(ApprovalAction.REJECTED);
        rejection.setReviewStage(stage);
        rejection.setReviewerComment(dto.getRejectionReason() + (dto.getComment() != null ? ": " + dto.getComment() : ""));
        rejection.setClientIp(clientIp);
        request.addApproval(rejection);

        EnrollmentRequest updated = enrollmentRequestRepository.save(request);

        log.info("AUDIT [RA_REQUEST_REJECTED]: Request ID {}, Reviewer '{}' (id: {}), Reason: '{}', Comment: '{}', Client IP: {}",
                updated.getId(), reviewer.getUsername(), reviewerId, dto.getRejectionReason(), dto.getComment(), clientIp);

        return mapToResponse(updated);
    }

    /**
     * Cancels a pending request by the owning End Entity.
     */
    @Transactional
    public EnrollmentResponse cancelRequest(Long requestId, Long requesterId) {
        EnrollmentRequest request = enrollmentRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("EnrollmentRequest", "id", requestId));

        if (!request.getRequester().getId().equals(requesterId)) {
            log.warn("AUDIT [ACCESS_DENIED]: User id {} attempted to cancel unowned request ID {}", requesterId, requestId);
            throw new AccessDeniedException("Access denied: You can only cancel your own enrollment requests.");
        }

        if (request.getStatus() != RequestStatus.PENDING_REVIEW && request.getStatus() != RequestStatus.PENDING_SECOND_APPROVAL) {
            throw new BadRequestException("Request cannot be cancelled in current status: " + request.getStatus(), "PKI-40032");
        }

        request.setStatus(RequestStatus.CANCELLED);
        EnrollmentRequest updated = enrollmentRequestRepository.save(request);

        log.info("AUDIT [ENROLLMENT_CANCELLED]: Request ID {} cancelled by Requester '{}' (id: {})",
                updated.getId(), request.getRequester().getUsername(), requesterId);

        return mapToResponse(updated);
    }

    private EnrollmentResponse mapToResponse(EnrollmentRequest entity) {
        EnrollmentResponse dto = new EnrollmentResponse();
        dto.setRequestId(entity.getId());
        dto.setRequesterId(entity.getRequester().getId());
        dto.setRequesterUsername(entity.getRequester().getUsername());
        dto.setProfileCode(entity.getProfile().getProfileCode());
        dto.setProfileName(entity.getProfile().getProfileName());
        dto.setStatus(entity.getStatus());
        dto.setSubjectDn(entity.getSubjectDn());
        dto.setRawSanEntries(entity.getSanEntries());
        dto.setSanEntries(SanEntriesDto.fromList(entity.getSanEntries()));
        dto.setCsrPem(entity.getCsrPem());
        dto.setKeyAlgorithm(entity.getPublicKeyAlgorithm());
        dto.setKeyLength(entity.getKeyLength());
        dto.setFingerprint(entity.getPublicKeyFingerprint());
        dto.setValidityDays(entity.getValidityDays());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setRejectionComment(entity.getRejectionComment());
        dto.setRequiresDualApproval(entity.getProfile().isRequiresDualApproval());
        dto.setSubmittedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getApprovals() != null) {
            List<RequestApprovalResponse> approvalResponses = entity.getApprovals().stream()
                    .map(app -> new RequestApprovalResponse(
                            app.getId(),
                            app.getReviewer().getId(),
                            app.getReviewer().getUsername(),
                            app.getAction(),
                            app.getReviewStage(),
                            app.getReviewerComment(),
                            app.getClientIp(),
                            app.getReviewedAt()
                    ))
                    .collect(Collectors.toList());
            dto.setApprovals(approvalResponses);
        }

        return dto;
    }
}
