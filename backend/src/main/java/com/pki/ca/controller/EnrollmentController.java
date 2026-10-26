package com.pki.ca.controller;

import com.pki.ca.dto.request.EnrollmentSubmissionRequest;
import com.pki.ca.dto.response.EnrollmentResponse;
import com.pki.ca.dto.response.PagedResponse;
import com.pki.ca.enums.RequestStatus;
import com.pki.ca.security.UserPrincipal;
import com.pki.ca.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/enrollment/requests")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Certificate Enrollment", description = "End Entity CSR submission and request tracking")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('END_ENTITY', 'RA_OPERATOR', 'CA_ADMIN')")
    @Operation(summary = "Submit PKCS#10 CSR enrollment request")
    public ResponseEntity<EnrollmentResponse> submitRequest(
            @Valid @RequestBody EnrollmentSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest.getRemoteAddr();
        EnrollmentResponse response = enrollmentService.submitEnrollmentRequest(request, principal.getId(), clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('END_ENTITY', 'RA_OPERATOR', 'CA_ADMIN', 'SECURITY_OFFICER', 'AUDITOR')")
    @Operation(summary = "List enrollment requests (Queue for RA / My Requests for End Entity)")
    public ResponseEntity<PagedResponse<EnrollmentResponse>> listRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @AuthenticationPrincipal UserPrincipal principal) {

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = (sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortField));

        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        boolean isStaff = roles.contains("ROLE_RA_OPERATOR") ||
                          roles.contains("ROLE_CA_ADMIN") ||
                          roles.contains("ROLE_SECURITY_OFFICER") ||
                          roles.contains("ROLE_AUDITOR");

        PagedResponse<EnrollmentResponse> responses;
        if (isStaff) {
            responses = enrollmentService.getRequestsForQueue(status, pageable);
        } else {
            responses = enrollmentService.getMyRequests(principal.getId(), pageable);
        }

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get single request details by ID")
    public ResponseEntity<EnrollmentResponse> getRequestById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        boolean isStaff = roles.contains("ROLE_RA_OPERATOR") ||
                          roles.contains("ROLE_CA_ADMIN") ||
                          roles.contains("ROLE_SECURITY_OFFICER") ||
                          roles.contains("ROLE_AUDITOR");

        EnrollmentResponse response = enrollmentService.getRequestById(id, principal.getId(), isStaff);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('END_ENTITY', 'RA_OPERATOR', 'CA_ADMIN')")
    @Operation(summary = "Cancel a pending enrollment request")
    public ResponseEntity<EnrollmentResponse> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        EnrollmentResponse response = enrollmentService.cancelRequest(id, principal.getId());
        return ResponseEntity.ok(response);
    }
}
