package com.smartlab.zippy.controller.robot;

import com.smartlab.zippy.exception.constant.ErrorCode;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.robot.LocationResponse;
import com.smartlab.zippy.model.dto.web.response.robot.BatteryResponse;
import com.smartlab.zippy.model.dto.web.response.robot.StatusResponse;
import com.smartlab.zippy.model.dto.web.response.robot.ContainerStatusResponse;
import com.smartlab.zippy.service.robot.RobotDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for retrieving robot information with intelligent cache/database fallback
 */
@RestController
@RequestMapping("/api/robot/message/{robotCode}")
public class RobotMessageController {

    private final RobotDataService robotDataService;

    public RobotMessageController(RobotDataService robotDataService) {
        this.robotDataService = robotDataService;
    }

    /**
     * Get container status for a specific robot container
     *
     * @param robotCode      Robot ID
     * @param containerCode Container Code
     * @return Container status information
     */
    @GetMapping("/container/{containerCode}/status")
    public ResponseEntity<ApiResponse<ContainerStatusResponse>> getContainerStatus(
            @PathVariable String robotCode,
            @PathVariable String containerCode) {

        Optional<RobotContainerStatusDTO> containerStatusOpt = robotDataService.getContainerStatus(robotCode, containerCode);
        if (containerStatusOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "No container with code " + containerCode + " found for robot " + robotCode
            ));
        } else {
            RobotContainerStatusDTO containerStatus = containerStatusOpt.get();
            ContainerStatusResponse response = new ContainerStatusResponse(
                    robotCode,
                    containerCode,
                    containerStatus.getStatus()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    /**
     * Get location information for a specific robot
     *
     * @param robotCode Robot ID
     * @return Location information
     */
    @GetMapping("/location")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable String robotCode) {
        Optional<RobotLocationDTO> locationOpt = robotDataService.getLocation(robotCode);
        if (locationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "No location information available for robot "
            ));
        } else {
            RobotLocationDTO location = locationOpt.get();
            LocationResponse response = new LocationResponse(
                    robotCode,
                    location.getLat(),
                    location.getLon()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    /**
     * Get battery status for a specific robot
     *
     * @param robotCode Robot ID
     * @return Battery information
     */
    @GetMapping("/battery")
    public ResponseEntity<ApiResponse<BatteryResponse>> getBattery(@PathVariable String robotCode) {
        Optional<RobotBatteryDTO> batteryOpt = robotDataService.getBattery(robotCode);
        if (batteryOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "No battery information available for robot " + robotCode
            ));
        } else {
            RobotBatteryDTO battery = batteryOpt.get();
            BatteryResponse response = new BatteryResponse(
                    robotCode,
                    battery.getBattery()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    /**
     * Get operational status for a specific robot
     *
     * @param robotCode Robot ID
     * @return Status information
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<StatusResponse>> getStatus(@PathVariable String robotCode) {
        Optional<RobotStatusDTO> statusOpt = robotDataService.getStatus(robotCode);
        if (statusOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "No status information available for robot " + robotCode
            ));
        } else {
            RobotStatusDTO status = statusOpt.get();
            StatusResponse response = new StatusResponse(
                    robotCode,
                    status.getStatus()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    /**
     * Get connection status for a specific robot
     *
     * @param robotCode Robot ID
     * @return Connection information
     */
    @GetMapping("/connection")
    public ResponseEntity<?> getConnectionStatus(@PathVariable String robotCode) {
        Map<String, Object> connectionInfo = robotDataService.getRobotConnectionInfo(robotCode);

        if (connectionInfo == null || connectionInfo.isEmpty()) {
            Map<String, Object> errorResponse = createErrorResponse(
                    "Connection information not found",
                    "No connection information available for robot " + robotCode
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("timestamp", LocalDateTime.now());
        response.put("data", connectionInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all available information for a specific robot
     *
     * @param robotCode Robot ID
     * @return All available robot information including connection status
     */
    @GetMapping("/info")
    public ResponseEntity<?> getRobotInfo(@PathVariable String robotCode) {
        Map<String, Object> robotInfo = new HashMap<>();

        // Get all available information about the robot
        robotDataService.getLocation(robotCode).ifPresent(location -> robotInfo.put("location", location));
        robotDataService.getBattery(robotCode).ifPresent(battery -> robotInfo.put("battery", battery));
        robotDataService.getStatus(robotCode).ifPresent(status -> robotInfo.put("status", status));

        // Add connection information
        Map<String, Object> connectionInfo = robotDataService.getRobotConnectionInfo(robotCode);
        if (connectionInfo != null && !connectionInfo.isEmpty()) {
            robotInfo.put("connection", connectionInfo);
        }

        // If we couldn't find any information about this robot
        if (robotInfo.isEmpty()) {
            Map<String, Object> errorResponse = createErrorResponse(
                    "Robot not found",
                    "No information available for robot " + robotCode
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("timestamp", LocalDateTime.now());
        response.put("robotCode", robotCode);
        response.put("data", robotInfo);

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to create standardized error responses
     */
    private Map<String, Object> createErrorResponse(String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return errorResponse;
    }
}
