package com.smartlab.zippy.service.pickup;

import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.robot.ContainerCmdDTO;
import com.smartlab.zippy.model.dto.web.request.pickup.SendOtpRequest;
import com.smartlab.zippy.model.dto.web.request.pickup.VerifyOtpRequest;
import com.smartlab.zippy.model.dto.web.response.pickup.PickupResponse;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.PickupOtp;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.PickupOtpRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.service.email.EmailService;
import com.smartlab.zippy.service.mqtt.MqttPublisherImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class PickupService {

    private final OrderRepository orderRepository;
    private final TripRepository tripRepository;
    private final PickupOtpRepository pickupOtpRepository;
    private final EmailService emailService;
    private final MqttPublisherImpl publisher;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;

    @Transactional
    public PickupResponse sendOtp(SendOtpRequest request) {
        try {
            log.info("Processing OTP send request for orderCode: {}, tripCode: {}",
                request.getOrderCode(), request.getTripCode());

            // Validate order exists and is deliverable
            Optional<Order> orderOpt = orderRepository.findByOrderCode(request.getOrderCode());
            if (orderOpt.isEmpty()) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("ORDER_NOT_FOUND")
                    .build();
            }

            Order order = orderOpt.get();

            // Validate trip exists and matches order
            Optional<Trip> tripOpt = tripRepository.findByTripCode(request.getTripCode());
            if (tripOpt.isEmpty()) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("TRIP_NOT_FOUND")
                    .build();
            }

            Trip trip = tripOpt.get();

            // Validate trip belongs to order
            if (!order.getTripId().equals(trip.getId())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("TRIP_MISMATCH")
                    .build();
            }

            // Check if order is already completed
            if ("COMPLETED".equals(order.getStatus())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("ALREADY_PICKED_UP")
                    .build();
            }

            // Check if trip is in the right status for pickup (DELIVERED or FINISHED)
            if (!"LOADING".equals(trip.getStatus()) && !"FINISHED".equals(trip.getStatus())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("NOT_READY_FOR_PICKUP")
                    .build();
            }

            // Generate and save OTP
            String otpCode = generateOtpCode();
            User receiver = order.getReceiver();

            PickupOtp pickupOtp = PickupOtp.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .otpCode(otpCode)
                .email(receiver.getEmail())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();

            pickupOtpRepository.save(pickupOtp);

            // Send OTP via email
            boolean emailSent = sendOtpEmail(receiver.getEmail(), otpCode, order.getOrderCode());

            if (!emailSent) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("EMAIL_SEND_FAILED")
                    .build();
            }

            log.info("OTP sent successfully for orderCode: {}, tripCode: {} to email: {}",
                request.getOrderCode(), request.getTripCode(), receiver.getEmail());

            return PickupResponse.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .status("OTP_SENT")
                .build();

        } catch (Exception e) {
            log.error("Error sending OTP for orderCode: {}, tripCode: {}",
                request.getOrderCode(), request.getTripCode(), e);

            return PickupResponse.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .status("SERVER_ERROR")
                .build();
        }
    }

    @Transactional
    public PickupResponse verifyOtpToLoadingAndPickingItems(VerifyOtpRequest request) {
        log.info("Verify OTP Request: {}", request);

        try {
            PickupOtp otp = pickupOtpRepository.findUnverifiedOtp(
                            request.getOrderCode(), request.getTripCode(), request.getOtp())
                    .orElse(null);

            if (otp == null) {
                return buildResponse(false, "Invalid or expired OTP", request, "INVALID_OTP");
            }

            if (otp.isExpired()) {
                return buildResponse(false, "OTP has expired", request, "OTP_EXPIRED");
            }

            otp.setVerified(true);
            otp.setVerifiedAt(LocalDateTime.now());
            pickupOtpRepository.save(otp);

            Trip trip = tripRepository.findByTripCode(request.getTripCode()).orElse(null);
            if (trip == null) {
                return buildResponse(false, "Trip not found", request, "TRIP_NOT_FOUND");
            }

            if (isContainerOpenEligible(trip)) {
                publisher.publishContainerCmd(
                        trip.getRobot().getCode(),
                        new ContainerCmdDTO(0)
                );
                publisher.publishQrCodeCommand(trip.getRobot().getCode(), null, 0);
                return buildResponse(true, "Open container command sent to robot", request, "OTP_VERIFIED");
            }


            return buildResponse(true, "OTP verified successfully", request, "OTP_VERIFIED");

        } catch (Exception e) {
            log.error("Error verifying OTP for orderCode: {}, tripCode: {}",
                    request.getOrderCode(), request.getTripCode(), e);
            return buildResponse(false, "Internal server error", request, "SERVER_ERROR");
        }
    }

    @Transactional
    public PickupResponse resendOtp(SendOtpRequest request) {
        try {
            log.info("Processing OTP resend request for orderCode: {}, tripCode: {}",
                request.getOrderCode(), request.getTripCode());

            // Rate limiting: Check if too many OTPs were sent recently (within last 2 minutes)
            LocalDateTime rateLimitWindow = LocalDateTime.now().minusMinutes(2);
            long recentOtpCount = pickupOtpRepository.countOtpsCreatedSince(
                request.getOrderCode(), request.getTripCode(), rateLimitWindow);

            if (recentOtpCount >= 3) { // Max 3 OTPs per 2 minutes
                log.warn("Rate limit exceeded for orderCode: {}, tripCode: {}",
                    request.getOrderCode(), request.getTripCode());
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("RATE_LIMIT_EXCEEDED")
                    .build();
            }

            // Validate order exists and is deliverable
            Optional<Order> orderOpt = orderRepository.findByOrderCode(request.getOrderCode());
            if (orderOpt.isEmpty()) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("ORDER_NOT_FOUND")
                    .build();
            }

            Order order = orderOpt.get();

            // Validate trip exists and matches order
            Optional<Trip> tripOpt = tripRepository.findByTripCode(request.getTripCode());
            if (tripOpt.isEmpty()) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("TRIP_NOT_FOUND")
                    .build();
            }

            Trip trip = tripOpt.get();

            // Validate trip belongs to order
            if (!order.getTripId().equals(trip.getId())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("TRIP_MISMATCH")
                    .build();
            }

            // Check if order is already completed
            if ("COMPLETED".equals(order.getStatus())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("ALREADY_PICKED_UP")
                    .build();
            }

            // Check if trip is in the right status for pickup (LOADING or FINISHED)
            if (!"LOADING".equals(trip.getStatus()) && !"FINISHED".equals(trip.getStatus())) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("NOT_READY_FOR_PICKUP")
                    .build();
            }

            // Check if there's already a valid unverified OTP
            Optional<PickupOtp> existingOtpOpt = pickupOtpRepository.findLatestUnverifiedOtp(
                request.getOrderCode(), request.getTripCode());

            if (existingOtpOpt.isPresent() && !existingOtpOpt.get().isExpired()) {
                // Resend the existing valid OTP instead of generating a new one
                PickupOtp existingOtp = existingOtpOpt.get();
                User receiver = order.getReceiver();

                boolean emailSent = sendOtpEmail(receiver.getEmail(), existingOtp.getOtpCode(), order.getOrderCode());

                if (!emailSent) {
                    return PickupResponse.builder()
                        .orderCode(request.getOrderCode())
                        .tripCode(request.getTripCode())
                        .status("EMAIL_SEND_FAILED")
                        .build();
                }

                log.info("Existing OTP resent successfully for orderCode: {}, tripCode: {} to email: {}",
                    request.getOrderCode(), request.getTripCode(), receiver.getEmail());

                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("OTP_RESENT")
                    .build();
            }

            // Generate and save new OTP if no valid existing OTP
            String otpCode = generateOtpCode();
            User receiver = order.getReceiver();

            PickupOtp pickupOtp = PickupOtp.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .otpCode(otpCode)
                .email(receiver.getEmail())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();

            pickupOtpRepository.save(pickupOtp);

            // Send OTP via email
            boolean emailSent = sendOtpEmail(receiver.getEmail(), otpCode, order.getOrderCode());

            if (!emailSent) {
                return PickupResponse.builder()
                    .orderCode(request.getOrderCode())
                    .tripCode(request.getTripCode())
                    .status("EMAIL_SEND_FAILED")
                    .build();
            }

            log.info("New OTP generated and sent for orderCode: {}, tripCode: {} to email: {}",
                request.getOrderCode(), request.getTripCode(), receiver.getEmail());

            return PickupResponse.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .status("OTP_RESENT")
                .build();

        } catch (Exception e) {
            log.error("Error resending OTP for orderCode: {}, tripCode: {}",
                request.getOrderCode(), request.getTripCode(), e);

            return PickupResponse.builder()
                .orderCode(request.getOrderCode())
                .tripCode(request.getTripCode())
                .status("SERVER_ERROR")
                .build();
        }
    }

    private boolean isContainerOpenEligible(Trip trip) {
        return "LOADING".equals(trip.getStatus()) || "FINISHED".equals(trip.getStatus());
    }

    private PickupResponse buildResponse(boolean success, String message, VerifyOtpRequest req, String status) {
        return PickupResponse.builder()
                .orderCode(req.getOrderCode())
                .tripCode(req.getTripCode())
                .status(status)
                .build();
    }

    private String generateOtpCode() {
        Random random = new Random();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    private boolean sendOtpEmail(String email, String otpCode, String orderCode) {
        try {
            String subject = "Zippy Pickup Verification - Order " + orderCode;
            String content = String.format(
                "Your OTP for order pickup verification is: %s\n\n" +
                "This OTP will expire in %d minutes.\n\n" +
                "If you did not request this pickup, please ignore this email.",
                otpCode, OTP_EXPIRY_MINUTES
            );

            emailService.sendSimpleMessage(email, subject, content);
            return true;
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", email, e);
            return false;
        }
    }

    @Transactional
    public void cleanupExpiredOtps() {
        try {
            pickupOtpRepository.deleteExpiredOtps(LocalDateTime.now());
            log.debug("Cleaned up expired OTPs");
        } catch (Exception e) {
            log.error("Error cleaning up expired OTPs", e);
        }
    }
}
