package com.kganesh.ecommerce.meta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "meta", description = "Build and runtime information")
class MetaController {

    private final AppMeta meta;

    MetaController(@Value("${spring.application.name}") String name,
                   ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.meta = new AppMeta(name, build != null ? build.getVersion() : "dev");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getMeta", summary = "Name and version of the running backend")
    AppMeta get() {
        return meta;
    }
}
