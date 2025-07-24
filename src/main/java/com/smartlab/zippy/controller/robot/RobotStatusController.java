package com.smartlab.zippy.controller.robot;

import com.smartlab.zippy.model.dto.robot.RobotBatteryDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotLocationDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.service.robot.RobotStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/robot/status/{robotId}")
public class RobotStatusController {

    private final RobotStatusService robotStatusService;

    public RobotStatusController(RobotStatusService robotStatusService) {
        this.robotStatusService = robotStatusService;
    }

    /**
     * Get robot location by robot ID
     *
     * @param robotId Robot ID
     * @return Location data
     */
    @GetMapping("/location")
    public ResponseEntity<RobotLocationDTO> getRobotLocation(@PathVariable String robotId) {
        return ResponseEntity.ok(robotStatusService.getLocation(robotId));
    }

    /**
     * Get robot battery by robot ID
     *
     * @param robotId Robot ID
     * @return Battery data
     */
    @GetMapping("/battery")
    public ResponseEntity<RobotBatteryDTO> getRobotBattery(@PathVariable String robotId) {
        return ResponseEntity.ok(robotStatusService.getBattery(robotId));
    }

    /**
     * Get robot status by robot ID
     *
     * @param robotId Robot ID
     * @return Status data
     */
    @GetMapping
    public ResponseEntity<RobotStatusDTO> getRobotStatus(@PathVariable String robotId) {
        return ResponseEntity.ok(robotStatusService.getStatus(robotId));
    }

    /**
     * Get container status by robot ID and container code
     *
     * @param robotId Robot ID
     * @param containerCode Container Code
     * @return Container status data
     */
    @GetMapping("/container/{containerCode}")
    public ResponseEntity<RobotContainerStatusDTO> getContainerStatus(
            @PathVariable String robotId,
            @PathVariable String containerCode) {
        return ResponseEntity.ok(robotStatusService.getContainerStatus(robotId, containerCode));
    }
}
