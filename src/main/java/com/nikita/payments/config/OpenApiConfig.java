package com.nikita.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payments Service API")
                        .version("1.0.0")
                        .description("Resilient payment-processing REST API with idempotency and circuit-breaker protection")
                        .contact(new Contact()
                                .name("Nikita")
                                .email("nikita@callhub.io"))
                        .license(new License().name("MIT")));
    }
}
