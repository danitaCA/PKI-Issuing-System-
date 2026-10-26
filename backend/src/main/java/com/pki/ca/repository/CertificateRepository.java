package com.pki.ca.repository;

import com.pki.ca.entity.Certificate;
import com.pki.ca.enums.CertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long>, JpaSpecificationExecutor<Certificate> {

    Optional<Certificate> findBySerialNumber(String serialNumber);

    Optional<Certificate> findByEnrollmentRequestId(Long requestId);

    Optional<Certificate> findByFingerprintSha256(String fingerprintSha256);

    boolean existsBySerialNumber(String serialNumber);

    Page<Certificate> findByEnrollmentRequestRequesterId(Long requesterId, Pageable pageable);

    long countByStatus(CertificateStatus status);
}
