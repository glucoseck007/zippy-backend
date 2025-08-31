package com.smartlab.zippy.model.dto.robot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotBatteryDTO {
    private double battery; // Battery percentage as double, e.g., 85.5
}
