package com.smartlab.zippy.controller.auth;

import com.smartlab.zippy.config.JwtService;
import com.smartlab.zippy.exception.GlobalHandlingException.ResourceNotFoundException;
import com.smartlab.zippy.model.dto.request.auth.LoginRequest;
import com.smartlab.zippy.model.dto.request.auth.RegisterRequest;
import com.smartlab.zippy.model.dto.request.auth.VerifyRequest;
import com.smartlab.zippy.model.dto.response.ApiResponse;
import com.smartlab.zippy.model.dto.response.auth.LoginResponse;
import com.smartlab.zippy.model.dto.response.auth.RegisterResponse;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.UserRepository;
import com.smartlab.zippy.service.auth.OtpService;
import com.smartlab.zippy.service.auth.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserService userService,
                          OtpService otpService,
                          PasswordEncoder passwordEncoder,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    /**
     * User login endpoint
     *
     * @param loginRequest Login credentials
     * @return JWT tokens on successful authentication
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for user: {}", loginRequest.getUsername());

        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            // Get user details from the authentication object
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Generate access and refresh tokens
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            // Optional: Update user's last login time if required
        /*
        Optional<User> userByUsername = userRepository.findByUsername(userDetails.getUsername());
        User user = userByUsername.orElseThrow(() -> new UsernameNotFoundException(
            "User not found with username: " + userDetails.getUsername()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        */

            // Build and return successful response
            ApiResponse<LoginResponse> response = ApiResponse.success(
                    LoginResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .build(),
                    "User logged in successfully"
            );

            logger.info("User {} successfully logged in", userDetails.getUsername());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            logger.error("Authentication failed for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        } catch (Exception e) {
            logger.error("Login failed for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An error occurred during login"));
        }
    }

    /**
     * User registration endpoint
     *
     * @param registerRequest Registration data
     * @param bindingResult   Validation results
     * @return Registration status
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            BindingResult bindingResult) {

        logger.info("Registration attempt for email: {}", registerRequest.getEmail());

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getAllErrors().forEach(error -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
            return ResponseEntity.badRequest().body(ApiResponse.error(errors.toString()));
        }

        // Check if passwords match
        if (!registerRequest.isPasswordMatching()) {
            Map<String, String> errors = new HashMap<>();
            errors.put("confirmPassword", "Password and confirmation do not match");
            return ResponseEntity.badRequest().body(ApiResponse.error(errors.toString()));
        }

        // Check if email already exists
        Optional<User> existingUser = userRepository.findByEmail(registerRequest.getEmail());
        if (existingUser.isPresent()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Email already registered"));
        }

        // Create the user
        User newUser = userService.createUser(registerRequest);

        // Generate and send OTP
        String otp = otpService.generateOtp(newUser.getEmail());
        otpService.sendOtp(newUser.getEmail(), otp);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        RegisterResponse.builder()
                                .verificationLink("/verify-otp?email=" + newUser.getEmail())
                                .emailVerificationRequired(true)
                                .build(), "Registration successful, please verify your email with the OTP sent"
                )
        );
    }

    /**
     * OTP verification endpoint
     *
     * @param verifyRequest Request containing email and OTP
     * @return Verification status
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Object>> verifyOtp(
            @Valid @RequestBody VerifyRequest verifyRequest) {

        logger.info("OTP verification attempt for email: {}", verifyRequest.getEmail());

        boolean isValid = otpService.validateOtp(verifyRequest.getEmail(), verifyRequest.getOtp());

        if (isValid) {
            User user = userRepository.findByEmail(verifyRequest.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // Activate user account
            user.setStatus("ACTIVE");
            userRepository.save(user);

            logger.info("OTP verification successful for email: {}", verifyRequest.getEmail());
            return ResponseEntity.ok(ApiResponse.success("OTP verification successful"));
        } else {
            logger.warn("OTP verification failed for email: {}", verifyRequest.getEmail());
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
        }
    }

    /**
     * Resend OTP endpoint
     *
     * @param email User email
     * @return Status of OTP resend operation
     */
    @GetMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Object>> resendOtp(@RequestParam String email) {
        logger.info("OTP resend request for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate new OTP and send
        String otp = otpService.generateOtp(email);
        otpService.sendOtp(email, otp);

        logger.info("OTP resent successfully for email: {}", email);
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully"));
    }
}

