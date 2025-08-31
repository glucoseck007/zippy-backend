package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotStatusMqttDTO {
    private String status; // "free" or "non-free"
}
