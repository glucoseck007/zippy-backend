package com.smartlab.zippy.model.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCommandMqttDTO {
    private String trip_id;
    private int command_status;
}
