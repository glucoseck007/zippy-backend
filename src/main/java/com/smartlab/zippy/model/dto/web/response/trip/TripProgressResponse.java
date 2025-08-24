package com.smartlab.zippy.model.dto.web.response.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripProgressResponse {
    private UUID tripId;
    private String tripCode;
    private String robotCode;
    private String startPoint;
    private String endPoint;
    private Integer startProgress;
    private Integer endProgress;
    private String startStatus; // PENDING, ACTIVE, DELIVERED
    private String endStatus;   // PENDING, ACTIVE, DELIVERED
    private String overallStatus; // PENDING, PREPARED, ACTIVE, DELIVERED, COMPLETED
    private boolean isPreparing; // Indicates if robot is in the preparing phase (heading to start point)
    private double progress;     // Overall progress percentage
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
