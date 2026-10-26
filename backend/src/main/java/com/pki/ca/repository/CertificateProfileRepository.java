package com.pki.ca.repository;

import com.pki.ca.entity.CertificateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateProfileRepository extends JpaRepository<CertificateProfile, Long> {

    Optional<CertificateProfile> findByProfileCode(String profileCode);

    List<CertificateProfile> findByIsActiveTrue();

    boolean existsByProfileCode(String profileCode);
}
