package com.smartlab.zippy.model.dto.web.response.robot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PickupCommandResponse {

    private String robotCode;
    private String containerCode;
    private boolean pickup;
    private String message;
}
