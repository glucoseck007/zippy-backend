package com.smartlab.zippy.model.dto.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotQrCodeMqttDTO {
    private String qrCode; // base64 encoded
    private int status; // 0=Canceled, 1=Done, 2=Waiting
}
