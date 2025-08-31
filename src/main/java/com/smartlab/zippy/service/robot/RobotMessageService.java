package com.smartlab.zippy.service.robot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.zippy.model.dto.robot.*;
import com.smartlab.zippy.service.trip.TripStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for handling incoming MQTT messages from robots
 * Updated to handle the new topic structure and payload formats
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RobotMessageService {

    private final ObjectMapper objectMapper;
    private final RobotDataService robotDataService;
    private final TripStatusService tripStatusService;

    /**
     * Handle robot location message
     * Payload format: {"roomCode": "String"}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleLocationMessage(String robotCode, String payload) {
        try {
            RobotLocationMqttDTO locationData = objectMapper.readValue(payload, RobotLocationMqttDTO.class);
            log.info("Robot {} location update - Room: {}", robotCode, locationData.getRoomCode());

            // Convert to existing location DTO format for compatibility
            RobotLocationDTO location = RobotLocationDTO.builder()
                    .roomCode(locationData.getRoomCode())
                    .build();

            robotDataService.updateLocation(robotCode, location);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse location payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle location message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot battery status message
     * Payload format: {"battery": <double>}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleBatteryMessage(String robotCode, String payload) {
        try {
            RobotBatteryMqttDTO batteryData = objectMapper.readValue(payload, RobotBatteryMqttDTO.class);
            log.info("Robot {} battery level: {}%", robotCode, batteryData.getBattery());

            // Convert to existing battery DTO format - both are now double
            RobotBatteryDTO battery = RobotBatteryDTO.builder()
                    .battery(batteryData.getBattery()) // Direct assignment - both are double
                    .build();

            robotDataService.updateBattery(robotCode, battery);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse battery payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle battery message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot status message
     * Payload format: {"status": "free" or "non-free"}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleStatusMessage(String robotCode, String payload) {
        try {
            RobotStatusMqttDTO statusData = objectMapper.readValue(payload, RobotStatusMqttDTO.class);
            log.info("Robot {} status: {}", robotCode, statusData.getStatus());

            // Convert to existing status DTO format for compatibility
            RobotStatusDTO status = RobotStatusDTO.builder()
                    .status(statusData.getStatus())
                    .build();

            // Use heartbeat handler for status messages to ensure proper online status tracking
            robotDataService.handleRobotHeartbeat(robotCode, status);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse status payload for robot {}: {}", robotCode, payload, e);

            // If payload parsing fails, still mark robot as online (simple heartbeat)
            robotDataService.handleRobotHeartbeat(robotCode);
        } catch (Exception e) {
            log.error("Failed to handle status message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot container status message
     * Payload format: {"status": "free" or "non-free", "isClosed": true or false}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleContainerMessage(String robotCode, String payload) {
        try {
            RobotContainerMqttDTO containerData = objectMapper.readValue(payload, RobotContainerMqttDTO.class);
            log.info("Robot {} container status: {}, isClosed: {}",
                    robotCode, containerData.getStatus(), containerData.isClosed());

            // Convert to existing container status DTO format for compatibility
            RobotContainerStatusDTO containerStatus = RobotContainerStatusDTO.builder()
                    .status(containerData.getStatus())
                    .isClosed(containerData.isClosed())
                    .build();

            // Update container status for all containers of this robot
            // Note: The original method expected containerCode, but new format doesn't specify it
            // We'll need to update all containers for this robot
            robotDataService.updateAllContainerStatuses(robotCode, containerStatus);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse container payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle container message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot trip message
     * Payload format: {"trip_id": "string", "progress": <double>, "status": <int>,
     *                  "start_point": "string", "end_point": "string"}
     * Status mapping: 0=Prepare, 1=Load, 2=OnGoing, 3=Delivered, 4=Finish
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleTripMessage(String robotCode, String payload) {
        try {
            RobotTripMqttDTO tripData = objectMapper.readValue(payload, RobotTripMqttDTO.class);
            log.info("Robot {} trip update - ID: {}, Progress: {}%, Status: {}, Start: {}, End: {}",
                    robotCode, tripData.getTrip_id(), tripData.getProgress(),
                    tripData.getStatus(), tripData.getStart_point(), tripData.getEnd_point());

            // Map status integer to string
            String statusString = mapTripStatusToString(tripData.getStatus());

            // Process the trip update using TripStatusService
            tripStatusService.updateTripStatusFromMqtt(tripData.getTrip_id(), tripData, statusString);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse trip payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle trip message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot QR code message
     * Payload format: {"qr-code": "base64", "status": <int>}
     * Status mapping: 0=Canceled, 1=Done, 2=Waiting
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleQrCodeMessage(String robotCode, String payload) {
        try {
            RobotQrCodeMqttDTO qrCodeData = objectMapper.readValue(payload, RobotQrCodeMqttDTO.class);
            String statusString = mapQrCodeStatusToString(qrCodeData.getStatus());

            log.info("Robot {} QR code update - Status: {} ({})",
                    robotCode, qrCodeData.getStatus(), statusString);

            // Handle QR code status updates
            handleQrCodeStatusUpdate(robotCode, qrCodeData, statusString);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse QR code payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle QR code message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot force move message
     * Payload format: {"end_point": "string"}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleForceMoveMessage(String robotCode, String payload) {
        try {
            RobotForceMoveDTO forceMoveData = objectMapper.readValue(payload, RobotForceMoveDTO.class);
            log.info("Robot {} force move command - Destination: {}",
                    robotCode, forceMoveData.getEndPoint());

            // Handle force move command
            handleForceMoveCommand(robotCode, forceMoveData);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse force move payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle force move message for robot {}", robotCode, e);
        }
    }

    /**
     * Handle robot warning message
     * Payload format: {"title": "string", "message": "string", "timestamp": "string"}
     *
     * @param robotCode Robot code
     * @param payload JSON payload
     */
    public void handleWarningMessage(String robotCode, String payload) {
        try {
            RobotWarningDTO warningData = objectMapper.readValue(payload, RobotWarningDTO.class);
            log.warn("Robot {} warning - Title: {}, Message: {}, Timestamp: {}",
                    robotCode, warningData.getTitle(), warningData.getMessage(), warningData.getTimestamp());

            // Handle robot warning
            handleRobotWarning(robotCode, warningData);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse warning payload for robot {}: {}", robotCode, payload, e);
        } catch (Exception e) {
            log.error("Failed to handle warning message for robot {}", robotCode, e);
        }
    }

    // Legacy methods for backward compatibility
    /**
     * @deprecated Use handleContainerMessage instead
     */
    @Deprecated
    public void handleContainerStatus(String robotId, String containerCode, String payload) {
        handleContainerMessage(robotId, payload);
    }

    /**
     * @deprecated Use handleLocationMessage instead
     */
    @Deprecated
    public void handleLocation(String robotId, String payload) {
        handleLocationMessage(robotId, payload);
    }

    /**
     * @deprecated Use handleBatteryMessage instead
     */
    @Deprecated
    public void handleBattery(String robotId, String payload) {
        handleBatteryMessage(robotId, payload);
    }

    /**
     * @deprecated Use handleStatusMessage instead
     */
    @Deprecated
    public void handleStatus(String robotId, String payload) {
        handleStatusMessage(robotId, payload);
    }

    /**
     * @deprecated Use handleTripMessage instead
     */
    @Deprecated
    public void handleTripStatus(String robotId, String tripId, String payload) {
        handleTripMessage(robotId, payload);
    }

    /**
     * Handle robot connection lost event
     *
     * @param robotCode Robot code
     */
    public void handleConnectionLost(String robotCode) {
        log.warn("Robot {} connection lost", robotCode);
        robotDataService.markRobotOffline(robotCode);
    }

    // Helper methods
    private String mapTripStatusToString(int status) {
        return switch (status) {
            case 0 -> "PREPARE";
            case 1 -> "LOAD";
            case 2 -> "ONGOING";
            case 3 -> "DELIVERED";
            case 4 -> "FINISHED";
            default -> "UNKNOWN";
        };
    }

    private String mapQrCodeStatusToString(int status) {
        return switch (status) {
            case 0 -> "CANCELED";
            case 1 -> "DONE";
            case 2 -> "WAITING";
            default -> "UNKNOWN";
        };
    }

    private void handleQrCodeStatusUpdate(String robotCode, RobotQrCodeMqttDTO qrCodeData, String statusString) {
        // TODO: Implement QR code status handling logic
        // This could involve updating order status, notifying users, etc.
        log.info("Processing QR code status update for robot {}: {}", robotCode, statusString);
    }

    private void handleForceMoveCommand(String robotCode, RobotForceMoveDTO forceMoveData) {
        // TODO: Implement force move command handling logic
        // This could involve creating emergency trips, updating robot status, etc.
        log.info("Processing force move command for robot {} to {}", robotCode, forceMoveData.getEndPoint());
    }

    private void handleRobotWarning(String robotCode, RobotWarningDTO warningData) {
        // TODO: Implement warning handling logic
        // This could involve alerting administrators, logging to monitoring systems, etc.
        log.warn("Processing warning from robot {}: {} - {}",
                robotCode, warningData.getTitle(), warningData.getMessage());
    }
}
