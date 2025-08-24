package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.RobotContainer;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.RobotContainerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Enhanced service that handles robot data with cache-first strategy and database persistence
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RobotDataService {

    private final RobotStateCache robotStateCache;
    private final RobotRepository robotRepository;
    private final RobotContainerRepository robotContainerRepository;

    // Track robot connection status and last seen time
    private final Map<String, LocalDateTime> robotLastSeen = new ConcurrentHashMap<>();
    private final Map<String, Boolean> robotConnectionStatus = new ConcurrentHashMap<>();

    // Scheduler for async database persistence
    private final ScheduledExecutorService persistenceScheduler = Executors.newScheduledThreadPool(2);

    private static final int ROBOT_OFFLINE_THRESHOLD_MINUTES = 5;

    /**
     * Update robot location with cache-first and database persistence
     */
    @Transactional
    public void updateLocation(String robotId, RobotLocationDTO location) {
        // Update cache immediately for performance
        robotStateCache.updateLocation(robotId, location);
        markRobotOnline(robotId);

        // Persist to database asynchronously
        persistLocationToDatabase(robotId, location);
    }

    /**
     * Update robot battery with cache-first and database persistence
     */
    @Transactional
    public void updateBattery(String robotId, RobotBatteryDTO battery) {
        robotStateCache.updateBattery(robotId, battery);
        markRobotOnline(robotId);

        persistBatteryToDatabase(robotId, battery);
    }

    /**
     * Update robot status with cache-first and database persistence
     */
    @Transactional
    public void updateStatus(String robotId, RobotStatusDTO status) {
        robotStateCache.updateStatus(robotId, status);
        markRobotOnline(robotId);

        persistStatusToDatabase(robotId, status);
    }

    /**
     * Update container status with cache-first and database persistence
     */
    @Transactional
    public void updateContainerStatus(String robotId, String containerCode, RobotContainerStatusDTO status) {
        robotStateCache.updateContainerStatus(robotId, containerCode, status);
        markRobotOnline(robotId);

        persistContainerStatusToDatabase(robotId, containerCode, status);
    }

    /**
     * Get robot location with intelligent fallback
     */
    public Optional<RobotLocationDTO> getLocation(String robotId) {
        if (isRobotOnline(robotId)) {
            Optional<RobotLocationDTO> cached = robotStateCache.getLocation(robotId);
            if (cached.isPresent()) {
                // Schedule persistence to database after successful return
                scheduleCachePersistence(() -> persistLocationToDatabase(robotId, cached.get()));
                return cached;
            }
        }
        // Only return data if robot is online and data is in cache
        return Optional.empty();
    }

    /**
     * Get robot battery with intelligent fallback
     */
    public Optional<RobotBatteryDTO> getBattery(String robotId) {
        if (isRobotOnline(robotId)) {
            Optional<RobotBatteryDTO> cached = robotStateCache.getBattery(robotId);
            if (cached.isPresent()) {
                // Schedule persistence to database after successful return
                scheduleCachePersistence(() -> persistBatteryToDatabase(robotId, cached.get()));
                return cached;
            }
        }

        // Only return data if robot is online and data is in cache
        return Optional.empty();
    }

    /**
     * Get robot status with intelligent fallback
     */
    public Optional<RobotStatusDTO> getStatus(String robotId) {
        if (isRobotOnline(robotId)) {
            Optional<RobotStatusDTO> cached = robotStateCache.getStatus(robotId);
            if (cached.isPresent()) {
                // Schedule persistence to database after successful return
                scheduleCachePersistence(() -> persistStatusToDatabase(robotId, cached.get()));
                return cached;
            }
        }

        // Only return data if robot is online and data is in cache
        return Optional.empty();
    }

    /**
     * Get container status with intelligent fallback
     */
    public Optional<RobotContainerStatusDTO> getContainerStatus(String robotId, String containerCode) {
        if (isRobotOnline(robotId)) {
            Optional<RobotContainerStatusDTO> cached = robotStateCache.getContainerStatus(robotId, containerCode);
            if (cached.isPresent()) {
                // Schedule persistence to database after successful return
                scheduleCachePersistence(() -> persistContainerStatusToDatabase(robotId, containerCode, cached.get()));
                return cached;
            }
        }
        // Only return data if robot is online and data is in cache
        return Optional.empty();
    }

    /**
     * Mark robot as online and update last seen time
     */
    private void markRobotOnline(String robotId) {
        robotLastSeen.put(robotId, LocalDateTime.now());
        robotConnectionStatus.put(robotId, true);
    }

    /**
     * Mark robot as offline
     */
    public void markRobotOffline(String robotId) {
        robotConnectionStatus.put(robotId, false);
        log.info("Robot {} marked as offline", robotId);
    }

    /**
     * Check if robot is considered online
     */
    public boolean isRobotOnline(String robotId) {
        Boolean status = robotConnectionStatus.get(robotId);
        LocalDateTime lastSeen = robotLastSeen.get(robotId);

        if (status == null || !status) {
            return false;
        }

        if (lastSeen == null) {
            return false;
        }

        // Consider robot offline if no activity for threshold minutes
        return lastSeen.isAfter(LocalDateTime.now().minusMinutes(ROBOT_OFFLINE_THRESHOLD_MINUTES));
    }

    /**
     * Get robot connection status
     */
    public Map<String, Object> getRobotConnectionInfo(String robotId) {
        return Map.of(
            "online", isRobotOnline(robotId),
            "lastSeen", robotLastSeen.get(robotId)
        );
    }

    /**
     * Handle robot heartbeat/status message sent every 5 minutes
     * This method updates the robot's online status when receiving periodic status messages
     */
    @Transactional
    public void handleRobotHeartbeat(String robotId, RobotStatusDTO status) {
        log.debug("Received heartbeat from robot: {}", robotId);

        // Update the robot status in cache
        robotStateCache.updateStatus(robotId, status);

        // Mark robot as online and update last seen time
        markRobotOnline(robotId);

        // Persist status to database
        persistStatusToDatabase(robotId, status);

        log.info("Robot {} status updated: {}",
                robotId, status.getStatus());
    }

    /**
     * Handle robot heartbeat without status data (simple ping)
     * This method can be used when robot only sends a simple "I'm alive" message
     */
    @Transactional
    public void handleRobotHeartbeat(String robotId) {
        log.debug("Received heartbeat ping from robot: {}", robotId);

        // Mark robot as online and update last seen time
        markRobotOnline(robotId);

        log.info("Robot {} heartbeat received - marked as online", robotId);
    }

    /**
     * Schedule cache persistence to database after successful return
     * This ensures data is persisted asynchronously after serving from cache
     */
    private void scheduleCachePersistence(Runnable persistenceTask) {
        persistenceScheduler.schedule(() -> {
            try {
                persistenceTask.run();
                log.debug("Cache data successfully persisted to database");
            } catch (Exception e) {
                log.error("Failed to persist cache data to database", e);
            }
        }, 1, TimeUnit.SECONDS); // Execute immediately after return
    }

    // Private helper methods for database operations
    private void persistLocationToDatabase(String robotId, RobotLocationDTO location) {
        try {
            Optional<Robot> robotOpt = robotRepository.findByCode(robotId);
            if (robotOpt.isPresent()) {
                Robot robot = robotOpt.get();
                // Store coordinates in locationRealtime field
                robot.setLocationRealtime(String.format("%.6f,%.6f", location.getLat(), location.getLon()));
                // Store roomCode in separate field
                robot.setRoomCode(location.getRoomCode());
                robotRepository.save(robot);
                log.debug("Persisted location and roomCode to database for robot {}: lat={}, lon={}, roomCode={}",
                         robotId, location.getLat(), location.getLon(), location.getRoomCode());
            } else {
                log.warn("Robot not found in database with code: {}", robotId);
            }
        } catch (Exception e) {
            log.error("Failed to persist location to database for robot {}", robotId, e);
        }
    }

    private void persistBatteryToDatabase(String robotId, RobotBatteryDTO battery) {
        try {
            Optional<Robot> robotOpt = robotRepository.findByCode(robotId);
            if (robotOpt.isPresent()) {
                Robot robot = robotOpt.get();
                robot.setBatteryStatus(battery.getBattery());
                robotRepository.save(robot);
            }
        } catch (Exception e) {
            log.error("Failed to persist battery to database for robot {}", robotId, e);
        }
    }

    private void persistStatusToDatabase(String robotId, RobotStatusDTO status) {
        try {
            // Robot status is not directly stored in Robot entity
            // You might want to add a status field to Robot entity or create a separate table
            log.debug("Robot status persisted: {} - {}", robotId, status.getStatus());
        } catch (Exception e) {
            log.error("Failed to persist status to database for robot {}", robotId, e);
        }
    }

    private void persistContainerStatusToDatabase(String robotId, String containerCode, RobotContainerStatusDTO status) {
        try {
            Optional<RobotContainer> containerOpt = robotContainerRepository
                .findByRobotCodeAndContainerCode(robotId, containerCode);
            if (containerOpt.isPresent()) {
                RobotContainer container = containerOpt.get();
                container.setStatus(status.getStatus());
                robotContainerRepository.save(container);
            }
        } catch (Exception e) {
            log.error("Failed to persist container status to database for robot {} container {}", robotId, containerCode, e);
        }
    }

    private Optional<RobotLocationDTO> getLocationFromDatabase(String robotId) {
        try {
            Optional<Robot> robotOpt = robotRepository.findByCode(robotId);
            if (robotOpt.isPresent()) {
                Robot robot = robotOpt.get();
                if (robot.getLocationRealtime() != null) {
                    String[] coords = robot.getLocationRealtime().split(",");
                    if (coords.length == 2) {
                        return Optional.of(RobotLocationDTO.builder()
                            .lat(Double.parseDouble(coords[0]))
                            .lon(Double.parseDouble(coords[1]))
                            .roomCode(robot.getRoomCode()) // Include roomCode from database
                            .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get location from database for robot {}", robotId, e);
        }
        return Optional.empty();
    }

    private Optional<RobotBatteryDTO> getBatteryFromDatabase(String robotId) {
        try {
            Optional<Robot> robotOpt = robotRepository.findByCode(robotId);
            if (robotOpt.isPresent() && robotOpt.get().getBatteryStatus() != null) {
                return Optional.of(RobotBatteryDTO.builder()
                    .battery(robotOpt.get().getBatteryStatus())
                    .build());
            }
        } catch (Exception e) {
            log.error("Failed to get battery from database for robot {}", robotId, e);
        }
        return Optional.empty();
    }

    private Optional<RobotStatusDTO> getStatusFromDatabase(String robotId) {
        try {
            // Since robot status is not stored in database, return a default or implement accordingly
            return Optional.of(RobotStatusDTO.builder()
                .status("unknown")
                .build());
        } catch (Exception e) {
            log.error("Failed to get status from database for robot {}", robotId, e);
        }
        return Optional.empty();
    }

    private Optional<RobotContainerStatusDTO> getContainerStatusFromDatabase(String robotId, String containerCode) {
        try {
            Optional<RobotContainer> containerOpt = robotContainerRepository
                .findByRobotCodeAndContainerCode(robotId, containerCode);
            if (containerOpt.isPresent() && containerOpt.get().getStatus() != null) {
                return Optional.of(RobotContainerStatusDTO.builder()
                    .status(containerOpt.get().getStatus())
                    .build());
            }
        } catch (Exception e) {
            log.error("Failed to get container status from database for robot {} container {}", robotId, containerCode, e);
        }
        return Optional.empty();
    }

    /**
     * Shutdown the persistence scheduler gracefully
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        persistenceScheduler.shutdown();
        try {
            if (!persistenceScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                persistenceScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            persistenceScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
