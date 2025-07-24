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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        // Try cache first if robot is online
        if (isRobotOnline(robotId)) {
            Optional<RobotLocationDTO> cached = robotStateCache.getLocation(robotId);
            if (cached.isPresent()) {
                return cached;
            }
        }

        // Fallback to database
        return getLocationFromDatabase(robotId);
    }

    /**
     * Get robot battery with intelligent fallback
     */
    public Optional<RobotBatteryDTO> getBattery(String robotId) {
        if (isRobotOnline(robotId)) {
            Optional<RobotBatteryDTO> cached = robotStateCache.getBattery(robotId);
            if (cached.isPresent()) {
                return cached;
            }
        }

        return getBatteryFromDatabase(robotId);
    }

    /**
     * Get robot status with intelligent fallback
     */
    public Optional<RobotStatusDTO> getStatus(String robotId) {
        if (isRobotOnline(robotId)) {
            Optional<RobotStatusDTO> cached = robotStateCache.getStatus(robotId);
            if (cached.isPresent()) {
                return cached;
            }
        }

        return getStatusFromDatabase(robotId);
    }

    /**
     * Get container status with intelligent fallback
     */
    public Optional<RobotContainerStatusDTO> getContainerStatus(String robotId, String containerCode) {
        if (isRobotOnline(robotId)) {
            Optional<RobotContainerStatusDTO> cached = robotStateCache.getContainerStatus(robotId, containerCode);
            if (cached.isPresent()) {
                return cached;
            }
        }

        return getContainerStatusFromDatabase(robotId, containerCode);
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

    // Private helper methods for database operations
    private void persistLocationToDatabase(String robotId, RobotLocationDTO location) {
        try {
            Optional<Robot> robotOpt = robotRepository.findByCode(robotId);
            if (robotOpt.isPresent()) {
                Robot robot = robotOpt.get();
                robot.setLocationRealtime(String.format("%.6f,%.6f", location.getLat(), location.getLon()));
                robotRepository.save(robot);
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
            if (robotOpt.isPresent() && robotOpt.get().getLocationRealtime() != null) {
                String[] coords = robotOpt.get().getLocationRealtime().split(",");
                if (coords.length == 2) {
                    return Optional.of(RobotLocationDTO.builder()
                        .lat(Double.parseDouble(coords[0]))
                        .lon(Double.parseDouble(coords[1]))
                        .build());
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
}
