package com.pki.ca.controller;

import com.pki.ca.dto.response.CertificateDownloadDto;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.dto.response.CertificateSummaryResponse;
import com.pki.ca.dto.response.PagedResponse;
import com.pki.ca.security.UserPrincipal;
import com.pki.ca.service.CaIssuanceService;
import com.pki.ca.service.CertificateQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@Tag(name = "Certificates", description = "Endpoints for certificate issuance, repository querying, and downloading")
public class CertificateController {

    private final CaIssuanceService caIssuanceService;
    private final CertificateQueryService certificateQueryService;

    public CertificateController(CaIssuanceService caIssuanceService,
                                 CertificateQueryService certificateQueryService) {
        this.caIssuanceService = caIssuanceService;
        this.certificateQueryService = certificateQueryService;
    }

    @PostMapping("/api/v1/ca/requests/{id}/issue")
    @PreAuthorize("hasAnyRole('RA_OPERATOR', 'CA_ADMIN')")
    @Operation(summary = "Execute HSM digital signature and certificate issuance for an approved request")
    public ResponseEntity<CertificateResponse> issueCertificate(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        String clientIp = resolveClientIp(request);
        CertificateResponse response = caIssuanceService.issueCertificate(id, principal, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/certificates")
    @PreAuthorize("hasAnyRole('RA_OPERATOR', 'CA_ADMIN', 'SECURITY_OFFICER', 'AUDITOR')")
    @Operation(summary = "Search issued certificates with filtering and pagination")
    public ResponseEntity<PagedResponse<CertificateSummaryResponse>> searchCertificates(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "commonName", required = false) String commonName,
            @RequestParam(value = "serialNumber", required = false) String serialNumber,
            @RequestParam(value = "notAfterBefore", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime notAfterBefore,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CertificateSummaryResponse> result = certificateQueryService.searchCertificates(
                status, commonName, serialNumber, notAfterBefore, pageable
        );
        return ResponseEntity.ok(new PagedResponse<>(result));
    }

    @GetMapping("/api/v1/certificates/my")
    @PreAuthorize("hasRole('END_ENTITY')")
    @Operation(summary = "View certificates owned by the currently authenticated End Entity")
    public ResponseEntity<PagedResponse<CertificateSummaryResponse>> getMyCertificates(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CertificateSummaryResponse> result = certificateQueryService.getMyCertificates(principal.getId(), pageable);
        return ResponseEntity.ok(new PagedResponse<>(result));
    }

    @GetMapping("/api/v1/certificates/{serialNumber}")
    @PreAuthorize("hasAnyRole('END_ENTITY', 'RA_OPERATOR', 'CA_ADMIN', 'SECURITY_OFFICER', 'AUDITOR')")
    @Operation(summary = "Get full details of a specific certificate by serial number")
    public ResponseEntity<CertificateResponse> getCertificateDetails(
            @PathVariable("serialNumber") String serialNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        CertificateResponse response = certificateQueryService.getCertificateDetails(serialNumber, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/certificates/{serialNumber}/download")
    @Operation(summary = "Download public X.509 certificate file in PEM, DER, or P7B bundle format")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable("serialNumber") String serialNumber,
            @RequestParam(value = "format", defaultValue = "pem") String format) {

        CertificateDownloadDto download = certificateQueryService.downloadCertificate(serialNumber, format);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.getFilename() + "\"")
                .body(download.getData());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
