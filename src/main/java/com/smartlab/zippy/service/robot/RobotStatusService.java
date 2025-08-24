package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.exception.GlobalHandlingException;
import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RobotStatusService {

    private final RobotStateCache robotStateCache;
    private final RobotRepository robotRepository;
    private final RobotDataService robotDataService;

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

    /**
     * Get all robots currently in a specific room
     *
     * @param roomCode Room code to filter robots
     * @return List of robot locations for robots in the specified room
     */
    public List<RobotLocationDTO> getRobotsByRoom(String roomCode) {
        log.info("Getting robots in room: {}", roomCode);

        // First try to get from cache for online robots
        List<RobotLocationDTO> onlineRobotsInRoom = robotRepository.findByRoomCode(roomCode).stream()
                .filter(robot -> robotDataService.isRobotOnline(robot.getCode()))
                .map(robot -> {
                    // Try to get from cache first
                    return robotStateCache.getLocation(robot.getCode())
                            .orElse(null);
                })
                .filter(location -> location != null && roomCode.equals(location.getRoomCode()))
                .collect(Collectors.toList());

        log.info("Found {} robots in room {}", onlineRobotsInRoom.size(), roomCode);
        return onlineRobotsInRoom;
    }
}
