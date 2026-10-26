package com.pki.ca.repository;

import com.pki.ca.entity.CaAuthority;
import com.pki.ca.enums.CaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaAuthorityRepository extends JpaRepository<CaAuthority, Long> {

    Optional<CaAuthority> findFirstByStatus(CaStatus status);

    Optional<CaAuthority> findByCaName(String caName);

    Optional<CaAuthority> findByHsmKeyAlias(String hsmKeyAlias);

    Optional<CaAuthority> findBySerialNumber(String serialNumber);
}
