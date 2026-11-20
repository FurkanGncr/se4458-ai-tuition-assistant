package edu.se4458.tuition.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injected so tests can use a fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI tuitionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Tuition API")
                .version("v1")
                .description("""
                        Tuition REST API for the university mobile app, banking app and admin web site.
                        All routes are versioned under /api/v1. Errors use RFC 7807 Problem Details \
                        (application/problem+json). Money uses 2 decimals with HALF_UP rounding."""));
    }
}
