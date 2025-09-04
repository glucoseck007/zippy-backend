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
    private String tripCode;
    private String robotCode;
    private int status; // 0=Prepare, 1=Load, 2=OnGoing, 3=Delivered, 4=Finish
    private String startPoint;
    private String endPoint;
    private double progress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
