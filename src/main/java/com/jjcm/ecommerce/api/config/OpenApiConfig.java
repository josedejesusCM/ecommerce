package com.jjcm.ecommerce.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ecommerceOpenAPI(){
        return new OpenAPI().info(
                new Info().title("ecommerce-api")
                        .description("REST API for managing categories, products, orders, customers and payments.")
                        .version("v1")
                        .contact(new Contact().name("Jose de Jesus").email(""))
                        .license(new License().name("Apache 2.0")));
    }
}
