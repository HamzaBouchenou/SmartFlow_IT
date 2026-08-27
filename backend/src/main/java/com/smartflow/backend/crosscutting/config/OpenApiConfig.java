package com.smartflow.backend.crosscutting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * §11.1 - "Documentation OpenAPI générée et disponible dans l'environnement de
 * développement." springdoc-openapi-starter-webmvc-ui already serves /v3/api-docs and
 * /swagger-ui.html with zero configuration by scanning every @RestController; this bean
 * only supplies the human-readable title/description shown on that page.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartFlow IT — API")
                .description("Gestion des demandes internes et pilotage des SLA (cahier des charges §11).")
                .version("v1"));
    }
}
