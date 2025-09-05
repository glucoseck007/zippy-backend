package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.PickupOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PickupOtpRepository extends JpaRepository<PickupOtp, Long> {

    @Query("SELECT p FROM PickupOtp p WHERE p.orderCode = :orderCode AND p.tripCode = :tripCode AND p.verified = false ORDER BY p.createdAt DESC")
    Optional<PickupOtp> findLatestUnverifiedOtp(@Param("orderCode") String orderCode, @Param("tripCode") String tripCode);

    @Query("SELECT p FROM PickupOtp p WHERE p.orderCode = :orderCode AND p.tripCode = :tripCode AND p.otpCode = :otpCode AND p.verified = false")
    Optional<PickupOtp> findUnverifiedOtp(@Param("orderCode") String orderCode, @Param("tripCode") String tripCode, @Param("otpCode") String otpCode);

    @Query("SELECT COUNT(p) FROM PickupOtp p WHERE p.orderCode = :orderCode AND p.tripCode = :tripCode AND p.verified = true")
    long countVerifiedOtps(@Param("orderCode") String orderCode, @Param("tripCode") String tripCode);

    // Clean up expired OTPs
    @Query("DELETE FROM PickupOtp p WHERE p.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);

    // Count OTPs created within a time window for rate limiting
    @Query("SELECT COUNT(p) FROM PickupOtp p WHERE p.orderCode = :orderCode AND p.tripCode = :tripCode AND p.createdAt > :since")
    long countOtpsCreatedSince(@Param("orderCode") String orderCode, @Param("tripCode") String tripCode, @Param("since") LocalDateTime since);
}
