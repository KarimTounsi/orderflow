package com.example.orderflow.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow - Order Service API")
                        .description("Order management and event publishing (Kafka, saga choreography)")
                        .version("1.0.0")
                        .license(new License().name("MIT")));
    }
}
