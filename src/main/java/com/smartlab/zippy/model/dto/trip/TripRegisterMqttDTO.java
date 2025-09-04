package com.smartlab.zippy.model.dto.trip;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripRegisterMqttDTO {
    private String trip_id;
    private String start_point;
    private String end_point;
}
