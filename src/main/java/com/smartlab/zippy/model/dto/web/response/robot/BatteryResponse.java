package com.smartlab.zippy.model.dto.web.response.robot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatteryResponse {

    private String robotCode;
    private double battery; // Changed from String to double to match the new battery system
}
