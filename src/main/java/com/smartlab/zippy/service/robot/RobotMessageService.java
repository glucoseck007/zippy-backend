package com.smartlab.zippy.service.robot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.dto.trip.TripProgressDTO;
import com.smartlab.zippy.service.trip.TripStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for handling incoming MQTT messages from robots
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RobotMessageService {

    private final ObjectMapper objectMapper;
    private final RobotDataService robotDataService;
    private final TripStatusService tripStatusService;

    /**
     * Handle container status message from robot
     *
     * @param robotId Robot ID
     * @param containerCode Container code
     * @param payload JSON payload
     */
    public void handleContainerStatus(String robotId, String containerCode, String payload) {
        try {
            RobotContainerStatusDTO containerStatus = parseContainerStatus(payload);
            robotDataService.updateContainerStatus(robotId, containerCode, containerStatus);
            log.debug("Updated container status for robot {} container {}: {}", robotId, containerCode, containerStatus);
        } catch (Exception e) {
            log.error("Failed to handle container status for robot {} container {}", robotId, containerCode, e);
        }
    }

    /**
     * Handle robot location message
     *
     * @param robotId Robot ID
     * @param payload JSON payload
     */
    public void handleLocation(String robotId, String payload) {
        try {
            RobotLocationDTO location = objectMapper.readValue(payload, RobotLocationDTO.class);
            log.info("Robot {} location: lat={}, lon={}", robotId, location.getLat(), location.getLon());
            robotDataService.updateLocation(robotId, location);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse location payload: {}", payload, e);
        }
    }

    /**
     * Handle robot battery status message
     *
     * @param robotId Robot ID
     * @param payload JSON payload
     */
    public void handleBattery(String robotId, String payload) {
        try {
            RobotBatteryDTO battery = objectMapper.readValue(payload, RobotBatteryDTO.class);
            log.info("Robot {} battery: {}", robotId, battery.getBattery());
            robotDataService.updateBattery(robotId, battery);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse battery payload: {}", payload, e);
        }
    }

    /**
     * Handle robot status message
     *
     * @param robotId Robot ID
     * @param payload JSON payload
     */
    public void handleStatus(String robotId, String payload) {
        try {
            RobotStatusDTO status = objectMapper.readValue(payload, RobotStatusDTO.class);
            log.info("Robot {} status: {}", robotId, status.getStatus());

            // Use heartbeat handler for status messages (sent every 5 minutes)
            // This ensures proper online status tracking
            robotDataService.handleRobotHeartbeat(robotId, status);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse status payload: {}", payload, e);

            // If payload parsing fails, still mark robot as online (simple heartbeat)
            // This handles cases where robots send simple status pings
            robotDataService.handleRobotHeartbeat(robotId);
        }
    }

    /**
     * Handle robot trip status message
     * This method processes trip updates from MQTT with format:
     * Topic: robot/{robotId}/trip/{tripId}
     * Payload: {progress: double, start_point: roomCode, end_point: roomCode}
     *
     * @param robotId Robot ID
     * @param tripId Trip ID (tripCode)
     * @param payload JSON payload
     */
    public void handleTripStatus(String robotId, String tripId, String payload) {
        try {
            log.info("Robot {} trip {} status update: {}", robotId, tripId, payload);

            // Process the trip update using the method in TripStatusService
            // This will check start_point/end_point matching and update status based on progress
            tripStatusService.updateTripStatus(tripId, payload);
        } catch (Exception e) {
            log.error("Failed to handle trip status for robot {} trip {}", robotId, tripId, e);
        }
    }

    /**
     * Handle robot connection lost event
     *
     * @param robotId Robot ID
     */
    public void handleConnectionLost(String robotId) {
        log.warn("Robot {} connection lost", robotId);
        robotDataService.markRobotOffline(robotId);
    }

    private RobotContainerStatusDTO parseContainerStatus(String payload) throws JsonProcessingException {
        return objectMapper.readValue(payload, RobotContainerStatusDTO.class);
    }
}
