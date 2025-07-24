package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.exception.GlobalHandlingException;
import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import org.springframework.stereotype.Service;

@Service
public class RobotStatusService {

    // This could be replaced with a repository or cache for retrieving real data
    // For now, we're using a simple in-memory approach
    private final RobotStateCache robotStateCache;

    public RobotStatusService(RobotStateCache robotStateCache) {
        this.robotStateCache = robotStateCache;
    }

    /**
     * Get the current location of a robot
     *
     * @param robotId Robot ID
     * @return Location data
     */
    public RobotLocationDTO getLocation(String robotId) {
        return robotStateCache.getLocation(robotId)
                .orElseThrow(() -> new GlobalHandlingException.ResourceNotFoundException("Robot location not found"));
    }

    /**
     * Get the current battery status of a robot
     *
     * @param robotId Robot ID
     * @return Battery data
     */
    public RobotBatteryDTO getBattery(String robotId) {
        return robotStateCache.getBattery(robotId)
                .orElseThrow(() -> new GlobalHandlingException.ResourceNotFoundException("Robot battery status not found"));
    }

    /**
     * Get the current status of a robot
     *
     * @param robotId Robot ID
     * @return Status data
     */
    public RobotStatusDTO getStatus(String robotId) {
        return robotStateCache.getStatus(robotId)
                .orElseThrow(() -> new GlobalHandlingException.ResourceNotFoundException("Robot status not found"));
    }

    /**
     * Get the current status of a specific container
     *
     * @param robotId Robot ID
     * @param containerCode Container Code
     * @return Container status data
     */
    public RobotContainerStatusDTO getContainerStatus(String robotId, String containerCode) {
        return robotStateCache.getContainerStatus(robotId, containerCode)
                .orElseThrow(() -> new GlobalHandlingException.ResourceNotFoundException("Container status not found"));
    }
}
