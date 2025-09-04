package com.smartlab.zippy.component;

import com.smartlab.zippy.model.dto.robot.*;
import com.smartlab.zippy.model.dto.trip.TripStateMqttDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RobotStatusCache {

    private final Map<String, String> statusMap = new ConcurrentHashMap<>();
    private final Map<String, RobotContainerMqttDTO> containerMap = new ConcurrentHashMap<>();
    private final Map<String, RobotQrCodeMqttDTO> qrCodeMap = new ConcurrentHashMap<>();
    private final Map<String, RobotHeartbeatMqttDTO> heartbeatMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> heartbeatTimestampMap = new ConcurrentHashMap<>();
    private final Map<String, TripStateMqttDTO> tripMap = new ConcurrentHashMap<>();

    // Auto-clear heartbeat data after 3 seconds
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 30;

    public void updateStatus(String robotCode, String status) {
        statusMap.put(robotCode, status);
    }

    public void updateContainerStatus(String robotCode, RobotContainerMqttDTO dto) {
        containerMap.put(robotCode, dto);
    }

    public void updateQrCode(String robotCode, RobotQrCodeMqttDTO dto) {
        qrCodeMap.put(robotCode, dto);
    }

    public  void updateHeartbeat(String robotCode, RobotHeartbeatMqttDTO dto) {
        heartbeatMap.put(robotCode, dto);
        heartbeatTimestampMap.put(robotCode, LocalDateTime.now());
        log.debug("Updated heartbeat for robot: {} at {}", robotCode, LocalDateTime.now());
    }

    public void updateTrip(String robotCode, TripStateMqttDTO dto) {
        tripMap.put(robotCode, dto);
    }

    public String getStatus(String robotCode) {
        return statusMap.get(robotCode);
    }

    public RobotContainerMqttDTO getContainerStatus(String robotCode) {
        return containerMap.get(robotCode);
    }

    public RobotQrCodeMqttDTO getQrCode(String robotCode) {
        return qrCodeMap.get(robotCode);
    }

    public RobotHeartbeatMqttDTO getHeartbeat(String robotCode) {
        return heartbeatMap.get(robotCode);
    }

    public TripStateMqttDTO getTrip(String robotCode) {
        return tripMap.get(robotCode);
    }

    public boolean isAlive(String robotCode) {
        RobotHeartbeatMqttDTO heartbeat = heartbeatMap.get(robotCode);
        LocalDateTime lastHeartbeat = heartbeatTimestampMap.get(robotCode);

        if (heartbeat == null || lastHeartbeat == null) {
            return false;
        }

        // Check if heartbeat is within timeout period
        LocalDateTime now = LocalDateTime.now();
        boolean withinTimeout = lastHeartbeat.plusSeconds(HEARTBEAT_TIMEOUT_SECONDS).isAfter(now);

        if (!withinTimeout) {
            log.debug("Robot {} heartbeat expired. Last heartbeat: {}, Current time: {}",
                     robotCode, lastHeartbeat, now);
            return false;
        }

        // Consider the robot alive if heartbeat is recent and status is alive
        return heartbeat.isAlive() && withinTimeout;
    }

    public boolean isFree(String robotCode) {
        return "FREE".equalsIgnoreCase(statusMap.get(robotCode));
    }

    // Scheduled task to clean up expired heartbeat data every second
    @Scheduled(fixedRate = 1000) // Run every 1 second
    public void cleanupExpiredHeartbeats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        heartbeatTimestampMap.entrySet().removeIf(entry -> {
            String robotCode = entry.getKey();
            LocalDateTime timestamp = entry.getValue();

            if (timestamp.isBefore(cutoffTime)) {
                heartbeatMap.remove(robotCode);
                log.debug("Removed expired heartbeat data for robot: {} (last heartbeat: {})",
                         robotCode, timestamp);
                return true;
            }
            return false;
        });
    }
}
