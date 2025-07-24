package com.smartlab.zippy.model.dto.robot;

import lombok.*;

@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RobotLocationDTO {
    private double lat;
    private double lon;
}
