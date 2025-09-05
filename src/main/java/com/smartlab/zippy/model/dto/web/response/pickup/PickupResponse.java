package com.smartlab.zippy.model.dto.web.response.pickup;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupResponse {
    private String orderCode;
    private String tripCode;
    private String status; // SENT, VERIFIED, FAILED, EXPIRED, etc.
}
