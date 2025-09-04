package com.smartlab.zippy.service.robot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.zippy.component.RobotStatusCache;
import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.robot.*;
import com.smartlab.zippy.model.dto.trip.TripStateMqttDTO;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Product;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.ProductRepository;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.service.qr.QRCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RobotMessageService {

    private final ObjectMapper objectMapper;
    private final TripRepository tripRepository;
    private final RobotRepository robotRepository;
    private final RobotStatusCache robotStatusCache;
    private final ApplicationEventPublisher eventPublisher;
    private final QRCodeService qrCodeService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MqttCommandPublisher mqttCommandPublisher;

    // Map to track the last QR code publishing time for each robot-trip combination
    private final Map<String, LocalDateTime> qrCodePublishingTracker = new ConcurrentHashMap<>();

    // QR code cooldown period in seconds
    private static final long QR_CODE_COOLDOWN_SECONDS = 30;

    private boolean isExistRobot(String robotCode) {
        Optional<Robot> robotOptional = robotRepository.findByCode(robotCode);
        return robotOptional.isPresent();
    }

    public boolean isRobotFree(String robotCode) {
        return robotStatusCache.isFree(robotCode);
    }

    public boolean isAlive(String robotCode) {
        return robotStatusCache.isAlive(robotCode);
    }

    @Transactional
    public void handleBattery(String robotCode, String payload) {
        log.info("handleBattery robotCode:{}, payload:{}", robotCode, payload);

        try {
            double batteryLevel = Double.parseDouble(payload);

            if (!isExistRobot(robotCode)) {
                return;
            }

            Optional<Robot> robotOptional = robotRepository.findByCode(robotCode);
            if (robotOptional.isPresent()) {
                Robot robot = robotOptional.get();
                robot.setBatteryStatus(batteryLevel);
                robotRepository.save(robot);
                log.info("Robot {} battery level updated to {}", robotCode, batteryLevel);
            }
        } catch (NumberFormatException e) {
            log.error("Failed to parse battery level for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle battery message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void handleLocation(String robotCode, String payload) {
        try {
            log.info("Processing location update for robot: {} with payload: {}", robotCode, payload);

            // Parse the JSON payload
            RobotLocationMqttDTO locationData = objectMapper.readValue(payload, RobotLocationMqttDTO.class);

            // Find the robot by code
            Optional<Robot> robotOptional = robotRepository.findByCode(robotCode);

            if (robotOptional.isEmpty()) {
                log.error("Robot with code {} not found in database", robotCode);
                return;
            }

            Robot robot = robotOptional.get();
            String newRoomCode = locationData.getRoomCode();
            String currentRoomCode = robot.getRoomCode();

            // Update robot location
            robot.setRoomCode(newRoomCode);
            robot.setLocationRealtime(newRoomCode); // Also update real-time location

            // Save to database
            robotRepository.save(robot);

            // Log the location change
            if (!newRoomCode.equals(currentRoomCode)) {
                log.info("Robot {} moved from room '{}' to room '{}'",
                        robotCode, currentRoomCode, newRoomCode);
            } else {
                log.debug("Robot {} location confirmed at room '{}'", robotCode, newRoomCode);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse location payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle location message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void handleStatus(String robotCode, String payload) {
        try {
            log.info("Processing status update for robot: {} with payload: {}", robotCode, payload);

            // Parse the JSON payload
            RobotStatusMqttDTO statusData = objectMapper.readValue(payload, RobotStatusMqttDTO.class);

            if (!isExistRobot(robotCode)) {
                return;
            }

            robotStatusCache.updateStatus(robotCode, statusData.getStatus());

            log.info("Robot {} status updated to '{}'", robotCode, statusData.getStatus());

            // Check if robot becomes available and trigger dequeue
            boolean isRobotAlive = isAlive(robotCode);
            boolean isRobotFree = isRobotFree(robotCode);

            log.debug("Robot {} - isAlive: {}, isFree: {}", robotCode, isRobotAlive, isRobotFree);

            if (isRobotAlive && isRobotFree) {
                log.info("Robot {} is now available, publishing dequeue event", robotCode);
                eventPublisher.publishEvent(new RobotStatusChangedEvent(this, robotCode, true));
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse status payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle status message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void handleContainerStatus(String robotCode, String payload) {
        try {
            log.info("Processing container update for robot: {} with payload: {}", robotCode, payload);

            RobotContainerMqttDTO containerData = objectMapper.readValue(payload, RobotContainerMqttDTO.class);

            if (!isExistRobot(robotCode)) {
                return;
            }

            robotStatusCache.updateContainerStatus(robotCode, containerData);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void handleQRCode(String robotCode, String payload) {
        try {
            log.info("Processing QR code update for robot: {} with payload: {}", robotCode, payload);

            RobotQrCodeMqttDTO qrCodeData = objectMapper.readValue(payload, RobotQrCodeMqttDTO.class);

            if (!isExistRobot(robotCode)) {
                return;
            }

            robotStatusCache.updateQrCode(robotCode, qrCodeData);

        } catch (Exception e) {
            log.error("Failed to handle QR code message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void handleHeartbeat(String robotCode, String payload) {
        try {
            log.info("Processing heartbeat update for robot: {} with payload: {}", robotCode, payload);

            RobotHeartbeatMqttDTO heart = objectMapper.readValue(payload, RobotHeartbeatMqttDTO.class);

            if (!isExistRobot(robotCode)) {
                return;
            }

            robotStatusCache.updateHeartbeat(robotCode, heart);

            // Check if robot becomes available and trigger dequeue
            boolean isRobotAlive = isAlive(robotCode);
            boolean isRobotFree = isRobotFree(robotCode);

            log.debug("Robot {} heartbeat - isAlive: {}, isFree: {}", robotCode, isRobotAlive, isRobotFree);

            if (isRobotAlive && isRobotFree) {
                log.info("Robot {} heartbeat shows available, publishing dequeue event", robotCode);
                eventPublisher.publishEvent(new RobotStatusChangedEvent(this, robotCode, true));
            }

        } catch (Exception e) {
            log.error("Failed to handle heartbeat message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void handleTrip(String robotCode, String payload) {
        try {
            log.info("Processing trip update for robot: {} with payload: {}", robotCode, payload);

            TripStateMqttDTO tripCache = objectMapper.readValue(payload, TripStateMqttDTO.class);

            Trip trip = tripRepository.findByTripCode(tripCache.getTrip_id()).get();

            if (!isExistRobot(robotCode)) {
                return;
            }

            Optional<Robot> robotOptional = robotRepository.findByCode(robotCode);
            Robot robot = robotOptional.get();

            int status = tripCache.getStatus();

            switch (status) {
                case 0: // Prepare
                    robot.setLocationRealtime(tripCache.getStart_point());
                    robot.setRoomCode(tripCache.getStart_point());
                    trip.setStatus("PREPARE");
                    log.info("Progress: {}", tripCache.getProgress());
                    break;
                case 1: // Load
                    robot.setLocationRealtime(tripCache.getStart_point());
                    robot.setRoomCode(tripCache.getStart_point());
                    trip.setStatus("LOADING");
                    publishQRCode(robotCode, tripCache.getTrip_id());
                    log.info("Progress: {}", tripCache.getProgress());
                    break;
                case 2: // OnGoing
                    // Do nothing, keep current location
                    trip.setStatus("ONGOING");
                    log.info("Progress: {}", tripCache.getProgress());
                    break;
                case 3: // Delivered
                    robot.setLocationRealtime(tripCache.getEnd_point());
                    robot.setRoomCode(tripCache.getEnd_point());
                    trip.setStatus("DELIVERED");
                    log.info("Progress: {}", tripCache.getProgress());
                    break;
                case 4: // Finish
                    robot.setLocationRealtime(tripCache.getEnd_point());
                    robot.setRoomCode(tripCache.getEnd_point());
                    trip.setStatus("FINISHED");
                    publishQRCode(robotCode, tripCache.getTrip_id());
                    log.info("Progress: {}", tripCache.getProgress());
                    break;
                default:
                    log.warn("Unknown tripCache status {} for robot {}", status, robotCode);
            }
            robotStatusCache.updateTrip(robotCode, tripCache);
            robotRepository.save(robot);
            tripRepository.save(trip);
        } catch (Exception e) {
            log.error("Failed to handle trip message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    public void handleTripState(String robotCode, String payload) {
        try {
            log.info("Processing trip state update for robot: {} with payload: {}", robotCode, payload);

            TripStateMqttDTO tripState = objectMapper.readValue(payload, TripStateMqttDTO.class);

            if (!isExistRobot(robotCode)) {
                return;
            }

            // Currently, just log the trip state. Extend this method as needed.
            log.info("Robot {} trip state updated: trip_id={}, state={}",
                    robotCode, tripState.getTrip_id(), tripState.getProgress());

            robotStatusCache.updateTrip(robotCode, tripState);

        } catch (Exception e) {
            log.error("Failed to handle trip state message for robot {}: {}", robotCode, e.getMessage(), e);
        }
    }

    private void publishQRCode(String robotCode, String tripCode) {
        Order order = orderRepository.getOrderByTripCode(tripCode);
        if (order == null) {
            return;
        }
        Product product = productRepository.findById(order.getProductId()).get();
        String qrCodeBase64 = qrCodeService.generateQRCode(tripCode, order.getOrderCode(), product.getCode());
        RobotQrCodeMqttDTO robotQrCodeMqttDTO = new RobotQrCodeMqttDTO(qrCodeBase64, 2);

        // Cooldown logic: Check the last publishing time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastPublishedTime = qrCodePublishingTracker.get(robotCode + "_" + tripCode);

        if (lastPublishedTime == null || java.time.Duration.between(lastPublishedTime, now).getSeconds() >= QR_CODE_COOLDOWN_SECONDS) {
            // Publish the QR code command
            mqttCommandPublisher.publishQrCodeCommand(robotCode, qrCodeBase64, 2);

            // Update the last published time
            qrCodePublishingTracker.put(robotCode + "_" + tripCode, now);
            log.info("Published QR code for robot {}: {}", robotCode, qrCodeBase64);
        } else {
            log.info("Skipped QR code publishing for robot {}: cooldown active", robotCode);
        }
    }
}
