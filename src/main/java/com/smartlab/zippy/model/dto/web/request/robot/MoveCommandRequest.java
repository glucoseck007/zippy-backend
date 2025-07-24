package com.smartlab.zippy.model.dto.web.request.robot;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class MoveCommandRequest {
    private double lat;
    private double lon;
}
