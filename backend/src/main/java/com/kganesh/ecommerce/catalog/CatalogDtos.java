package com.kganesh.ecommerce.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Request and response shapes for the catalog APIs (spec: API specification). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public enum MatchType {
        /** The ingredients include the allergen. */
        CONTAINS,
        /** Precautionary label such as "may contain" or "processed in a facility with". */
        MAY_CONTAIN
    }

    @Schema(description = "An allergen on a product's label, at the product's current allergen version")
    public record ProductAllergen(
            @Schema(requiredMode = REQUIRED, example = "TREE_NUT.CASHEW") String allergenId,
            @Schema(requiredMode = REQUIRED, example = "Cashew") String displayName,
            @Schema(requiredMode = REQUIRED) MatchType matchType) {
    }

    @Schema(description = "A catalog product with the allergens of its current allergen version")
    public record Product(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) String sku,
            @Schema(requiredMode = REQUIRED) String name,
            String description,
            @Schema(requiredMode = REQUIRED, description = "Price in cents") long priceCents,
            @Schema(requiredMode = REQUIRED, example = "USD") String currency,
            @Schema(requiredMode = REQUIRED) boolean active,
            @Schema(description = "Current allergen version; absent when no allergen data has been entered")
            Integer allergenVersion,
            @Schema(requiredMode = REQUIRED,
                    description = "False when no allergen data exists. Clients must show 'Unable to verify allergens', never 'safe'.")
            boolean allergenDataAvailable,
            @Schema(requiredMode = REQUIRED) List<ProductAllergen> allergens) {
    }

    @Schema(description = "One page of products")
    public record ProductPage(
            @Schema(requiredMode = REQUIRED) List<Product> items,
            @Schema(requiredMode = REQUIRED) int page,
            @Schema(requiredMode = REQUIRED) int size,
            @Schema(requiredMode = REQUIRED) long total) {
    }

    @Schema(description = "An allergen to put on a product's label")
    public record AllergenEntry(
            @Schema(requiredMode = REQUIRED, example = "TREE_NUT.CASHEW") @NotBlank String allergenId,
            @Schema(requiredMode = REQUIRED) @NotNull MatchType matchType) {
    }

    @Schema(description = "Create a product. Omit allergens if the label data is not yet known.")
    public record CreateProductRequest(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 64) String sku,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 200) String name,
            String description,
            @Schema(requiredMode = REQUIRED) @PositiveOrZero long priceCents,
            @Schema(description = "ISO 4217 code; defaults to USD", example = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @Schema(description = "Allergens for version 1. Omit for 'no allergen data yet'; send [] for 'verified: none'.")
            @Valid List<AllergenEntry> allergens) {
    }

    @Schema(description = "Update product details. Allergens are changed separately and create a new version.")
    public record UpdateProductRequest(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 200) String name,
            String description,
            @Schema(requiredMode = REQUIRED) @PositiveOrZero long priceCents,
            @Schema(requiredMode = REQUIRED) boolean active) {
    }

    @Schema(description = "Replace a product's allergens. Always creates a new immutable allergen version.")
    public record SetAllergensRequest(
            @Schema(requiredMode = REQUIRED, description = "Complete allergen list; [] means verified to contain none")
            @NotNull @Valid List<AllergenEntry> allergens) {
    }

    public enum AuditAction { PRODUCT_CREATED, PRODUCT_UPDATED, ALLERGENS_CHANGED }

    @Schema(description = "One catalog edit")
    public record AuditEntry(
            @Schema(requiredMode = REQUIRED) AuditAction action,
            @Schema(requiredMode = REQUIRED) String actor,
            Integer fromVersion,
            Integer toVersion,
            @Schema(requiredMode = REQUIRED) Instant at) {
    }
}
