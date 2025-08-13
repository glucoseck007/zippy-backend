package com.smartlab.zippy.model.dto.web.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupVerifyOtpRequest {
    
    @NotBlank(message = "Order code is required")
    private String orderCode;
    
    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    private String otp;
    
    @NotBlank(message = "Trip code is required")
    private String tripCode;
}
