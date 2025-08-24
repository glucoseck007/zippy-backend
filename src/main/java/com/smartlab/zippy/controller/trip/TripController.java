package com.smartlab.zippy.controller.trip;

import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.trip.TripResponse;
import com.smartlab.zippy.model.dto.web.response.trip.TripProgressResponse;
import com.smartlab.zippy.service.trip.TripService;
import com.smartlab.zippy.service.trip.TripStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/trip")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TripStatusService tripStatusService;

    @GetMapping("/by-order-id")
    public ResponseEntity<ApiResponse<TripResponse>> getTripByOrderId(@RequestParam UUID orderId) {
        try {
            log.info("Received request to get trip for orderId: {}", orderId);

            TripResponse tripResponse = tripService.getTripByOrderId(orderId);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip retrieved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error retrieving trip for orderId {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error retrieving trip for orderId {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/by-order-code")
    public ResponseEntity<ApiResponse<TripResponse>> getTripByOrderCode(@RequestParam String orderCode) {
        try {
            log.info("Received request to get trip for orderCode: {}", orderCode);

            TripResponse tripResponse = tripService.getTripByOrderCode(orderCode);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip retrieved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error retrieving trip for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error retrieving trip for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/order/code/{orderCode}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripByOrderCodePath(@PathVariable String orderCode) {
        try {
            log.info("Received request to get trip for orderCode: {}", orderCode);

            TripResponse tripResponse = tripService.getTripByOrderCode(orderCode);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip retrieved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error retrieving trip for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error retrieving trip for orderCode {}: {}", orderCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/progress/{tripCode}")
    public ResponseEntity<ApiResponse<TripProgressResponse>> getTripProgressByParam(@PathVariable String tripCode) {
        try {
            log.info("Received request to get progress for tripCode: {}", tripCode);

            // Get progress from TripStatusService
            double progress = tripStatusService.getTripProgress(tripCode);

            // Get trip details to include status
            TripResponse tripResponse = tripService.getTripByCode(tripCode);

            // Check if the trip is in the "PREPARED" status (robot is heading to start point)
            boolean isPreparing = "PREPARED".equals(tripResponse.getStatus());

            TripProgressResponse progressResponse = TripProgressResponse.builder()
                    .tripCode(tripCode)
                    .overallStatus(tripResponse.getStatus())
                    .progress(progress)
                    .isPreparing(isPreparing)
                    .startTime(tripResponse.getStartTime())
                    .endTime(tripResponse.getEndTime())
                    .build();

            return ResponseEntity.ok(
                    ApiResponse.success(progressResponse, "Trip progress retrieved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error retrieving trip progress for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error retrieving trip progress for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/details/{tripCode}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripDetails(@PathVariable String tripCode) {
        try {
            log.info("Received request to get trip details for tripCode: {}", tripCode);

            TripResponse tripResponse = tripService.getTripByCode(tripCode);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip details retrieved successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error retrieving trip details for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error retrieving trip details for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }
}
