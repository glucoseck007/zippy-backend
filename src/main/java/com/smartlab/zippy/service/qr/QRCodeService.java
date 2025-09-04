package com.smartlab.zippy.service.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.smartlab.zippy.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Service
public class QRCodeService {

    public String generateQRCode(String tripCode, String orderCode, String productCode) {
        try {
            // Build structured QR content
            String qrData = String.format(
                    "{\"tripCode\":\"%s\",\"orderCode\":\"%s\",\"productCode\":\"%s\"}",
                    tripCode, orderCode, productCode
            );

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, 300, 300);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] qrCodeBytes = outputStream.toByteArray();
            String base64QRCode = Base64.getEncoder().encodeToString(qrCodeBytes);

            log.info("QR code generated successfully for tripCode={}, orderCode={}, productCode={}",
                    tripCode, orderCode, productCode);

            return base64QRCode;

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code for tripCode={}, orderCode={}, productCode={}",
                    tripCode, orderCode, productCode, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

}
