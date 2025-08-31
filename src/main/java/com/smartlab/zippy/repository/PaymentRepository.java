package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends CrudRepository<Payment, UUID> {
    List<Payment> findByUserId(UUID userId);
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
    Optional<Payment> findByPaymentCode(Long paymentCode);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.order LEFT JOIN FETCH p.user WHERE p.orderId = :orderId")
    List<Payment> findByOrderIdWithDetails(@Param("orderId") UUID orderId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.order LEFT JOIN FETCH p.user WHERE p.providerTransactionId = :providerTransactionId")
    Optional<Payment> findByProviderTransactionIdWithDetails(@Param("providerTransactionId") String providerTransactionId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.order LEFT JOIN FETCH p.user WHERE p.paymentCode = :paymentCode")
    Optional<Payment> findByPaymentCodeWithDetails(@Param("paymentCode") String paymentCode);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);

}
