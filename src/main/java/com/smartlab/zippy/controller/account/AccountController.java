package com.smartlab.zippy.controller.account;

import com.smartlab.zippy.model.dto.web.request.account.EditProfileRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.account.ProfileResponse;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final UserRepository userRepository;

    @Autowired
    public AccountController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {

        logger.info("Fetching user profile");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            ApiResponse<ProfileResponse> response = ApiResponse.success(
                    ProfileResponse.builder()
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .address(user.getAddress())
                            .build(),
                    "Get user profile successfully"
                    );

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
    }

    @PutMapping("/edit-profile")
    public ResponseEntity<ApiResponse<?>> editProfile(@RequestBody EditProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        logger.info("Profile edit request for user: {}", username);

        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (request.getPhone() != null) {
                user.setPhone(request.getPhone());
            }

            if (request.getAddress() != null) {
                user.setAddress(request.getAddress());
            }

            try {
                User updatedUser = userRepository.save(user);

                ProfileResponse response = ProfileResponse.builder()
                        .firstName(updatedUser.getFirstName())
                        .lastName(updatedUser.getLastName())
                        .email(updatedUser.getEmail())
                        .phone(updatedUser.getPhone())
                        .address(updatedUser.getAddress())
                        .build();

                logger.info("Profile updated successfully for user: {}", username);
                return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
            } catch (Exception e) {
                logger.error("Error updating profile for user: {}", username, e);
                return ResponseEntity.internalServerError().body(ApiResponse.error("An error occurred while updating profile"));
            }
        }

        logger.warn("User not found for profile update: {}", username);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
    }
}
