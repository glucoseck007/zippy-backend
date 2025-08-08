package com.smartlab.zippy.model.dto.web.response.qr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodeResponse {
    private String orderCode;
    private String qrCodeBase64;
    private String robotCode;
    private String message;
}
