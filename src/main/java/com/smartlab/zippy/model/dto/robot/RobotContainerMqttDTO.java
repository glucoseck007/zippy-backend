package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotContainerMqttDTO {
    private String status; // "free" or "non-free"
    private boolean isClosed;
}
