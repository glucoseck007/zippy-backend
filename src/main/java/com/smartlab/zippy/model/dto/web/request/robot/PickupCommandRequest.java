package com.smartlab.zippy.model.dto.web.request.robot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PickupCommandRequest {
    private boolean pickup;
}
