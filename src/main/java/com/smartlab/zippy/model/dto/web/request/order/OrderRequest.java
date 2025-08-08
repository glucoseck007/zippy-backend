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

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Product name cannot be empty")
    private String productName;

    @NotBlank(message = "Robot code cannot be empty")
    private String robotCode;

    @NotBlank(message = "Robot container code cannot be empty")
    private String robotContainerCode;

    @NotBlank(message = "Endpoint cannot be empty")
    private String endpoint;

    @Nullable
    private boolean approved;
}
