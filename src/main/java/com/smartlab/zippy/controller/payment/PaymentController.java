package com.smartlab.zippy.controller.payment;

import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.payment.PaymentDTO;
import com.smartlab.zippy.model.dto.web.response.payment.PaymentLinkResponse;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Payment;
import com.smartlab.zippy.service.payment.PayOSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PayOSService payOSService;

    /**
     * Create a payment link for an order
     * @param orderId The ID of the order to create a payment for
     * @return Payment link information including checkout URL
     */
    @PostMapping("/create/{orderId}")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createPayment(@PathVariable UUID orderId) {
        try {
            log.info("Creating payment for order ID: {}", orderId);
            PaymentLinkResponse response = payOSService.createPaymentLink(orderId);

            return ResponseEntity.ok(
                    ApiResponse.success(response, "Payment link created successfully")
            );
        } catch (Exception e) {
            log.error("Error creating payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create payment: " + e.getMessage()));
        }
    }

    /**
     * Webhook endpoint for PayOS to notify about payment status changes
     * @param payload The payload sent by PayOS
     * @return Success response if webhook processed successfully
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> paymentWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received payment webhook: {}", payload);

            // Extract payment ID from the payload
            if (payload.containsKey("paymentLinkId")) {
                String paymentLinkId = payload.get("paymentLinkId").toString();
                boolean verified = payOSService.verifyPayment(paymentLinkId);

                if (verified) {
                    return ResponseEntity.ok(
                            ApiResponse.success("Payment verified successfully")
                    );
                } else {
                    return ResponseEntity.ok(
                            ApiResponse.success("Payment verification pending")
                    );
                }
            }

            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid webhook payload"));
        } catch (Exception e) {
            log.error("Error processing payment webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process payment webhook: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to manually verify a payment status
     * @param paymentLinkId The payment link ID from PayOS
     * @return Success response if payment verified
     */
    @GetMapping("/verify/{paymentLinkId}")
    public ResponseEntity<ApiResponse<String>> verifyPayment(@PathVariable String paymentLinkId) {
        try {
            log.info("Manually verifying payment: {}", paymentLinkId);
            boolean verified = payOSService.verifyPayment(paymentLinkId);

            if (verified) {
                return ResponseEntity.ok(
                        ApiResponse.success("Payment verified successfully")
                );
            } else {
                return ResponseEntity.ok(
                        ApiResponse.error("Payment verification failed")
                );
            }
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to verify payment: " + e.getMessage()));
        }
    }

    /**
     * Create a payment link for a mobile app
     * @param orderId The ID of the order to create a payment for
     * @return Payment link information including checkout URL
     */
    @PostMapping("/mobile/create/{orderId}")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createMobilePayment(@PathVariable UUID orderId) {
        try {
            log.info("Creating mobile payment for order ID: {}", orderId);
            PaymentLinkResponse response = payOSService.createPaymentLinkForMobile(orderId);

            return ResponseEntity.ok(
                    ApiResponse.success(response, "Mobile payment link created successfully")
            );
        } catch (Exception e) {
            log.error("Error creating mobile payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create mobile payment: " + e.getMessage()));
        }
    }

    /**
     * Endpoint for mobile clients to check payment status after redirection
     * @param orderId The order ID to verify payment for
     * @return Status of the payment
     */
    @GetMapping("/mobile/status/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMobilePaymentStatus(@PathVariable UUID orderId) {
        try {
            log.info("Checking payment status for order ID: {}", orderId);

            // Use the new getPaymentStatus method that calls PayOS API
            Map<String, Object> response = payOSService.getPaymentStatus(orderId);

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error checking payment status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check payment status: " + e.getMessage()));
        }
    }

    /**
     * Cancel a payment for an order
     * @param orderId The ID of the order to cancel payment for
     * @return Success response if payment was cancelled successfully
     */
    @DeleteMapping("/mobile/cancel/{orderId}")
    public ResponseEntity<ApiResponse<String>> cancelMobilePayment(@PathVariable UUID orderId) {
        try {
            log.info("Cancelling payment for order ID: {}", orderId);
            boolean cancelled = payOSService.cancelPayment(orderId);

            if (cancelled) {
                return ResponseEntity.ok(
                        ApiResponse.success("Payment cancelled successfully")
                );
            } else {
                return ResponseEntity.ok(
                        ApiResponse.error("No payment found to cancel or cancellation failed")
                );
            }
        } catch (Exception e) {
            log.error("Error cancelling payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cancel payment: " + e.getMessage()));
        }
    }

    /**
     * Get existing payment link for an order or create a new one
     * @param orderId The ID of the order
     * @param forceNew If true, will cancel any existing payment and create a new one
     * @return Payment link information including checkout URL
     */
    @PostMapping("/mobile/create/{orderId}/force-new")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createMobilePaymentForceNew(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "true") boolean forceNew) {
        try {
            log.info("Creating mobile payment for order ID: {}, forceNew: {}", orderId, forceNew);
            PaymentLinkResponse response = payOSService.createPaymentLinkForMobile(orderId, forceNew);

            return ResponseEntity.ok(
                    ApiResponse.success(response, "Mobile payment link created successfully")
            );
        } catch (Exception e) {
            log.error("Error creating mobile payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create mobile payment: " + e.getMessage()));
        }
    }

    /**
     * Get existing payment link for an order without creating a new one if it exists
     * @param orderId The ID of the order
     * @return Existing payment link information or error if none exists
     */
    @GetMapping("/mobile/get/{orderId}")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> getMobilePaymentLink(@PathVariable UUID orderId) {
        try {
            log.info("Getting existing payment link for order ID: {}", orderId);
            PaymentLinkResponse response = payOSService.getExistingPaymentLink(orderId);

            if (response != null) {
                return ResponseEntity.ok(
                        ApiResponse.success(response, "Existing payment link retrieved successfully")
                );
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No existing payment link found for this order"));
            }
        } catch (Exception e) {
            log.error("Error getting payment link: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get payment link: " + e.getMessage()));
        }
    }
}
