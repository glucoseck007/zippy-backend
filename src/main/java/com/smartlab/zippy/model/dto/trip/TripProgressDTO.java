package com.smartlab.zippy.model.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripProgressDTO {
    private String tripCode;
    private Integer progress; // 0-100 percentage
    private String timestamp;
}
