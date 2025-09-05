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

    @GetMapping("/cancel")
    public ResponseEntity<ApiResponse<TripResponse>> cancelTrip(@RequestParam String tripCode) {
        try {
            log.info("Received request to cancel trip for tripCode: {}", tripCode);

            TripResponse tripResponse = tripStatusService.cancelTrip(tripCode);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip cancelled successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error cancelling trip for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error cancelling trip for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/continue")
    public ResponseEntity<ApiResponse<TripResponse>> continueTrip(@RequestParam String tripCode) {
        try {
            log.info("Received request to continue trip for tripCode: {}", tripCode);

            TripResponse tripResponse = tripStatusService.continueTrip(tripCode);

            return ResponseEntity.ok(
                    ApiResponse.success(tripResponse, "Trip continued successfully")
            );

        } catch (RuntimeException e) {
            log.error("Error continuing trip for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error continuing trip for tripCode {}: {}", tripCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error occurred"));
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<TripProgressResponse>> getTripProgressByParam(@RequestParam String tripCode) {
        try {
            log.info("Received request to get progress for tripCode: {}", tripCode);

            TripProgressResponse progressResponse = tripStatusService.getTripProgressResponse(tripCode);

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
