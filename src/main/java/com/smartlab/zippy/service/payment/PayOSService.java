package com.smartlab.zippy.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.zippy.config.PayOSProperties;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.payment.PaymentLinkResponse;
import com.smartlab.zippy.model.dto.web.response.payment.PaymentDTO;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Payment;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.type.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOSService {

    private final PayOSProperties payOSProperties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayOS payOS;

    /**
     * Create a payment link for an order
     * @param orderId The ID of the order to create a payment link for
     * @return PaymentLinkResponse with payment details and redirect URL
     */
    @Transactional
    public PaymentLinkResponse createPaymentLink(UUID orderId) {
        try {
            Order order = validateOrder(orderId);

            String returnUrl = "http://localhost:3000/payment/success?orderId=" + order.getId();
            String cancelUrl = "http://localhost:3000/payment/cancel";

            PaymentData paymentData = buildPaymentData(order, returnUrl, cancelUrl);

            // Create payment link using the PayOS SDK
            log.info("Creating payment link for order: {}", order.getOrderCode());
            CheckoutResponseData responseData = payOS.createPaymentLink(paymentData);

            // Create payment record
            String description = "Payment for order " + order.getOrderCode();
            Payment payment = createPaymentRecord(paymentData.getOrderCode(), order, responseData, description);

            return buildPaymentLinkResponse(payment, responseData, order);
        } catch (Exception e) {
            log.error("Error creating payment link: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    /**
     * Create a payment link for an order (mobile app version)
     * This method will first try to get an existing payment link, and if that fails,
     * it will try to create a new one. If the payment already exists, it will
     * either return the existing payment link or cancel it and create a new one.
     *
     * @param orderId The ID of the order to create a payment link for
     * @param forceNew If true, will cancel any existing payment and create a new one
     * @return PaymentLinkResponse with payment details and redirect URL
     */
    @Transactional
    public PaymentLinkResponse createPaymentLinkForMobile(UUID orderId, boolean forceNew) {
        try {
            Order order = validateOrder(orderId);

            // Try to get existing payment link first
            try {

                Long paymentCode = paymentRepository
                        .findTopByOrderIdOrderByCreatedAtDesc(orderId)
                        .map(Payment::getPaymentCode)
                        .orElse(null);;

                // Try to get existing payment information
                vn.payos.type.PaymentLinkData existingPayment = payOS.getPaymentLinkInformation(paymentCode);

                // If forceNew is true, cancel the existing payment and create a new one
                if (forceNew) {
                    log.info("Cancelling existing payment for order: {}", order.getOrderCode());
                    payOS.cancelPaymentLink(paymentCode, "huy thanh toan");
                }
            } catch (Exception e) {
                // If no existing payment found or other error, continue to create a new one
                log.info("No existing payment found or error occurred: {}", e.getMessage());
            }

            String returnUrl = "zippyapp://payment/result?status=success&orderId=" + order.getId();
            String cancelUrl = "zippyapp://payment/result?status=cancel&orderId=" + order.getId();

//            PaymentData paymentData = buildPaymentData(order, returnUrl, cancelUrl);

            // Log the payment data for debugging
            log.info("Creating mobile payment link for order: {}", order.getOrderCode());
//            log.info("Payment data: {}", objectMapper.writeValueAsString(paymentData));

            // Create payment link using the PayOS SDK
            PaymentData paymentData = buildPaymentData(order, returnUrl, cancelUrl);

            CheckoutResponseData responseData = payOS.createPaymentLink(paymentData);

            // Create payment record
            String description = "Payment for order " + order.getOrderCode();
            Payment payment = createPaymentRecord(paymentData.getOrderCode(), order, responseData, description);

            return buildPaymentLinkResponse(payment, responseData, order);
        } catch (Exception e) {
            log.error("Error creating mobile payment link: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    /**
     * Create a payment link for an order (mobile app version)
     * This is a convenience method that calls the above method with forceNew=false
     *
     * @param orderId The ID of the order to create a payment link for
     * @return PaymentLinkResponse with payment details and redirect URL
     */
    @Transactional
    public PaymentLinkResponse createPaymentLinkForMobile(UUID orderId) {
        return createPaymentLinkForMobile(orderId, true);
    }

    /**
     * Verify a payment from PayOS webhook or callback
     * @param paymentId The payment ID from PayOS
     * @return true if payment is verified successfully
     */
    @Transactional
    public boolean verifyPayment(String paymentId) {
        try {
            log.info("Verifying payment with ID: {}", paymentId);

            // Convert String paymentId to Long
            Long paymentLinkId;
            try {
                paymentLinkId = Long.parseLong(paymentId);
            } catch (NumberFormatException e) {
                log.error("Invalid payment ID format: {}", paymentId);
                return false;
            }

            // Use the PayOS SDK to get payment information
            vn.payos.type.PaymentLinkData paymentLinkData = payOS.getPaymentLinkInformation(paymentLinkId);

            if (paymentLinkData != null && "PAID".equalsIgnoreCase(paymentLinkData.getStatus())) {
                // Find payment by provider transaction ID with details loaded
                Optional<Payment> paymentOpt = paymentRepository.findByProviderTransactionIdWithDetails(paymentId);

                if (paymentOpt.isPresent()) {
                    Payment payment = paymentOpt.get();
                    payment.setTransactedAt(LocalDateTime.now());
                    payment.setUpdatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);

                    // Update order status if needed
                    Order order = orderRepository.findById(payment.getOrderId())
                            .orElseThrow(() -> new RuntimeException("Order not found"));
                    if (!"PAID".equals(order.getStatus())) {
                        order.setStatus("PAID");
                        order.setUpdatedAt(LocalDateTime.now());
                        orderRepository.save(order);
                    }

                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to verify payment: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel a payment for an order
     * @param orderId The ID of the order to cancel payment for
     * @return True if cancellation was successful
     */
    @Transactional
    public boolean cancelPayment(UUID orderId) {
        try {
            Order order = validateOrder(orderId);

            Long paymentCode = paymentRepository
                    .findTopByOrderIdOrderByCreatedAtDesc(orderId)
                    .map(Payment::getPaymentCode)
                    .orElse(null);

            log.info("Cancelling payment for order: {}", order.getOrderCode());

            // Cancel the payment in PayOS
            vn.payos.type.PaymentLinkData cancelledPayment = payOS.cancelPaymentLink(paymentCode, "huy thanh toan");

            // Update our payment records if exists
            List<Payment> payments = paymentRepository.findByOrderIdWithDetails(orderId);
            if (!payments.isEmpty()) {
                for (Payment payment : payments) {
                    payment.setUpdatedAt(LocalDateTime.now());
                    payment.setStatus("CANCELLED");
                    paymentRepository.save(payment);
                }
            }

            return cancelledPayment != null;
        } catch (Exception e) {
            log.error("Error cancelling payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to cancel payment: " + e.getMessage(), e);
        }
    }

    /**
     * Get existing payment link for an order
     * @param orderId The ID of the order
     * @return PaymentLinkResponse with existing payment details or null if none exists
     */
    @Transactional(readOnly = true)
    public PaymentLinkResponse getExistingPaymentLink(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

            // Convert orderCode to numeric value for PayOS API
            Long orderCodeNumeric = Long.parseLong(order.getOrderCode().replaceAll("[^0-9]", ""));

            // Try to get existing payment information
            vn.payos.type.PaymentLinkData existingPayment = payOS.getPaymentLinkInformation(orderCodeNumeric);

            if (existingPayment != null) {
                log.info("Found existing payment link for order: {}", order.getOrderCode());

                // Find or create a payment record in our database
                Optional<Payment> existingPaymentRecord = paymentRepository.findByProviderTransactionIdWithDetails(
                        String.valueOf(existingPayment.getId()));

                Payment payment;
                if (existingPaymentRecord.isPresent()) {
                    payment = existingPaymentRecord.get();
                } else {
                    payment = Payment.builder()
                            .orderId(orderId)
                            .userId(order.getUserId())
                            .price(order.getPrice())
                            .currency("VND")
                            .paymentMethod("PayOS")
                            .description("Payment for order " + order.getOrderCode())
                            .providerTransactionId(String.valueOf(existingPayment.getId()))
                            .status(existingPayment.getStatus())
                            .createdAt(LocalDateTime.now())
                            .build();
                    paymentRepository.save(payment);
                }

                return PaymentLinkResponse.builder()
                        .paymentId(payment.getId())
                        .paymentLinkId(String.valueOf(existingPayment.getId()))
                        .checkoutUrl("https://pay.payos.vn/web/" + existingPayment.getId())
                        .orderCode(order.getOrderCode())
                        .amount(order.getPrice())
                        .build();
            }

            return null;
        } catch (Exception e) {
            log.error("Error getting existing payment link: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get payment status for an order using PayOS API
     * @param orderId The order ID to get payment status for
     * @return Map containing payment status information
     */
    @Transactional // Remove readOnly = true to allow database updates
    public Map<String, Object> getPaymentStatus(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

            Optional<Payment> paymentOtp = paymentRepository
                    .findTopByOrderIdOrderByCreatedAtDesc(orderId);

            vn.payos.type.PaymentLinkData paymentLinkData;

            if (paymentOtp.isEmpty()) {
                throw new RuntimeException("No payment found for order ID: " + orderId);
            } else {
                Payment payment = paymentOtp.get();
                log.info("Getting payment status for order: {} with payment_code: {}", order.getOrderCode(), payment.getPaymentCode());
                Long paymentCode = payment.getPaymentCode();

                if (paymentCode == null) {
                    throw new RuntimeException("Payment code is null for order ID: " + orderId);
                }

                paymentLinkData = payOS.getPaymentLinkInformation(paymentCode);
            }

            return getStringObjectMap(orderId, paymentLinkData);
        } catch (Exception e) {
            log.error("Error getting payment status for order {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to get payment status: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> getStringObjectMap(UUID orderId, PaymentLinkData paymentLinkData) {
        if (paymentLinkData == null) {
            throw new RuntimeException("No payment information found in PayOS for order ID: " + orderId);
        }

        List<Transaction> transactions = paymentLinkData.getTransactions();

        boolean isPaid = !transactions.isEmpty() && "PAID".equalsIgnoreCase(paymentLinkData.getStatus());

        if (isPaid) {
            updateOrderAndPaymentStatus(orderId);
        }

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("paymentStatus", paymentLinkData.getStatus());
        statusMap.put("amount", paymentLinkData.getAmount());
        statusMap.put("createdAt", paymentLinkData.getCreatedAt());
        if (!transactions.isEmpty()) {
            statusMap.put("paidAt", transactions.get(0).getTransactionDateTime());
        }
        return statusMap;
    }

    @Transactional
    private void updateOrderAndPaymentStatus(UUID orderId) {
        try {
            // Update Order status
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

            if (!"PAID".equals(order.getStatus())) {
                order.setStatus("PAID");
                order.setUpdatedAt(LocalDateTime.now()); // Set updated timestamp
                orderRepository.save(order);
                log.info("Updated order {} status to PAID", order.getOrderCode());
            }

            // Update Payment transactedAt if not already set
            Optional<Payment> paymentOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);
            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();
                if (payment.getTransactedAt() == null) {
                    payment.setTransactedAt(LocalDateTime.now());
                    payment.setUpdatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                    log.info("Updated payment {} transactedAt", payment.getId());
                }
            }

        } catch (Exception e) {
            log.error("Error updating order and payment status for order {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to update order/payment status: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to validate order and ensure it has a valid price
     * @param orderId The order ID to validate
     * @return The validated order
     */
    private Order validateOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Order must have a price greater than zero");
        }

        return order;
    }

    /**
     * Helper method to build payment data for PayOS API
     * @param order The order to create payment data for
     * @param returnUrl The return URL after payment
     * @param cancelUrl The cancel URL for payment
     * @return PaymentData object for PayOS API
     */
    private PaymentData buildPaymentData(Order order, String returnUrl, String cancelUrl) {
        int amount = Math.max(1000, order.getPrice().intValue()); // Minimum amount of 1000 VND
        String description = "Payment for order " + order.getOrderCode();

        // Create item for the order
        ItemData item = ItemData.builder()
                .name("Order: " + order.getOrderCode())
                .price(amount)
                .quantity(1)
                .build();

        // Build the payment data using the PayOS SDK
        return PaymentData.builder()
                .orderCode(getOrderCode())
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .item(item)
                .build();
    }

    private long getOrderCode() {
        // Generate a unique numeric order code
        return System.currentTimeMillis(); // Simple example, replace with your own logic
    }

    /**
     * Helper method to create a payment record
     * @param order The order to create payment for
     * @param responseData The checkout response data
     * @param description Payment description
     * @return The created payment entity
     */
    private Payment createPaymentRecord(Long paymentCode, Order order, CheckoutResponseData responseData, String description) {
        Payment payment = Payment.builder()
                .paymentCode(paymentCode)
                .orderId(order.getId())
                .userId(order.getUserId())
                .price(order.getPrice())
                .currency("VND")
                .paymentMethod("PayOS")
                .description(description)
                .providerTransactionId(responseData.getPaymentLinkId())
                .createdAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }

    /**
     * Helper method to build payment link response
     * @param payment The payment entity
     * @param responseData The checkout response data
     * @param order The order
     * @return PaymentLinkResponse object
     */
    private PaymentLinkResponse buildPaymentLinkResponse(Payment payment, CheckoutResponseData responseData, Order order) {
        return PaymentLinkResponse.builder()
                .paymentId(payment.getId())
                .paymentLinkId(responseData.getPaymentLinkId())
                .checkoutUrl(responseData.getCheckoutUrl())
                .orderCode(order.getOrderCode())
                .amount(order.getPrice())
                .build();
    }
}

