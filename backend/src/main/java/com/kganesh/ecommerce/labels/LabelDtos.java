package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Response shapes for AI label reading (spec: AI assist, FR-16 to FR-20). */
public final class LabelDtos {

    private LabelDtos() {
    }

    public enum SuggestionSource {
        /** Only the model reported it. */
        MODEL,
        /** Only the keyword backstop found it in the text the model read (FR-18). */
        KEYWORD_SCAN,
        /** Both agree. */
        MODEL_AND_KEYWORD_SCAN
    }

    @Schema(description = "An allergen suggested from a label photo. A suggestion only; nothing is saved until an admin saves.")
    public record SuggestedAllergen(
            @Schema(requiredMode = REQUIRED, example = "TREE_NUT.CASHEW") String allergenId,
            @Schema(requiredMode = REQUIRED, example = "Cashew") String displayName,
            @Schema(requiredMode = REQUIRED) MatchType matchType,
            @Schema(requiredMode = REQUIRED) SuggestionSource source,
            @Schema(description = "Printed words the suggestion is based on", example = "cashews") String evidence) {
    }

    @Schema(description = "What was read from one label photo, for the admin to check against the label")
    public record LabelExtraction(
            @Schema(requiredMode = REQUIRED, description = "Pass as labelExtractionId when saving allergens")
            UUID extractionId,
            @Schema(requiredMode = REQUIRED, example = "us.anthropic.claude-sonnet-5-5") String model,
            @Schema(requiredMode = REQUIRED, description = "False when the photo could not be read reliably")
            boolean readable,
            String ingredientsText,
            String containsStatement,
            String mayContainStatement,
            @Schema(requiredMode = REQUIRED) List<SuggestedAllergen> allergens,
            @Schema(requiredMode = REQUIRED, description = "Allergen-like words that match no taxonomy entry")
            List<String> unmatchedTerms,
            String notes) {
    }

    @Schema(description = "Whether label reading is turned on")
    public record LabelReadingStatus(
            @Schema(requiredMode = REQUIRED) boolean enabled,
            @Schema(requiredMode = REQUIRED, example = "us.anthropic.claude-sonnet-5-5") String model) {
    }
}
