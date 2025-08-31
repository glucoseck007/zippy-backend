package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotTripMqttDTO {
    private String trip_id;
    private double progress;
    private int status; // 0=Prepare, 1=Load, 2=OnGoing, 3=Delivered, 4=Finish
    private String start_point;
    private String end_point;
}
