package com.example.orderflow.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow - Product Service API")
                        .description("Product catalog and shopping cart management")
                        .version("1.0.0")
                        .license(new License().name("MIT")));
    }
}
