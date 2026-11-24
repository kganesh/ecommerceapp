package com.kganesh.ecommerce.meta;

import io.swagger.v3.oas.annotations.media.Schema;

/** Identifies the running backend build. Used by the frontend shell and smoke checks. */
@Schema(description = "Identifies the running backend build")
public record AppMeta(
        @Schema(description = "Application name", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Build version", requiredMode = Schema.RequiredMode.REQUIRED)
        String version) {
}
