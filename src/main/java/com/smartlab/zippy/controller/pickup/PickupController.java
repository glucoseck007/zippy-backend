package com.smartlab.zippy.controller.pickup;

import com.smartlab.zippy.model.dto.web.request.pickup.SendOtpRequest;
import com.smartlab.zippy.model.dto.web.request.pickup.VerifyOtpRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.pickup.PickupResponse;
import com.smartlab.zippy.service.pickup.PickupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order/pickup")
@RequiredArgsConstructor
@Slf4j
public class PickupController {

    private final PickupService pickupService;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<PickupResponse>> sendOtp(@RequestBody SendOtpRequest request) {
        PickupResponse pickupResponse = pickupService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(pickupResponse));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<PickupResponse>> resendOtp(@RequestBody SendOtpRequest request) {
        log.info("Received resend OTP request for orderCode: {}, tripCode: {}",
            request.getOrderCode(), request.getTripCode());

        try {
            PickupResponse response = pickupService.resendOtp(request);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error processing resend OTP request for orderCode: {}",
                request.getOrderCode(), e);

            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<PickupResponse>> verifyOtp(@RequestBody VerifyOtpRequest request) {
        log.info("Received verify OTP request for orderCode: {}, tripCode: {}",
            request.getOrderCode(), request.getTripCode());

        try {
            PickupResponse response = pickupService.verifyOtpToLoadingAndPickingItems(request);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error processing verify OTP request for orderCode: {}",
                request.getOrderCode(), e);

            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/cleanup-expired")
    public ResponseEntity<String> cleanupExpiredOtps() {
        log.info("Manual cleanup of expired OTPs requested");

        try {
            pickupService.cleanupExpiredOtps();
            return ResponseEntity.ok("Expired OTPs cleaned up successfully");
        } catch (Exception e) {
            log.error("Error during manual OTP cleanup", e);
            return ResponseEntity.internalServerError().body("Failed to cleanup expired OTPs");
        }
    }
}
