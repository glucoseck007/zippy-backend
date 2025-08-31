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
     * Get robots by battery level threshold
     *
     * @param threshold Battery level threshold (0.0 to 100.0)
     * @param low Optional parameter: true for robots below threshold, false for above (default: true)
     * @return List of robots matching battery criteria
     */
    @GetMapping("/battery/level/{threshold}")
    public ResponseEntity<ApiResponse<List<RobotDTO>>> getRobotsByBatteryLevel(
            @PathVariable double threshold,
            @RequestParam(defaultValue = "true") boolean low) {
        List<RobotDTO> robots = robotService.getRobotsByBatteryLevel(threshold, low);
        String message = String.format("Robots with battery %s %.1f%% retrieved successfully",
                low ? "below" : "above", threshold);
        return ResponseEntity.ok(ApiResponse.success(robots, message));
    }

    /**
     * Get robots with low battery (below 20%)
     *
     * @return List of robots with low battery
     */
    @GetMapping("/battery/low")
    public ResponseEntity<ApiResponse<List<RobotDTO>>> getLowBatteryRobots() {
        List<RobotDTO> robots = robotService.getLowBatteryRobots();
        return ResponseEntity.ok(ApiResponse.success(robots,
                "Robots with low battery retrieved successfully"));
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
