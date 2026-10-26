package com.pki.ca.controller;

import com.pki.ca.dto.request.CaImportCertRequest;
import com.pki.ca.dto.request.CaInitRequest;
import com.pki.ca.dto.response.CaInitResponse;
import com.pki.ca.dto.response.CaResponse;
import com.pki.ca.service.CaAuthorityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/ca")
@Tag(name = "CA Management", description = "Endpoints for managing the Issuing Certificate Authority, HSM initialization, and certificate downloads")
public class CaManagementController {

    private final CaAuthorityService caAuthorityService;

    public CaManagementController(CaAuthorityService caAuthorityService) {
        this.caAuthorityService = caAuthorityService;
    }

    @GetMapping
    @Operation(summary = "Get CA operational status and metadata")
    public ResponseEntity<CaResponse> getCaMetadata() {
        return ResponseEntity.ok(caAuthorityService.getCaMetadata());
    }

    @GetMapping(value = "/certificate", produces = "application/x-pem-file")
    @Operation(summary = "Download public X.509 certificate of the Issuing CA")
    public ResponseEntity<byte[]> downloadCaCertificate() {
        String certPem = caAuthorityService.getCaCertificatePem();
        byte[] bytes = certPem.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"issuing-ca.crt\"")
                .body(bytes);
    }

    @GetMapping(value = "/chain", produces = "application/x-pem-file")
    @Operation(summary = "Download complete PEM bundle containing Issuing CA + Root CA")
    public ResponseEntity<byte[]> downloadCaChain() {
        String chainPem = caAuthorityService.getCaChainPem();
        byte[] bytes = chainPem.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ca-chain.crt\"")
                .body(bytes);
    }

    @PostMapping("/init")
    @PreAuthorize("hasRole('CA_ADMIN')")
    @Operation(summary = "Trigger on-token CA asymmetric key generation in HSM and output Subordinate CA CSR")
    public ResponseEntity<CaInitResponse> initCaKey(@Valid @RequestBody CaInitRequest request) {
        CaInitResponse response = caAuthorityService.initCaKey(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/import-cert")
    @PreAuthorize("hasRole('CA_ADMIN')")
    @Operation(summary = "Import intermediate certificate signed by Root CA to activate the Issuing CA")
    public ResponseEntity<CaResponse> importSignedCert(@Valid @RequestBody CaImportCertRequest request) {
        CaResponse response = caAuthorityService.importSignedCert(request);
        return ResponseEntity.ok(response);
    }
}
