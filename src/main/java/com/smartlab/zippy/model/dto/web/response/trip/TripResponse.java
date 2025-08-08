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
public class TripResponse {
    private UUID userId;
    private UUID robotId;
    private String robotCode;
    private String tripCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
}
