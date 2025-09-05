package com.smartlab.zippy.model.dto.robot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotContainerMqttDTO {
    private String status; // "free" or "non-free"
    @JsonProperty("isClosed")
    private boolean isClosed;
    private double weight;
}
