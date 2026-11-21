package com.example.eventsbooking.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(long userId);

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM EmailVerification v WHERE v.userId = :userId")
    int deleteByUserId(@Param("userId") long userId);
}
