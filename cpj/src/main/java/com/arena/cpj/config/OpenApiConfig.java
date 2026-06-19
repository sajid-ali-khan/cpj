package com.arena.cpj.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CPJ API Documentation")
                        .version("1.0")
                        .description("API documentation for the CPJ platform. Use the 'Authorize' button to supply the X-Roll-No header for authenticated operations."))
                .addSecurityItem(new SecurityRequirement().addList("SessionToken"))
                .components(new Components()
                        .addSecuritySchemes("SessionToken", new SecurityScheme()
                                .name("X-Roll-No")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Active session token for authentication")));
    }
}
