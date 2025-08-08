package com.smartlab.zippy.config;

import com.smartlab.zippy.model.entity.Role;
import com.smartlab.zippy.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class DataLoader {

    @Value("${app.load-dummy-data:false}")
    private boolean loadDummyData;

    private static boolean flag = false;

    private final DataSource dataSource;

    public DataLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    @Profile("!test") // Don't run this for test profiles
    public CommandLineRunner initDatabase() throws Exception {
        return args -> {
            if (loadDummyData && !flag) {
                flag = true; // Prevent multiple executions
                log.info("Loading dummy data into the database...");

                Resource resource = new ClassPathResource("data/dummy_data.sql");
                try {
                    ScriptUtils.executeSqlScript(dataSource.getConnection(), resource);
                    log.info("Dummy data loaded successfully.");
                } catch (Exception e) {
                    log.error("Failed to load dummy data: {}", e.getMessage(), e);
                }
            } else {
                log.info("Dummy data loading is disabled. Set 'app.load-dummy-data' to true to enable.");
            }
        };
    }
}

