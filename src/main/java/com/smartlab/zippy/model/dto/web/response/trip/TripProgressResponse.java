package com.smartlab.zippy.model.dto.web.response.trip;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for trip progress information.
 * Contains trip code, current status, and progress percentage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripProgressResponse {
    private String tripCode;
    private String status;
    private Double progress;
}
