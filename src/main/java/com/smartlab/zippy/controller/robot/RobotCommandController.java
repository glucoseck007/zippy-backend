package com.smartlab.zippy.controller.robot;

import com.smartlab.zippy.model.dto.web.request.robot.LoadCommandRequest;
import com.smartlab.zippy.model.dto.web.request.robot.MoveCommandRequest;
import com.smartlab.zippy.model.dto.web.request.robot.PickupCommandRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.robot.LoadCommandResponse;
import com.smartlab.zippy.model.dto.web.response.robot.MoveCommandResponse;
import com.smartlab.zippy.model.dto.web.response.robot.PickupCommandResponse;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.RobotContainer;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.RobotContainerRepository;
import com.smartlab.zippy.service.robot.RobotCommandService;
import com.smartlab.zippy.service.robot.RobotDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/robot/command")
public class RobotCommandController {

    private final RobotCommandService commandService;
    private final RobotDataService robotDataService;
    private final RobotRepository robotRepository;
    private final RobotContainerRepository robotContainerRepository;

    public RobotCommandController(RobotCommandService commandService,
                                RobotDataService robotDataService,
                                RobotRepository robotRepository,
                                RobotContainerRepository robotContainerRepository) {
        this.commandService = commandService;
        this.robotDataService = robotDataService;
        this.robotRepository = robotRepository;
        this.robotContainerRepository = robotContainerRepository;
    }

