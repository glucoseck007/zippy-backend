package com.smartlab.zippy.model.dto.web.response.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for pickup operations.
 * Contains pickup-related information such as order status and verification details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PickupResponse {
    private String orderCode;
    private String status;
    private String otpSentTo;
    private Boolean verified;
}
