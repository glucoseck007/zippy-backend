package com.smartlab.zippy.controller.order;

import com.smartlab.zippy.model.dto.web.request.order.BatchOrderRequest;
import com.smartlab.zippy.model.dto.web.request.order.OrderRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.order.OrderResponse;
import com.smartlab.zippy.model.dto.web.response.qr.QRCodeResponse;
import com.smartlab.zippy.service.order.OrderService;
import com.smartlab.zippy.model.dto.web.request.order.PickupOtpRequest;
import com.smartlab.zippy.model.dto.web.request.order.PickupVerifyOtpRequest;
import com.smartlab.zippy.service.order.PickupOtpService;
import com.smartlab.zippy.model.dto.web.response.order.PickupResponse;
import com.smartlab.zippy.service.auth.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PickupOtpService pickupOtpService;
    private final JwtService jwtService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody OrderRequest request) {
        try {
            log.info("Received order request for sender: {}, product: {}",
                    request.getSenderIdentifier(), request.getProductName());

            OrderResponse orderResponse = orderService.createOrder(request);

            return ResponseEntity.ok(
                    ApiResponse.success(orderResponse, "Order created successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Create multiple orders for different receivers in one batch
     * Optimized for use case where one user orders for multiple recipients
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> createBatchOrders(@Valid @RequestBody BatchOrderRequest request) {
        try {
            log.info("Received batch order request from sender: {} for {} recipients",
                    request.getSenderIdentifier(), request.getRecipients().size());

            List<OrderResponse> orderResponses = orderService.createBatchOrders(request);

            return ResponseEntity.ok(
                    ApiResponse.success(orderResponses,
                        String.format("Successfully created %d orders in batch", orderResponses.size()))
            );

        } catch (RuntimeException e) {
            log.error("Error creating batch orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error creating batch orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred during batch order creation"));
        }
    }

    @GetMapping("/get")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrders(
            @RequestParam String username,
            @RequestParam(required = false, defaultValue = "both") String role) {
        try {
            List<OrderResponse> orders;

            if (username != null && !username.trim().isEmpty()) {
                log.info("Received request to get orders for username: {} with role: {}", username, role);

                // Switch based on the requested role
                switch(role.toLowerCase()) {
                    case "sender":
                        // Get orders where user is the sender
                        orders = orderService.getOrdersBySender(username);
                        break;
                    case "receiver":
                        // Get orders where user is the receiver
                        orders = orderService.getOrdersByReceiver(username);
                        break;
                    case "both":
                    default:
                        // Get orders where user is either sender or receiver
                        List<OrderResponse> sentOrders = orderService.getOrdersBySender(username);
                        List<OrderResponse> receivedOrders = orderService.getOrdersByReceiver(username);

                        // Combine both lists
                        orders = new ArrayList<>();
                        orders.addAll(sentOrders);
                        orders.addAll(receivedOrders);
                        break;
                }
            } else {
                log.info("Received request to get all orders");
                orders = orderService.getAllOrders();
            }

            return ResponseEntity.ok(
                    ApiResponse.success(orders, "Orders retrieved successfully")
            );

        } catch (Exception e) {
            log.error("Unexpected error retrieving orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    /**
     * Staff endpoint to get all orders from all users
     * Only accessible by users with STAFF role
     */
    @GetMapping("/staff/all")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrdersForStaff(
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Extract token from Authorization header
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authorization header is missing or invalid"));
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix

            // Extract role from JWT token
            String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));

            // Check if user has STAFF role
            if (!"STAFF".equalsIgnoreCase(role)) {
                log.warn("Unauthorized access attempt to staff endpoint. User role: {}", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied. Staff role required."));
            }

            log.info("Staff user requesting all orders from all users");
            List<OrderResponse> allOrders = orderService.getAllOrders();

            return ResponseEntity.ok(
                    ApiResponse.success(allOrders, "All orders retrieved successfully for staff")
            );

        } catch (Exception e) {
            log.error("Unexpected error retrieving all orders for staff: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/approve/{orderCode}")
    public ResponseEntity<ApiResponse<Boolean>> approveOrder(@PathVariable String orderCode) {
        try {
            log.info("Received request to approve order with code: {}", orderCode);

            boolean orderResponse = orderService.approveOrder(orderCode);

            return ResponseEntity.ok(
                    ApiResponse.success(orderResponse, "Order approved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error approving order with code {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error approving order with code {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/generate-qr")
    public ResponseEntity<ApiResponse<QRCodeResponse>> generateQRCode(@RequestParam String orderCode) {
        try {
            log.info("Received request to generate QR code for orderCode: {}", orderCode);

            QRCodeResponse qrCodeResponse = orderService.generateAndSendQRCode(orderCode);

            return ResponseEntity.ok(
                    ApiResponse.success(qrCodeResponse, "QR code generated and sent to robot successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error generating QR code for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error generating QR code for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @PostMapping("/pickup/send-otp")
    public ResponseEntity<ApiResponse<PickupResponse>> sendPickupOtp(@Valid @RequestBody PickupOtpRequest request) {
        try {
            log.info("Received request to send pickup OTP for order code: {}", request.getOrderCode());

            String userEmail = pickupOtpService.generateAndSendPickupOtp(request.getOrderCode());

            PickupResponse data = PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .status("OTP_SENT")
                    .otpSentTo(maskEmail(userEmail))
                    .verified(false)
                    .build();

            return ResponseEntity.ok(
                    ApiResponse.success(data, "OTP has been sent to your email address")
            );

        } catch (RuntimeException e) {
            log.error("Error sending pickup OTP for order code {}: {}", request.getOrderCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error sending pickup OTP for order code {}: {}", request.getOrderCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @PostMapping("/pickup/verify-otp")
    public ResponseEntity<ApiResponse<PickupResponse>> verifyPickupOtp(@Valid @RequestBody PickupVerifyOtpRequest request) {
        try {
            log.info("Received request to verify pickup OTP for order code: {} and trip code: {}",
                    request.getOrderCode(), request.getTripCode());

            boolean isValid = pickupOtpService.verifyPickupOtp(request.getOrderCode(), request.getOtp(), request.getTripCode());

            if (isValid) {
                // After successful OTP verification, complete the order and trip
                orderService.verifyOTPAndCompleteOrder(request.getOrderCode(), request.getOtp());

                PickupResponse data = PickupResponse.builder()
                        .orderCode(request.getOrderCode())
                        .status("PICKUP_VERIFIED")
                        .verified(true)
                        .build();

                return ResponseEntity.ok(
                        ApiResponse.success(data, "Order pickup verification completed successfully. Order and trip marked as completed.")
                );
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Invalid OTP or OTP has expired"));
            }

        } catch (RuntimeException e) {
            log.error("Error verifying pickup OTP for order code {}: {}", request.getOrderCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error verifying pickup OTP for order code {}: {}", request.getOrderCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    /**
     * Helper method to mask email address for privacy
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****@****.com";
        }

        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        String maskedUsername = username.length() > 2
                ? username.substring(0, 2) + "****"
                : "****";

        return maskedUsername + "@" + domain;
    }
}
