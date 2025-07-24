package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for caching robot state information received from MQTT messages
 */
@Service
@Slf4j
public class RobotStateCache {

    // In-memory caches for robot data
    private final Map<String, RobotLocationDTO> locationCache = new ConcurrentHashMap<>();
    private final Map<String, RobotBatteryDTO> batteryCache = new ConcurrentHashMap<>();
    private final Map<String, RobotStatusDTO> statusCache = new ConcurrentHashMap<>();
    private final Map<String, RobotContainerStatusDTO> containerStatusCache = new ConcurrentHashMap<>();

    /**
     * Store robot location
     *
     * @param robotId  Robot ID
     * @param location Location data
     */
    public void updateLocation(String robotId, RobotLocationDTO location) {
        log.debug("Updating location for robot {}: {}", robotId, location);
        locationCache.put(robotId, location);
    }

    /**
     * Store robot battery status
     *
     * @param robotId Robot ID
     * @param battery Battery data
     */
    public void updateBattery(String robotId, RobotBatteryDTO battery) {
        log.debug("Updating battery for robot {}: {}", robotId, battery);
        batteryCache.put(robotId, battery);
    }

    /**
     * Store robot status
     *
     * @param robotId Robot ID
     * @param status  Status data
     */
    public void updateStatus(String robotId, RobotStatusDTO status) {
        log.debug("Updating status for robot {}: {}", robotId, status);
        statusCache.put(robotId, status);
    }

    /**
     * Store container status
     *
     * @param robotId      Robot ID
     * @param containerCode Container Code
     * @param status       Container status data
     */
    public void updateContainerStatus(String robotId, String containerCode, RobotContainerStatusDTO status) {
        String key = getContainerKey(robotId, containerCode);
        log.debug("Updating container status for robot {} container {}: {}", robotId, containerCode, status);
        containerStatusCache.put(key, status);
    }

    /**
     * Get robot location
     *
     * @param robotId Robot ID
     * @return Optional location data
     */
    public Optional<RobotLocationDTO> getLocation(String robotId) {
        return Optional.ofNullable(locationCache.get(robotId));
    }

    /**
     * Get robot battery status
     *
     * @param robotId Robot ID
     * @return Optional battery data
     */
    public Optional<RobotBatteryDTO> getBattery(String robotId) {
        return Optional.ofNullable(batteryCache.get(robotId));
    }

    /**
     * Get robot status
     *
     * @param robotId Robot ID
     * @return Optional status data
     */
    public Optional<RobotStatusDTO> getStatus(String robotId) {
        return Optional.ofNullable(statusCache.get(robotId));
    }

    /**
     * Get container status
     *
     * @param robotId       Robot ID
     * @param containerCode Container Code
     * @return Optional container status data
     */
    public Optional<RobotContainerStatusDTO> getContainerStatus(String robotId, String containerCode) {
        String key = getContainerKey(robotId, containerCode);
        return Optional.ofNullable(containerStatusCache.get(key));
    }

    /**
     * Generate key for container status cache
     *
     * @param robotId       Robot ID
     * @param containerCode Container Code
     * @return Unique key
     */
    private String getContainerKey(String robotId, String containerCode) {
        return robotId + ":" + containerCode;
    }
}
