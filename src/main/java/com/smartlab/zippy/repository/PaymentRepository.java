package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByUserId(UUID userId);
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
}
