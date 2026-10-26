package com.pki.ca.repository;

import com.pki.ca.entity.IssuanceAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssuanceAttemptRepository extends JpaRepository<IssuanceAttempt, Long> {

    Optional<IssuanceAttempt> findByAllocatedSerialNumber(String allocatedSerialNumber);

    List<IssuanceAttempt> findByEnrollmentRequestId(Long requestId);

    Optional<IssuanceAttempt> findTopByEnrollmentRequestIdOrderByCreatedAtDesc(Long requestId);
}
