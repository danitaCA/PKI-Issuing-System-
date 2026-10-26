package com.pki.ca.repository;

import com.pki.ca.entity.EnrollmentRequest;
import com.pki.ca.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, Long> {

    Page<EnrollmentRequest> findByRequesterId(Long requesterId, Pageable pageable);

    Page<EnrollmentRequest> findByStatus(RequestStatus status, Pageable pageable);

    Page<EnrollmentRequest> findByStatusIn(Collection<RequestStatus> statuses, Pageable pageable);

    boolean existsByRequesterIdAndPublicKeyFingerprintAndStatusIn(
            Long requesterId,
            String publicKeyFingerprint,
            Collection<RequestStatus> statuses
    );

    @Query("SELECT r FROM EnrollmentRequest r JOIN FETCH r.requester JOIN FETCH r.profile WHERE r.id = :id")
    Optional<EnrollmentRequest> findByIdWithDetails(@Param("id") Long id);
}
