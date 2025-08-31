package com.smartlab.zippy.model.dto.robot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotDTO {
    private UUID id;
    private String code;
    private double batteryStatus;
    private String locationRealtime;
    private String status; // Available, Busy, Maintenance, Offline, etc.
    private int containerCount;
    private int activeTripsCount;
}
