package com.smartlab.zippy.model.dto.web.request.pickup;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendOtpRequest {
    private String orderCode;
    private String tripCode;
}
