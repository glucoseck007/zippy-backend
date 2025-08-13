package com.smartlab.zippy.controller.auth;

import com.smartlab.zippy.service.auth.JwtService;
import com.smartlab.zippy.model.dto.web.request.auth.LoginRequest;
import com.smartlab.zippy.model.dto.web.request.auth.RefreshTokenRequest;
import com.smartlab.zippy.model.dto.web.request.auth.RegisterRequest;
import com.smartlab.zippy.model.dto.web.request.auth.VerifyRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.auth.LoginResponse;
import com.smartlab.zippy.model.dto.web.response.auth.RegisterResponse;
import com.smartlab.zippy.model.dto.web.response.auth.VerifyResponse;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.UserRepository;
import com.smartlab.zippy.service.auth.OtpService;
import com.smartlab.zippy.service.auth.TokenService;
import com.smartlab.zippy.service.auth.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

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
    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserService userService,
                          OtpService otpService,
                          PasswordEncoder passwordEncoder,
                          UserRepository userRepository,
                          TokenService tokenService,
                          UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * User login endpoint
     *
     * @param loginRequest Login credentials
     * @return JWT tokens on successful authentication
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for user: {}", loginRequest.getCredential());

        try {
            // First, find the user to check status before authentication
            Optional<User> userOptional = userRepository.findByUsername(loginRequest.getCredential());

            if (userOptional.isEmpty()) {
                userOptional = userRepository.findByEmail(loginRequest.getCredential());
            }

            // Check if user is present before calling get()
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid username or password"));
            }

            User user = userOptional.get();

            // Check if user status is PENDING
            if ("PENDING".equals(user.getStatus())) {
                logger.info("User {} has PENDING status, needs verification", loginRequest.getCredential());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Email verification required"));
            }

            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getCredential(),
                            loginRequest.getPassword()
                    )
            );

            // Get user details from the authentication object
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Revoke all existing refresh tokens for this user before issuing new ones
            logger.info("Revoking all existing tokens for user: {}", userDetails.getUsername());
            tokenService.revokeAllUserTokens(userDetails.getUsername());

            // Generate access and refresh tokens
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            // Build and return successful response
            ApiResponse<LoginResponse> response = ApiResponse.success(
                    LoginResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .verificationRequired(false)
                            .build(),
                    "User logged in successfully"
            );

            logger.info("User {} successfully logged in", userDetails.getUsername());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            logger.error("Authentication failed for user: {}", loginRequest.getCredential(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        } catch (Exception e) {
            logger.error("Login failed for user: {}", loginRequest.getCredential(), e);
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
            String status = existingUser.get().getStatus();
            if ("ACTIVE".equals(status)) {
                logger.warn("Email already registered: {}", registerRequest.getEmail());
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Email already registered"));
            } else if ("PENDING".equals(status)) {
                logger.warn("Email verification pending for: {}", registerRequest.getEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponse.error("Email verification pending"));
            }
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
    public ResponseEntity<ApiResponse<VerifyResponse>> verifyOtp(
            @Valid @RequestBody VerifyRequest verifyRequest) {

        String email;
        Optional<User> userOptional = userRepository.findByEmail(verifyRequest.getCredential());
        if (userOptional.isPresent()) {
            email = userOptional.get().getEmail();
        } else {
            userOptional = userRepository.findByUsername(verifyRequest.getCredential());
            if (userOptional.isPresent()) {
                email = userOptional.get().getEmail();
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("User not found"));
            }
        }

        logger.info("OTP verification attempt for email: {}", email);

        boolean isValid = otpService.validateOtp(email, verifyRequest.getOtp());

        if (isValid) {

            User user = userOptional.get();

            // Activate user account
            user.setStatus("ACTIVE");
            userRepository.save(user);

            logger.info("OTP verification successful for user: {}", verifyRequest.getCredential());
//            return ResponseEntity.ok(ApiResponse.success("OTP verification successful"));
            return ResponseEntity.ok(
                    ApiResponse.success(
                            VerifyResponse.builder().success(true).build(), "OTP verification successful"
                    )
            );
        } else {
            logger.warn("OTP verification failed for user: {}", verifyRequest.getCredential());
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
        }
    }

    /**
     * Resend OTP endpoint
     *
     * @param credential User email
     * @return Status of OTP resend operation
     */
    @GetMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Object>> resendOtp(@RequestParam String credential) {
        String email, otp;
        Optional<User> userOptional = userRepository.findByEmail(credential);
        if (userOptional.isPresent()) {
            email = credential;
        } else {
            userOptional = userRepository.findByUsername(credential);
            if (userOptional.isPresent()) {
                email = userOptional.get().getEmail();
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("User not found"));
            }
        }
        otp = otpService.generateOtp(email);
        otpService.sendOtp(email, otp);
        logger.info("OTP resent to email: {}", email);
        return ResponseEntity.ok(ApiResponse.success("OTP resent successfully {}", email));
    }

    /**
     * User logout endpoint
     *
     * @param request HTTP request containing the authorization header
     * @return Success or error response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("No authorization token provided"));
        }

        String jwt = authHeader.substring(7);
        String username = jwtService.extractUsername(jwt);

        if (username != null) {
            // Blacklist the access token
            tokenService.blacklistAccessToken(jwt);

            logger.info("User {} successfully logged out", username);
            return ResponseEntity.ok(ApiResponse.success(null, "Successfully logged out"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid token"));
    }

    /**
     * Refresh access token using refresh token
     *
     * @param refreshTokenRequest The refresh token request
     * @return New access token and refresh token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();

        // Validate refresh token
        String username = tokenService.validateRefreshToken(refreshToken);

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid refresh token"));
        }

        // Load user details and generate new tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Revoke old refresh token
        tokenService.revokeRefreshToken(refreshToken);

        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        ApiResponse<LoginResponse> response = ApiResponse.success(
                LoginResponse.builder()
                        .accessToken(newAccessToken)
                        .refreshToken(newRefreshToken)
                        .verificationRequired(false)
                        .build(),
                "Tokens refreshed successfully"
        );

        logger.info("Tokens refreshed for user: {}", username);
        return ResponseEntity.ok(response);
    }
}
