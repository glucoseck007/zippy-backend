package com.smartlab.zippy.model.dto.web.request.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchOrderRequest {

    @NotBlank(message = "Sender identifier cannot be empty")
    private String senderIdentifier; // Can be email or phone

    @NotEmpty(message = "Recipients list cannot be empty")
    @Valid
    private List<OrderRecipient> recipients;

    @NotBlank(message = "Robot code cannot be empty")
    private String robotCode;

    @NotBlank(message = "Robot container code cannot be empty")
    private String robotContainerCode;

    @NotBlank(message = "Start point cannot be empty")
    private String startPoint;

    @NotBlank(message = "Endpoint cannot be empty")
    private String endpoint;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRecipient {
        @NotBlank(message = "Receiver identifier cannot be empty")
        private String receiverIdentifier; // Can be email or phone

        @NotBlank(message = "Product name cannot be empty")
        private String productName;
    }
}
