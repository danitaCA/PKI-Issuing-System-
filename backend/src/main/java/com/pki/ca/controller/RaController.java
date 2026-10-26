package com.pki.ca.controller;

import com.pki.ca.dto.request.ApproveRequestDto;
import com.pki.ca.dto.request.RejectRequestDto;
import com.pki.ca.dto.response.EnrollmentResponse;
import com.pki.ca.security.UserPrincipal;
import com.pki.ca.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ra/requests")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Registration Authority (RA) Review", description = "RA operator review queue and request decisions")
public class RaController {

    private final EnrollmentService enrollmentService;

    public RaController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('RA_OPERATOR', 'CA_ADMIN', 'SECURITY_OFFICER')")
    @Operation(summary = "Approve enrollment request (Enforces Separation of Duties & Dual Approval)")
    public ResponseEntity<EnrollmentResponse> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest.getRemoteAddr();
        Set<String> reviewerRoles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        EnrollmentResponse response = enrollmentService.approveRequest(id, principal.getId(), dto, clientIp, reviewerRoles);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RA_OPERATOR', 'CA_ADMIN')")
    @Operation(summary = "Reject enrollment request with mandatory justification")
    public ResponseEntity<EnrollmentResponse> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest.getRemoteAddr();
        EnrollmentResponse response = enrollmentService.rejectRequest(id, principal.getId(), dto, clientIp);
        return ResponseEntity.ok(response);
    }
}
