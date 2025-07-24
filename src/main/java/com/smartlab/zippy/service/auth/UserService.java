package com.smartlab.zippy.service.auth;

import com.smartlab.zippy.model.dto.web.request.auth.RegisterRequest;
import com.smartlab.zippy.model.entity.Role;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.RoleRepository;
import com.smartlab.zippy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for user-related operations like creation, updates, and retrieval
 */
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                      RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create a new user from registration data
     * @param request Registration request data
     * @return Created user entity
     */
    @Transactional
    public User createUser(RegisterRequest request) {
        logger.info("Creating new user with email: {}", request.getEmail());

        // Find default user role
        Role userRole = roleRepository.findByRoleName("USER")
                .orElseGet(() -> {
                    logger.warn("Default USER role not found, creating it");
                    Role newRole = new Role();
                    newRole.setRoleName("USER");
                    return roleRepository.save(newRole);
                });

        // Create new user entity
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .username(request.getUsername())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status("PENDING") // Pending until email verification
                .role(userRole)
                .roleId(userRole.getId())
                .build();

        // Save and return the new user
        User savedUser = userRepository.save(user);
        logger.info("User created with ID: {}", savedUser.getId());

        return savedUser;
    }
}