    /**
     * Request status from all robots and return free robots with free containers
     * Flow: API call -> Send command to all robots -> Robots respond via status topic -> Return free robots
     *
     * @return List of free robots with their container statuses
     */
    @PostMapping("/request-status")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestStatusFromAllRobots() {
        try {
            // Get all robots from database
            List<Robot> allRobots = (List<Robot>) robotRepository.findAll();
            List<String> commandsSent = new ArrayList<>();

            // Send status request command to each robot
            for (Robot robot : allRobots) {
                try {
                    commandService.requestStatus(robot.getCode());
                    commandsSent.add(robot.getCode());
                } catch (Exception e) {
                    // Log error but continue with other robots
                    System.err.println("Failed to send status request to robot " + robot.getCode() + ": " + e.getMessage());
                }
            }

            // Wait a moment for robots to respond (robots will send data to status topic)
            Thread.sleep(2000); // 2 seconds wait for responses

            // Collect free robots and their container statuses
            List<Map<String, Object>> freeRobots = new ArrayList<>();

            for (Robot robot : allRobots) {
                // Check if robot is online and get its status
                if (robotDataService.isRobotOnline(robot.getCode())) {
                    Optional<RobotStatusDTO> statusOpt = robotDataService.getStatus(robot.getCode());

                    if (statusOpt.isPresent()) {
                        RobotStatusDTO status = statusOpt.get();

                        // Check if robot is free (assuming "idle" or "free" status means available)
                        if ("free".equalsIgnoreCase(status.getStatus())) {

                            Map<String, Object> robotInfo = new HashMap<>();
                            robotInfo.put("robotCode", robot.getCode());
                            robotInfo.put("status", status.getStatus());
                            robotInfo.put("online", true);

                            // Get container statuses for this robot using repository query to avoid lazy loading
                            List<RobotContainer> containers = robotContainerRepository.findByRobotCode(robot.getCode());
                            List<Map<String, Object>> freeContainers = new ArrayList<>();

                            for (RobotContainer container : containers) {
                                Optional<RobotContainerStatusDTO> containerStatusOpt =
                                    robotDataService.getContainerStatus(robot.getCode(), container.getContainerCode());

                                if (containerStatusOpt.isPresent()) {
                                    RobotContainerStatusDTO containerStatus = containerStatusOpt.get();

                                    // Check if container is free
                                    if ("free".equalsIgnoreCase(containerStatus.getStatus())) {

                                        Map<String, Object> containerInfo = new HashMap<>();
                                        containerInfo.put("containerCode", container.getContainerCode());
                                        containerInfo.put("status", containerStatus.getStatus());
                                        freeContainers.add(containerInfo);
                                    }
                                }
                            }

                            robotInfo.put("freeContainers", freeContainers);
                            robotInfo.put("totalFreeContainers", freeContainers.size());

                            // Only add robot if it has free containers or if we want all free robots
                            if (!freeContainers.isEmpty()) {
                                freeRobots.add(robotInfo);
                            }
                        }
                    }
                }
            }

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("commandsSent", commandsSent.size());
            response.put("robotsRequested", commandsSent);
            response.put("freeRobotsCount", freeRobots.size());
            response.put("freeRobots", freeRobots);
            response.put("message", "Status request sent to " + commandsSent.size() + " robots, found " + freeRobots.size() + " free robots");

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to request status from robots: " + e.getMessage()
            ));
        }
    }

    /**
     * Send move command to robot
     *
     * @param robotCode Robot ID
     * @param request Move command request
     * @return Move command response
     */
    @PostMapping("/{robotCode}/move")
    public ResponseEntity<ApiResponse<MoveCommandResponse>> move(
            @PathVariable String robotCode,
            @RequestBody MoveCommandRequest request
    ) {
        try {
            commandService.sendMove(robotCode, request.getLat(), request.getLon(), request.getRoomCode());
            MoveCommandResponse response = new MoveCommandResponse(
                    robotCode,
                    request.getLat(),
                    request.getLon(),
                    "Move command sent successfully"
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send move command to robot " + robotCode
            ));
        }
    }

    /**
     * Send trip-based move command to robot
     *
     * @param robotCode Robot ID
     * @param tripCode Trip Code
     * @param request Move command request
     * @return Move command response
     */
    @PostMapping("/{robotCode}/trip/{tripCode}/move")
    public ResponseEntity<ApiResponse<MoveCommandResponse>> moveWithTrip(
            @PathVariable String robotCode,
            @PathVariable String tripCode,
            @RequestBody MoveCommandRequest request
    ) {
        try {
            commandService.sendTripMove(robotCode, tripCode, request.getLat(), request.getLon(), request.getRoomCode());
            MoveCommandResponse response = new MoveCommandResponse(
                    robotCode,
                    request.getLat(),
                    request.getLon(),
                    "Trip move command sent successfully for trip: " + tripCode
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send trip move command to robot " + robotCode + " for trip " + tripCode
            ));
        }
    }

    /**
     * Send pickup command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param request Pickup command request
     * @return Pickup command response
     */
    @PostMapping("/{robotCode}/container/{containerCode}/pickup")
    public ResponseEntity<ApiResponse<PickupCommandResponse>> sendPickupCommand(
            @PathVariable String robotCode,
            @PathVariable String containerCode,
            @RequestBody PickupCommandRequest request) {
        try {
            commandService.sendPickup(robotCode, containerCode, request.isPickup());
            PickupCommandResponse response = new PickupCommandResponse(
                    robotCode,
                    containerCode,
                    request.isPickup(),
                    "Pickup command sent successfully"
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send pickup command to robot " + robotCode + " container " + containerCode
            ));
        }
    }

    /**
     * Send trip-based pickup command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param tripCode Trip Code
     * @param request Pickup command request
     * @return Pickup command response
     */
    @PostMapping("/{robotCode}/container/{containerCode}/trip/{tripCode}/pickup")
    public ResponseEntity<ApiResponse<PickupCommandResponse>> sendTripPickupCommand(
            @PathVariable String robotCode,
            @PathVariable String containerCode,
            @PathVariable String tripCode,
            @RequestBody PickupCommandRequest request) {
        try {
            commandService.sendTripPickup(robotCode, containerCode, tripCode, request.isPickup());
            PickupCommandResponse response = new PickupCommandResponse(
                    robotCode,
                    containerCode,
                    request.isPickup(),
                    "Trip pickup command sent successfully for trip: " + tripCode
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send trip pickup command to robot " + robotCode + " container " + containerCode + " for trip " + tripCode
            ));
        }
    }

    /**
     * Send load command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param request Load command request
     * @return Load command response
     */
    @PostMapping("/{robotCode}/container/{containerCode}/load")
    public ResponseEntity<ApiResponse<LoadCommandResponse>> sendLoadCommand(
            @PathVariable String robotCode,
            @PathVariable String containerCode,
            @RequestBody LoadCommandRequest request) {
        try {
            commandService.sendLoad(robotCode, containerCode, request.isLoad());
            LoadCommandResponse response = new LoadCommandResponse(
                    robotCode,
                    containerCode,
                    request.isLoad(),
                    "Load command sent successfully"
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send load command to robot " + robotCode + " container " + containerCode
            ));
        }
    }

    /**
     * Send trip-based load command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param tripCode Trip Code
     * @param request Load command request
     * @return Load command response
     */
    @PostMapping("/{robotCode}/container/{containerCode}/trip/{tripCode}/load")
    public ResponseEntity<ApiResponse<LoadCommandResponse>> sendTripLoadCommand(
            @PathVariable String robotCode,
            @PathVariable String containerCode,
            @PathVariable String tripCode,
            @RequestBody LoadCommandRequest request) {
        try {
            commandService.sendTripLoad(robotCode, containerCode, tripCode, request.isLoad());
            LoadCommandResponse response = new LoadCommandResponse(
                    robotCode,
                    containerCode,
                    request.isLoad(),
                    "Trip load command sent successfully for trip: " + tripCode
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "Failed to send trip load command to robot " + robotCode + " container " + containerCode + " for trip " + tripCode
            ));
        }
    }
}
