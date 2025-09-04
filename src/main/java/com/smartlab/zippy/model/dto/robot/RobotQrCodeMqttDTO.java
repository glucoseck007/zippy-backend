package com.smartlab.zippy.model.dto.robot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotQrCodeMqttDTO {
    private String qrCode; // base64 encoded
    private int status; // 0=Canceled, 1=Done, 2=Waiting

}
