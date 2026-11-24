package com.kganesh.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /**
     * A fixed, relative server URL keeps the generated contract identical across
     * environments and ports, so docs/openapi.json can be checked byte-for-byte.
     */
    @Bean
    OpenAPI ecommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ecommerceapp API")
                        .version("v1")
                        .description("Nut-allergy safe shopping app. Contract source of truth: docs/openapi.json"))
                .servers(List.of(new Server().url("/")));
    }
}
