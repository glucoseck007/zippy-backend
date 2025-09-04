package com.smartlab.zippy.model.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStateMqttDTO {
    private String trip_id;
    private double progress;
    private int status;
    private String start_point;
    private String end_point;
}
