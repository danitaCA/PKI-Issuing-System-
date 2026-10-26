package com.pki.ca.repository;

import com.pki.ca.entity.User;
import com.pki.ca.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE UserToken ut SET ut.revoked = true WHERE ut.user = :user")
    void revokeAllTokensForUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM UserToken ut WHERE ut.expiresAt < :now OR ut.revoked = true")
    void deleteExpiredOrRevokedTokens(@Param("now") LocalDateTime now);
}
