package com.smartlab.zippy.service.order;

import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.repository.UserRepository;
import com.smartlab.zippy.service.auth.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickupOtpService {

    private final OtpService otpService;
    private final OrderRepository orderRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    /**
     * Generate and send OTP for order pickup
     * @param orderCode Order code to generate OTP for
     * @return User's email address
     */
    public String generateAndSendPickupOtp(String orderCode) {
        log.info("Generating pickup OTP for order code: {}", orderCode);

        // Find the order
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Order not found with code: " + orderCode));

        // Check if order is in a valid state for pickup
        if (!"DELIVERED".equals(order.getStatus())) {
            throw new RuntimeException("Order is not ready for pickup. Current status: " + order.getStatus());
        }

        // Find the user associated with the order
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found for order"));

        // Generate OTP using orderCode as key instead of email
        String otp = otpService.generateOtp(orderCode);

        // Send OTP via email to the user
        otpService.sendOtp(user.getEmail(), otp);

        log.info("Pickup OTP sent successfully for order code: {}", orderCode);

        return user.getEmail();
    }

    /**
     * Verify pickup OTP for order
     * @param orderCode Order code
     * @param otp OTP to verify
     * @param tripCode Trip code for direct trip lookup
     * @return true if verification successful
     */
    @Transactional
    public boolean verifyPickupOtp(String orderCode, String otp, String tripCode) {
        log.info("Verifying pickup OTP for order code: {} and trip code: {}", orderCode, tripCode);

        // Find the order to ensure it exists
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Order not found with code: " + orderCode));

        // Verify OTP using orderCode as key
        boolean isValid = otpService.validateOtp(orderCode, otp);

        if (isValid) {
            log.info("Pickup OTP verification successful for order code: {}", orderCode);

            // Update order status to FINISHED after successful OTP verification
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("Order status updated to FINISHED for order code: {}", orderCode);

            // Update trip status to COMPLETED using direct tripCode lookup for better performance
            Trip trip = tripRepository.findByTripCode(tripCode)
                    .orElseThrow(() -> new RuntimeException("Trip not found with code: " + tripCode));

            trip.setStatus("COMPLETED");
            tripRepository.save(trip);
            log.info("Trip status updated to COMPLETED for trip code: {} related to order code: {}",
                    tripCode, orderCode);
        } else {
            log.warn("Pickup OTP verification failed for order code: {}", orderCode);
        }

        return isValid;
    }
}
