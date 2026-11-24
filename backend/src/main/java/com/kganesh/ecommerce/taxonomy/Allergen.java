package com.kganesh.ecommerce.taxonomy;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A node in the allergen taxonomy. Matching is by id, never by name or synonym.")
public record Allergen(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TREE_NUT.CASHEW")
        String id,
        @Schema(description = "Parent node id; absent for a root such as PEANUT or TREE_NUT", example = "TREE_NUT")
        String parentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Cashew")
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Label terms that indicate this allergen; for tagging only")
        List<String> synonyms) {
}
