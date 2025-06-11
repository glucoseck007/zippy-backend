package com.smartlab.zippy.config;

import com.smartlab.zippy.model.entity.Role;
import com.smartlab.zippy.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DataLoader {

    @Bean
    @Profile("!test") // Don't run this for test profiles
    public CommandLineRunner loadData(RoleRepository roleRepository) {
        return args -> {
            // Initialize roles if they don't exist
            if (roleRepository.count() == 0) {
                roleRepository.save(new Role(null, "ADMIN", null));
                roleRepository.save(new Role(null, "USER", null));
                System.out.println("Sample roles have been initialized");
            }
        };
    }
}
