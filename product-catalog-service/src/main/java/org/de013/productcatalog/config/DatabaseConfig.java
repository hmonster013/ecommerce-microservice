package org.de013.productcatalog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
@EnableJpaAuditing
@Slf4j
public class DatabaseConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public CommandLineRunner testDatabaseConnection() {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                log.info("✅ Database connection successful!");
                log.info("Database URL: {}", connection.getMetaData().getURL());
                log.info("Database Product: {}", connection.getMetaData().getDatabaseProductName());
                log.info("Database Version: {}", connection.getMetaData().getDatabaseProductVersion());
            } catch (Exception e) {
                log.error("❌ Database connection failed: {}", e.getMessage());
                throw new RuntimeException("Failed to connect to database", e);
            }
        };
    }
}
