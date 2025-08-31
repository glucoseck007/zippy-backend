package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotLocationMqttDTO {
    private String roomCode;
}
