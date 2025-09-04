package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotWarningMqttDTO {
    private String title;
    private String message;
    private String timestamp;
}
