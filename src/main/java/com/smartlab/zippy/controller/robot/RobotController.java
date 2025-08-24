package com.smartlab.zippy.controller.robot;

import com.smartlab.zippy.model.dto.robot.RobotDTO;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.service.robot.RobotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    /**
     * Get all robots
     *
     * @return List of all robots
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RobotDTO>>> getAllRobots() {
        List<RobotDTO> robots = robotService.getAllRobots();
        return ResponseEntity.ok(ApiResponse.success(robots, "Robots retrieved successfully"));
    }

    /**
     * Get robot by ID
     *
     * @param id Robot ID
     * @return Robot details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RobotDTO>> getRobotById(@PathVariable UUID id) {
        RobotDTO robot = robotService.getRobotById(id);
        return ResponseEntity.ok(ApiResponse.success(robot, "Robot retrieved successfully"));
    }

    /**
     * Get robots by battery status
     *
     * @param batteryStatus Battery status filter (e.g., "LOW", "MEDIUM", "HIGH")
     * @return List of robots with specified battery status
     */
    @GetMapping("/battery/{batteryStatus}")
    public ResponseEntity<ApiResponse<List<RobotDTO>>> getRobotsByBatteryStatus(
            @PathVariable String batteryStatus) {
        List<RobotDTO> robots = robotService.getRobotsByBatteryStatus(batteryStatus);
        return ResponseEntity.ok(ApiResponse.success(robots,
                "Robots with battery status '" + batteryStatus + "' retrieved successfully"));
    }

    /**
     * Get available robots (for assignment)
     *
     * @return List of available robots
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<RobotDTO>>> getAvailableRobots() {
        List<RobotDTO> availableRobots = robotService.getAvailableRobots();
        return ResponseEntity.ok(ApiResponse.success(availableRobots, "Available robots retrieved successfully"));
    }
}
