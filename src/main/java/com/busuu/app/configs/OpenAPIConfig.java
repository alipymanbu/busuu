package com.busuu.app.configs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Busuu Learn Language API",
                version = "1.0.0",
                description = "API Documentation for Busuu, Unlock Your Multilingual Potential."
        ),
        servers = {
                @Server(url = "http://localhost:8080/api/v1", description = "Local Development Server"),
                @Server(url = "https://busuu.onrender.com/api/v1", description = "Production Server")
        }
)

@SecurityScheme(
        name = "bearer-key",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)

@Configuration
public class OpenAPIConfig {
}
