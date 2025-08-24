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

    /**
     * Handle trip status updates received from robots/devices
     *
     * @param robotId Robot ID sending the status update
     * @param tripCode Trip code
     * @param payload JSON payload containing progress information
     */
    @Transactional
    public void handleTripStatus(String robotId, String tripCode, String payload) {
        try {
            // Parse JSON payload to extract progress
            JsonNode jsonNode = objectMapper.readTree(payload);
            double progress = jsonNode.get("progress").asDouble();

            log.info("Received trip status for robot: {}, tripCode: {}, progress: {}", robotId, tripCode, progress);

            // Store progress in cache
            storeTripProgressInCache(tripCode, (Double) progress);

            // Update trip status based on progress
            updateTripStatusByProgress(tripCode, progress);

        } catch (Exception e) {
            log.error("Failed to process trip status message for robot: {}, tripCode: {}, payload: {}",
                    robotId, tripCode, payload, e);
        }
    }

    /**
     * Update trip status based on progress value
     * Logic: 0 = PENDING, >0 & <100 = ACTIVE, 100 = DELIVERED
     *
     * @param tripCode Trip code to update
     * @param progress Progress value (0-100)
     */
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
     *
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
     * Update trip progress and status
     *
     * @param tripCode Trip code
     * @param progress Progress percentage (0-100)
     */
    @Transactional
    public void updateTripProgress(String tripCode, Double progress) {
        try {
            log.info("Updating progress for trip: {}, progress: {}%", tripCode, progress);

            Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
            if (tripOpt.isPresent()) {
                Trip trip = tripOpt.get();

                // Cache the progress for real-time API access
                storeTripProgressInCache(tripCode, progress);

                // Update trip status based on progress
                updateTripStatusByProgress(tripCode, progress);

                log.info("Successfully updated progress for trip: {}", tripCode);
            } else {
                log.warn("Trip not found with code: {}", tripCode);
            }
        } catch (Exception e) {
            log.error("Failed to update progress for trip: {}", tripCode, e);
        }
    }

    /**
     * Store trip progress in Redis cache for real-time access
     *
     * @param tripCode Trip code
     * @param progress Progress value (0-100)
     */
    private void storeTripProgressInCache(String tripCode, Double progress) {
        try {
            String cacheKey = TRIP_PROGRESS_KEY_PREFIX + tripCode;
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(progress), PROGRESS_CACHE_TTL, TimeUnit.HOURS);
            log.debug("Cached progress for trip {}: {}%", tripCode, progress);
        } catch (Exception e) {
            log.error("Failed to cache trip progress for trip: {}", tripCode, e);
        }
    }

    /**
     * Get trip progress from Redis cache
     *
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
     *
     * @param trip Trip entity
     */
    private void synchronizeOrderStatusWithTrip(Trip trip) {
        try {
            String tripStatus = trip.getStatus();
            String tripCode = trip.getTripCode();

            // Business rule: If trip is DELIVERED, set associated order(s) to DELIVERED
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

    /**
     * Update trip status based on MQTT payload
     * This method implements the specific logic:
     * Scenario 1: If the start_point in payload matches the trip's start point in DB:
     *   - If progress = 0 → PENDING
     *   - If progress between 1-99 → ACTIVE
     *   - If progress = 100 → DELIVERED
     * Scenario 2: If end_point in payload matches the trip's start point in DB:
     *   - Mark as "PREPARED" (robot is getting ready to go to start point)
     *
     * @param tripCode Trip code from the MQTT topic
     * @param payload JSON payload with progress, start_point, and end_point
     */
    @Transactional
    public void updateTripStatus(String tripCode, String payload) {
        try {
            log.info("Processing trip status update for tripCode: {} with payload: {}", tripCode, payload);

            // Parse the JSON payload
            JsonNode jsonNode = objectMapper.readTree(payload);

            // Extract the relevant fields
            double progress = jsonNode.path("progress").asDouble(0);
            String payloadStartPoint = jsonNode.path("start_point").asText();
            String payloadEndPoint = jsonNode.path("end_point").asText();

            log.debug("Extracted progress: {}, start_point: {}, end_point: {}",
                     progress, payloadStartPoint, payloadEndPoint);

            // Find the trip in the database
            Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
            if (tripOpt.isEmpty()) {
                log.warn("Trip not found with code: {}", tripCode);
                return;
            }

            Trip trip = tripOpt.get();
            String tripStartPoint = trip.getStartPoint();

            // Cache the progress for real-time API access
            storeTripProgressInCache(tripCode, (double)progress);

            // SCENARIO 1: Check if the start point in the payload matches the trip's start point
            if (payloadStartPoint != null && payloadStartPoint.equals(tripStartPoint)) {
                log.info("Scenario 1: Start point match found for trip {}: {}", tripCode, payloadStartPoint);

                // Determine the new status based on progress
                String newStatus;
                if (progress == 0) {
                    newStatus = "PENDING";
                } else if (progress >= 1 && progress < 100) {
                    newStatus = "ACTIVE";
                } else if (progress == 100) {
                    newStatus = "DELIVERED";
                } else {
                    log.warn("Invalid progress value: {}. Must be between 0-100.", progress);
                    return;
                }

                // Update trip status if it has changed
                updateTripStatusIfChanged(trip, newStatus, progress);
                return;
            }

            // SCENARIO 2: Check if the end point in the payload matches the trip's start point
            if (payloadEndPoint != null && payloadEndPoint.equals(tripStartPoint)) {
                log.info("Scenario 2: End point match found for trip {}: {} - Robot is preparing",
                         tripCode, payloadEndPoint);

                // Mark trip as PREPARED - robot is getting ready to go to start point
                String newStatus = "PREPARED";

                // Only update if status is different and not already in a more advanced state
                String currentStatus = trip.getStatus();
                if (!"ACTIVE".equals(currentStatus) && !"DELIVERED".equals(currentStatus) &&
                    !newStatus.equals(currentStatus)) {

                    trip.setStatus(newStatus);
                    tripRepository.save(trip);
                    log.info("Trip {} status changed from {} to {} (preparing to reach start point)",
                            tripCode, currentStatus, newStatus);
                }
                return;
            }

            // If neither scenario matches, just log and do nothing
            log.info("No matching scenario for trip {}: payload start={}, end={}, trip start={}",
                    tripCode, payloadStartPoint, payloadEndPoint, tripStartPoint);

        } catch (Exception e) {
            log.error("Failed to process trip status update for tripCode: {}", tripCode, e);
        }
    }

    /**
     * Helper method to update trip status if changed and synchronize with orders
     */
    private void updateTripStatusIfChanged(Trip trip, String newStatus, double progress) {
        String currentStatus = trip.getStatus();
        if (!newStatus.equals(currentStatus)) {
            trip.setStatus(newStatus);

            // Set end time when trip is delivered
            if ("DELIVERED".equals(newStatus)) {
                trip.setEndTime(java.time.LocalDateTime.now());
            }

            tripRepository.save(trip);
            log.info("Trip {} status changed from {} to {} with progress: {}",
                    trip.getTripCode(), currentStatus, newStatus, progress);

            // Synchronize order status with trip status
            synchronizeOrderStatusWithTrip(trip);
        } else {
            log.debug("Trip {} status remains {} with progress: {}",
                    trip.getTripCode(), currentStatus, progress);
        }
    }
}
