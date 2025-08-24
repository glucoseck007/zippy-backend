package com.smartlab.zippy.model.dto.web.response.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {
    private String robotCode;
    private String tripCode;
    private String startPoint;
    private String endPoint;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
}
