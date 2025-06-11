package com.smartlab.zippy.model.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for handling login requests.
 * Contains user credentials and additional login options.
 */
@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequest {

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Option to remember the user's login session for extended periods
     */
    private boolean rememberMe;

    /**
     * Device information for security tracking and multi-device login management
     */
    private String deviceInfo;

    /**
     * IP address of the client for security logging
     */
    private String ipAddress;

    /**
     * Optional two-factor authentication code if enabled for the user
     */
    private String twoFactorCode;
}
