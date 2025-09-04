package com.smartlab.zippy.model.dto.robot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for robot heartbeat MQTT messages
 * Payload format: {"timestamp": "string", "alive": true/false}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotHeartbeatMqttDTO {

    @JsonProperty("isAlive")
    private boolean alive;
}
