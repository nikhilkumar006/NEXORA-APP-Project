package com.nexora.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a JSON mapper for optional external integrations.
 * Keeping this bean explicit avoids startup failures when an environment does
 * not auto-register Jackson's mapper through web auto-configuration.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
