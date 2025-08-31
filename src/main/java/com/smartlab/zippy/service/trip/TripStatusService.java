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
import com.smartlab.zippy.model.dto.robot.RobotTripMqttDTO;

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
     * Update trip status from MQTT message with new payload format
     * Payload format: {"trip_id": "string", "progress": <double>, "status": <int>,
     *                  "start_point": "string", "end_point": "string"}
     *
     * @param tripId Trip ID from MQTT message
     * @param tripData Trip data from MQTT
     * @param statusString Mapped status string
     */
    @Transactional
    public void updateTripStatusFromMqtt(String tripId, Object tripData, String statusString) {
        try {
            // Handle both old string payload and new DTO format
            double progress = 0.0;
            String startPoint = null;
            String endPoint = null;

            if (tripData instanceof RobotTripMqttDTO tripDto) {
                progress = tripDto.getProgress();
                startPoint = tripDto.getStart_point();
                endPoint = tripDto.getEnd_point();
            } else if (tripData instanceof String) {
                // Legacy support for string payload
                JsonNode jsonNode = objectMapper.readTree((String) tripData);
                progress = jsonNode.get("progress").asDouble();
                if (jsonNode.has("start_point")) {
                    startPoint = jsonNode.get("start_point").asText();
                }
                if (jsonNode.has("end_point")) {
                    endPoint = jsonNode.get("end_point").asText();
                }
            }

            log.info("Updating trip {} with progress: {}%, status: {}, start: {}, end: {}",
                    tripId, progress, statusString, startPoint, endPoint);

            // Store progress in cache
            storeTripProgressInCache(tripId, progress);

            // Find trip by trip code or trip ID
            Optional<Trip> tripOpt = tripRepository.findByTripCode(tripId);
            if (tripOpt.isEmpty()) {
                log.warn("Trip not found with code or ID: {}", tripId);
            }

            if (tripOpt.isPresent()) {
                Trip trip = tripOpt.get();

                // Update trip details if provided
                boolean updated = false;
                if (startPoint != null && !startPoint.equals(trip.getStartPoint())) {
                    trip.setStartPoint(startPoint);
                    updated = true;
                }
                if (endPoint != null && !endPoint.equals(trip.getEndPoint())) {
                    trip.setEndPoint(endPoint);
                    updated = true;
                }

                // Update status based on mapped status string
                String currentStatus = trip.getStatus();
                String newStatus = mapMqttStatusToTripStatus(statusString, progress);
                if (!newStatus.equals(currentStatus)) {
                    trip.setStatus(newStatus);
                    updated = true;
                    log.info("Trip {} status changed from {} to {}", tripId, currentStatus, newStatus);
                }

                if (updated) {
                    tripRepository.save(trip);
                    log.info("Updated trip {} in database", tripId);
                }
            } else {
                log.warn("Trip not found with ID: {}", tripId);
            }

        } catch (Exception e) {
            log.error("Failed to update trip status from MQTT for trip: {}", tripId, e);
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
     * Map MQTT status to internal trip status
     * Status mapping: 0=Prepare, 1=Load, 2=OnGoing, 3=Delivered, 4=Finish
     *
     * @param mqttStatus Status string from MQTT mapping
     * @param progress Progress value to help determine status
     * @return Internal trip status
     */
    private String mapMqttStatusToTripStatus(String mqttStatus, double progress) {
        return switch (mqttStatus) {
            case "PREPARE" -> "PREPARED";
            case "LOAD" -> "LOADING";
            case "ONGOING" -> "ACTIVE";
            case "DELIVERED" -> "DELIVERED";
            case "FINISHED" -> "COMPLETED";
            default -> {
                // Fallback to progress-based status determination
                if (progress == 0.0) {
                    yield "PENDING";
                } else if (progress >= 100.0) {
                    yield "DELIVERED";
                } else {
                    yield "ACTIVE";
                }
            }
        };
    }

    public void completeTripByOtpVerification(String tripCode) {
        try {
            Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
            if (tripOpt.isEmpty()) {
                log.warn("Trip not found with code: {}", tripCode);
                return;
            }

            Trip trip = tripOpt.get();
            if ("COMPLETED".equals(trip.getStatus())) {
                log.info("Trip {} is already completed", tripCode);
                return;
            }

            // Update trip status to COMPLETED
            trip.setStatus("COMPLETED");
            tripRepository.save(trip);
            log.info("Trip {} marked as COMPLETED", tripCode);

            // Update associated orders to DELIVERED
            List<Order> orders = orderRepository.findByTripId(trip.getId());
            for (Order order : orders) {
                if (!"DELIVERED".equals(order.getStatus())) {
                    order.setStatus("DELIVERED");
                    orderRepository.save(order);
                    log.info("Order {} associated with trip {} marked as DELIVERED", order.getId(), tripCode);
                }
            }

        } catch (Exception e) {
            log.error("Failed to complete trip by OTP verification for trip: {}", tripCode, e);
        }
    }
}
