package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // Track last access time for cache cleanup
    private final Map<String, LocalDateTime> locationLastAccess = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> batteryLastAccess = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> statusLastAccess = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> containerStatusLastAccess = new ConcurrentHashMap<>();

    // Scheduler for cache cleanup
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Cache cleanup interval (30 seconds)
    private static final int CACHE_CLEANUP_DELAY_SECONDS = 30;

    /**
     * Store robot location
     *
     * @param robotId  Robot ID
     * @param location Location data
     */
    public void updateLocation(String robotId, RobotLocationDTO location) {
        log.debug("Updating location for robot {}: {}", robotId, location);
        locationCache.put(robotId, location);
        locationLastAccess.put(robotId, LocalDateTime.now());
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
        batteryLastAccess.put(robotId, LocalDateTime.now());
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
        statusLastAccess.put(robotId, LocalDateTime.now());
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
        containerStatusLastAccess.put(key, LocalDateTime.now());
    }

    /**
     * Get robot location
     *
     * @param robotId Robot ID
     * @return Optional location data
     */
    public Optional<RobotLocationDTO> getLocation(String robotId) {
        Optional<RobotLocationDTO> result = Optional.ofNullable(locationCache.get(robotId));
        if (result.isPresent()) {
            locationLastAccess.put(robotId, LocalDateTime.now());
        }
        return result;
    }

    /**
     * Get robot battery status
     *
     * @param robotId Robot ID
     * @return Optional battery data
     */
    public Optional<RobotBatteryDTO> getBattery(String robotId) {
        Optional<RobotBatteryDTO> result = Optional.ofNullable(batteryCache.get(robotId));
        if (result.isPresent()) {
            batteryLastAccess.put(robotId, LocalDateTime.now());
        }
        return result;
    }

    /**
     * Get robot status
     *
     * @param robotId Robot ID
     * @return Optional status data
     */
    public Optional<RobotStatusDTO> getStatus(String robotId) {
        Optional<RobotStatusDTO> result = Optional.ofNullable(statusCache.get(robotId));
        if (result.isPresent()) {
            statusLastAccess.put(robotId, LocalDateTime.now());
        }
        return result;
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
        Optional<RobotContainerStatusDTO> result = Optional.ofNullable(containerStatusCache.get(key));
        if (result.isPresent()) {
            containerStatusLastAccess.put(key, LocalDateTime.now());
        }
        return result;
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

    // Schedule cache cleanup task
    {
        scheduler.scheduleAtFixedRate(this::cleanupCaches, CACHE_CLEANUP_DELAY_SECONDS, CACHE_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Cleanup caches by removing entries that haven't been accessed recently
     */
    private void cleanupCaches() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(CACHE_CLEANUP_DELAY_SECONDS);

        locationLastAccess.forEach((robotId, lastAccessTime) -> {
            if (lastAccessTime.isBefore(threshold)) {
                log.debug("Removing stale location cache for robot {}", robotId);
                locationCache.remove(robotId);
                locationLastAccess.remove(robotId);
            }
        });

        batteryLastAccess.forEach((robotId, lastAccessTime) -> {
            if (lastAccessTime.isBefore(threshold)) {
                log.debug("Removing stale battery cache for robot {}", robotId);
                batteryCache.remove(robotId);
                batteryLastAccess.remove(robotId);
            }
        });

        statusLastAccess.forEach((robotId, lastAccessTime) -> {
            if (lastAccessTime.isBefore(threshold)) {
                log.debug("Removing stale status cache for robot {}", robotId);
                statusCache.remove(robotId);
                statusLastAccess.remove(robotId);
            }
        });

        containerStatusLastAccess.forEach((key, lastAccessTime) -> {
            if (lastAccessTime.isBefore(threshold)) {
                String robotId = key.split(":")[0];
                String containerCode = key.split(":")[1];
                log.debug("Removing stale container status cache for robot {} container {}", robotId, containerCode);
                containerStatusCache.remove(key);
                containerStatusLastAccess.remove(key);
            }
        });
    }

    /**
     * Shutdown the scheduler gracefully
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
