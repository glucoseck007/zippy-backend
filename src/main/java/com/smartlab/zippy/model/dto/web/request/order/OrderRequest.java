package com.smartlab.zippy.model.dto.web.request.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {

    @NotBlank(message = "Sender identifier cannot be empty")
    private String senderIdentifier; // Can be email or phone

    @NotBlank(message = "Receiver identifier cannot be empty")
    private String receiverIdentifier; // Can be email or phone

    @NotBlank(message = "Product name cannot be empty")
    private String productName;

    @NotBlank(message = "Robot code cannot be empty")
    private String robotCode;

    @NotBlank(message = "Start point cannot be empty")
    private String startPoint;

    @NotBlank(message = "Endpoint cannot be empty")
    private String endPoint;

}
