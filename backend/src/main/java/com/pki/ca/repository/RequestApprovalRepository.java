package com.pki.ca.repository;

import com.pki.ca.entity.RequestApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestApprovalRepository extends JpaRepository<RequestApproval, Long> {

    List<RequestApproval> findByRequestIdOrderByReviewedAtAsc(Long requestId);

    boolean existsByRequestIdAndReviewerId(Long requestId, Long reviewerId);
}
