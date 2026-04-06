package com.profitflow.adapter.in.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc metadata when {@code springdoc.api-docs.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiDocumentationConfig {

    @Bean
    public OpenAPI profitflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ProfitFlow API")
                        .description(
                                "Version 1 REST API. Responses include "
                                        + ApiVersionResponseHeaderFilter.HEADER_NAME + ".")
                        .version(ApiVersionResponseHeaderFilter.VERSION_ONE))
                .addServersItem(new Server().url("/").description("Current host"));
    }
}
