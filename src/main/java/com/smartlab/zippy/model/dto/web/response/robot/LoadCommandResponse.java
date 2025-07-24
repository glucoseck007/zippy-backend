package com.smartlab.zippy.model.dto.web.response.robot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoadCommandResponse {

    private String robotCode;
    private String containerCode;
    private boolean load;
    private String message;
}
