package com.smartlab.zippy.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_code", unique = true)
    private Long paymentCode;

    @Column(name = "order_id")
    private UUID orderId;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "transacted_at")
    private LocalDateTime transactedAt;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    private String description;
    
    private BigDecimal price;
    
    private String currency;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "provider_transaction_id")
    private String providerTransactionId;
    
    @Column(name = "status")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
