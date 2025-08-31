package com.smartlab.zippy.model.dto.web.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {
    private UUID paymentId;
    private String paymentLinkId;
    private String checkoutUrl;
    private String orderCode;
    private BigDecimal amount;
}
