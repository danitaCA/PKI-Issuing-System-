package com.pki.ca.service;

import com.pki.ca.dto.response.CertificateDownloadDto;
import com.pki.ca.dto.response.CertificateResponse;
import com.pki.ca.dto.response.CertificateSummaryResponse;
import com.pki.ca.entity.Certificate;
import com.pki.ca.enums.CertificateStatus;
import com.pki.ca.exception.BadRequestException;
import com.pki.ca.exception.ResourceNotFoundException;
import com.pki.ca.repository.CertificateRepository;
import jakarta.persistence.criteria.Predicate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.util.CollectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CertificateQueryService {

    private static final Logger log = LoggerFactory.getLogger(CertificateQueryService.class);

    private final CertificateRepository certificateRepository;

    public CertificateQueryService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    /**
     * Searches certificates with optional filtering across status, CN, serial, and validity.
     */
    @Transactional(readOnly = true)
    public Page<CertificateSummaryResponse> searchCertificates(
            String status,
            String commonName,
            String serialNumber,
            LocalDateTime notAfterBefore,
            Pageable pageable) {

        Specification<Certificate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                try {
                    CertificateStatus certStatus = CertificateStatus.valueOf(status.toUpperCase().trim());
                    predicates.add(cb.equal(root.get("status"), certStatus));
                } catch (IllegalArgumentException ex) {
                    throw new BadRequestException("Invalid certificate status filter: " + status, "PKI-40016");
                }
            }

            if (commonName != null && !commonName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("subjectDn")), "%cn=" + commonName.toLowerCase().trim() + "%"));
            }

            if (serialNumber != null && !serialNumber.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("serialNumber")), serialNumber.toLowerCase().trim()));
            }

            if (notAfterBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("notAfter"), notAfterBefore));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return certificateRepository.findAll(spec, pageable).map(this::mapToSummary);
    }

    /**
     * Retrieves issued certificates owned by a specific End Entity user.
     */
    @Transactional(readOnly = true)
    public Page<CertificateSummaryResponse> getMyCertificates(Long requesterId, Pageable pageable) {
        return certificateRepository.findByEnrollmentRequestRequesterId(requesterId, pageable)
                .map(this::mapToSummary);
    }

    /**
     * Retrieves detailed certificate record by serial number with ownership checks.
     */
    @Transactional(readOnly = true)
    public CertificateResponse getCertificateDetails(String serialNumber, com.pki.ca.security.UserPrincipal principal) {
        Certificate cert = certificateRepository.findBySerialNumber(serialNumber.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with serial number: " + serialNumber));

        // Enforce ownership fence for End Entity users
        if (principal != null) {
            boolean isStaff = principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_RA_OPERATOR")
                            || a.getAuthority().equals("ROLE_CA_ADMIN")
                            || a.getAuthority().equals("ROLE_SECURITY_OFFICER")
                            || a.getAuthority().equals("ROLE_AUDITOR"));

            if (!isStaff) {
                Long requesterId = cert.getEnrollmentRequest().getRequester().getId();
                if (!requesterId.equals(principal.getId())) {
                    log.warn("AUDIT [ACCESS_DENIED]: User '{}' (id: {}) attempted unauthorized access to certificate serial '{}' owned by requester id {}",
                            principal.getUsername(), principal.getId(), serialNumber, requesterId);
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Access denied: You do not have permission to view certificate metadata for serial: " + serialNumber);
                }
            }
        }

        return mapToResponse(cert);
    }

    /**
     * Downloads an issued certificate file in the requested format (PEM, DER, P7B).
     */
    @Transactional(readOnly = true)
    public CertificateDownloadDto downloadCertificate(String serialNumber, String format) {
        Certificate cert = certificateRepository.findBySerialNumber(serialNumber.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with serial number: " + serialNumber));

        String fmt = (format != null && !format.isBlank()) ? format.toLowerCase().trim() : "pem";
        if (!fmt.equals("pem") && !fmt.equals("der") && !fmt.equals("p7b")) {
            throw new BadRequestException("Unsupported certificate download format: '" + format + "'. Allowed formats: 'pem', 'der', 'p7b'.", "PKI-40017");
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate x509Cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(cert.getCertificatePem().getBytes(StandardCharsets.UTF_8)));

            return switch (fmt) {
                case "der" -> new CertificateDownloadDto(
                        cert.getSerialNumber() + ".cer",
                        "application/pkix-cert",
                        x509Cert.getEncoded()
                );
                case "p7b" -> {
                    // Bundle leaf certificate and CA certificate in CMS/PKCS#7 SignedData
                    List<X509CertificateHolder> certList = new ArrayList<>();
                    certList.add(new JcaX509CertificateHolder(x509Cert));

                    if (cert.getCaAuthority() != null && cert.getCaAuthority().getCertificatePem() != null) {
                        X509Certificate caCert = (X509Certificate) cf.generateCertificate(
                                new ByteArrayInputStream(cert.getCaAuthority().getCertificatePem().getBytes(StandardCharsets.UTF_8)));
                        certList.add(new JcaX509CertificateHolder(caCert));
                    }

                    CollectionStore<X509CertificateHolder> store = new CollectionStore<>(certList);
                    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                    gen.addCertificates(store);
                    CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(new byte[0]), false);

                    yield new CertificateDownloadDto(
                            cert.getSerialNumber() + ".p7b",
                            "application/x-pkcs7-certificates",
                            signedData.getEncoded()
                    );
                }
                default -> new CertificateDownloadDto(
                        cert.getSerialNumber() + ".crt",
                        "application/x-pem-file",
                        cert.getCertificatePem().getBytes(StandardCharsets.UTF_8)
                );
            };
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to package certificate download for serial {}: {}", serialNumber, ex.getMessage(), ex);
            throw new RuntimeException("Error packaging certificate: " + ex.getMessage(), ex);
        }
    }

    private CertificateSummaryResponse mapToSummary(Certificate cert) {
        return new CertificateSummaryResponse(
                cert.getId(),
                cert.getEnrollmentRequest().getId(),
                cert.getSerialNumber(),
                cert.getSubjectDn(),
                cert.getIssuerDn(),
                cert.getNotBefore(),
                cert.getNotAfter(),
                cert.getStatus().name(),
                cert.getFingerprintSha256(),
                cert.getCreatedAt()
        );
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
