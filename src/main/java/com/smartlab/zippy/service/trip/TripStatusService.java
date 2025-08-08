package com.smartlab.zippy.service.trip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripStatusService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TRIP_PROGRESS_KEY_PREFIX = "trip:progress:";
    private static final long PROGRESS_CACHE_TTL = 24; // hours

    @Transactional
    public void handleTripStatus(String robotId, String tripCode, String payload) {
        try {
            // Parse JSON payload to extract progress
            JsonNode jsonNode = objectMapper.readTree(payload);
            double progress = jsonNode.get("progress").asDouble();

            log.info("Received trip status for robot: {}, tripCode: {}, progress: {}", robotId, tripCode, progress);

            // Store progress in cache
            storeTripProgressInCache(tripCode, progress);

            // Update trip status based on progress
            updateTripStatusByProgress(tripCode, progress);

        } catch (Exception e) {
            log.error("Failed to process trip status message for robot: {}, tripCode: {}, payload: {}",
                     robotId, tripCode, payload, e);
        }
    }

    @Transactional
    public void updateTripStatusByProgress(String tripCode, double progress) {
        log.info("Updating trip status for tripCode: {} with progress: {}", tripCode, progress);

        Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
        if (tripOpt.isEmpty()) {
            log.warn("Trip not found with code: {}", tripCode);
            return;
        }

        Trip trip = tripOpt.get();
        String currentTripStatus = trip.getStatus();

        // Trip status rules based ONLY on progress:
        // - progress = 0 → PENDING (regardless of order status)
        // - progress > 0 and < 100 → ACTIVE
        // - progress = 100 → DELIVERED

        String newTripStatus;

        if (progress == 0.0) {
            newTripStatus = "PENDING";
        } else if (progress >= 100.0) {
            newTripStatus = "DELIVERED";
        } else {
            newTripStatus = "ACTIVE";
        }

        // Update trip status if changed
        if (!newTripStatus.equals(currentTripStatus)) {
            trip.setStatus(newTripStatus);

            // Set end time when trip is delivered
            if ("DELIVERED".equals(newTripStatus)) {
                trip.setEndTime(java.time.LocalDateTime.now());
            }

            tripRepository.save(trip);
            log.info("Trip {} status changed from {} to {} with progress: {}", tripCode, currentTripStatus, newTripStatus, progress);
        } else {
            log.debug("Trip {} status remains {} with progress: {}", tripCode, currentTripStatus, progress);
        }

        // Synchronize order status based on trip status
        synchronizeOrderStatusWithTrip(trip);
    }

    /**
     * Complete a trip by setting its status to COMPLETED
     * This is used when OTP verification is successful
     * @param tripCode Trip code to complete
     */
    @Transactional
    public void completeTripByOtpVerification(String tripCode) {
        log.info("Completing trip by OTP verification for tripCode: {}", tripCode);

        Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
        if (tripOpt.isEmpty()) {
            log.warn("Trip not found with code: {}", tripCode);
            throw new RuntimeException("Trip not found with code: " + tripCode);
        }

        Trip trip = tripOpt.get();
        trip.setStatus("COMPLETED");
        trip.setEndTime(java.time.LocalDateTime.now());
        tripRepository.save(trip);

        log.info("Trip {} status set to COMPLETED after OTP verification", tripCode);
    }

    /**
     * Store trip progress in Redis cache
     * @param tripCode Trip code
     * @param progress Progress value
     */
    private void storeTripProgressInCache(String tripCode, double progress) {
        try {
            String cacheKey = TRIP_PROGRESS_KEY_PREFIX + tripCode;
            // Convert double to string to avoid serialization issues with StringRedisSerializer
            String progressValue = String.valueOf(progress);
            redisTemplate.opsForValue().set(cacheKey, progressValue, PROGRESS_CACHE_TTL, TimeUnit.HOURS);
            log.debug("Stored trip progress in cache for tripCode: {} with progress: {}", tripCode, progress);
        } catch (Exception e) {
            log.error("Failed to store trip progress in cache for tripCode: {}", tripCode, e);
        }
    }

    /**
     * Get trip progress from Redis cache
     * @param tripCode Trip code to get progress for
     * @return Trip progress as double value, 0.0 if not found
     */
    public double getTripProgress(String tripCode) {
        try {
            log.info("Getting trip progress from cache for tripCode: {}", tripCode);

            String cacheKey = TRIP_PROGRESS_KEY_PREFIX + tripCode;
            Object cachedProgress = redisTemplate.opsForValue().get(cacheKey);

            if (cachedProgress != null) {
                double progress = Double.parseDouble(cachedProgress.toString());
                log.debug("Retrieved trip progress from cache for tripCode: {} with progress: {}", tripCode, progress);
                return progress;
            } else {
                log.warn("No progress found in cache for tripCode: {}", tripCode);
                return 0.0;
            }
        } catch (Exception e) {
            log.error("Failed to retrieve trip progress from cache for tripCode: {}", tripCode, e);
            return 0.0;
        }
    }

    /**
     * Synchronize order status with trip status based on business rules
     * @param trip Trip entity
     */
    private void synchronizeOrderStatusWithTrip(Trip trip) {
        try {
            String tripStatus = trip.getStatus();
            String tripCode = trip.getTripCode();

            // Business rule: If trip is DELIVERED, set associated order(s) to COMPLETED
            if ("DELIVERED".equals(tripStatus)) {
                List<Order> orders = orderRepository.findByTripCode(tripCode);
                for (Order order : orders) {
                    if (!"DELIVERED".equals(order.getStatus())) {
                        order.setStatus("DELIVERED");
                        orderRepository.save(order);
                        log.info("Order {} status set to DELIVERED because trip {} is DELIVERED", order.getId(), tripCode);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to synchronize order status with trip status for tripCode: {}", trip.getTripCode(), e);
        }
    }
}
