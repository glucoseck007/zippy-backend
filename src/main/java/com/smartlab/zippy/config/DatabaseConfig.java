package com.smartlab.zippy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

@Configuration
@EnableTransactionManagement
@EnableJpaAuditing
public class DatabaseConfig {

    /**
     * This bean is used to provide the current auditor for JPA auditing.
     * In a real application, this would typically return the current authenticated user.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        // For now, just return a fixed value. In a real app, you would get the current user.
        return () -> Optional.of("system");
    }
}
