package com.smartlab.zippy.controller.robot;

import com.smartlab.zippy.model.dto.web.request.robot.LoadCommandRequest;
import com.smartlab.zippy.model.dto.web.request.robot.MoveCommandRequest;
import com.smartlab.zippy.model.dto.web.request.robot.PickupCommandRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.robot.LoadCommandResponse;
import com.smartlab.zippy.model.dto.web.response.robot.MoveCommandResponse;
import com.smartlab.zippy.model.dto.web.response.robot.PickupCommandResponse;
import com.smartlab.zippy.service.robot.RobotCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/robot/command/{robotCode}")
public class RobotCommandController {

    private final RobotCommandService commandService;

    public RobotCommandController(RobotCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * Send move command to robot
     *
     * @param robotCode Robot ID
     * @param request Move command request
     * @return Move command response
     */
    @PostMapping("/move")
    public ResponseEntity<ApiResponse<MoveCommandResponse>> move(
            @PathVariable String robotCode,
            @RequestBody MoveCommandRequest request
    ) {
        try {
            commandService.sendMove(robotCode, request.getLat(), request.getLon());
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
     * Send pickup command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param request Pickup command request
     * @return Pickup command response
     */
    @PostMapping("/container/{containerCode}/pickup")
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
     * Send load command to robot container
     *
     * @param robotCode Robot ID
     * @param containerCode Container Code
     * @param request Load command request
     * @return Load command response
     */
    @PostMapping("/container/{containerCode}/load")
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
}
