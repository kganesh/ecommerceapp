package com.kganesh.ecommerce.cart;

import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.safety.SafetyDtos.AffectedProfile;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyMatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public final class CartDtos {

    private CartDtos() {
    }

    public enum LineStatus {
        /** No child's avoid list matches the product's current allergens. */
        CLEAR,
        /** A warning applies and the parent acknowledged it for the current data. */
        ACKNOWLEDGED,
        /** A warning applies with no acknowledgment for the current data (new match, or data changed since: FR-9). */
        NEEDS_ACKNOWLEDGMENT
    }

    @Schema(description = "One cart line with its allergen status, re-checked on every read")
    public record CartLine(
            @Schema(requiredMode = REQUIRED) Product product,
            @Schema(requiredMode = REQUIRED) int qty,
            @Schema(requiredMode = REQUIRED) long lineTotalCents,
            @Schema(requiredMode = REQUIRED) LineStatus status,
            @Schema(requiredMode = REQUIRED, description = "True when the product has no allergen data: unable to verify")
            boolean unverified,
            @Schema(requiredMode = REQUIRED) List<SafetyMatch> matches,
            @Schema(requiredMode = REQUIRED) List<AffectedProfile> affectedProfiles,
            @Schema(requiredMode = REQUIRED, description = "False when the product is no longer sold")
            boolean available) {
    }

    @Schema(description = "The signed-in parent's cart")
    public record Cart(
            @Schema(requiredMode = REQUIRED) List<CartLine> lines,
            @Schema(requiredMode = REQUIRED) long totalCents,
            @Schema(requiredMode = REQUIRED, example = "USD") String currency,
            @Schema(requiredMode = REQUIRED, description = "True if any line needs acknowledging before checkout")
            boolean needsAcknowledgment) {
    }

    @Schema(description = "Add a product. Resend with ackToken after the parent confirms a 409 allergen warning.")
    public record AddItemRequest(
            @Schema(requiredMode = REQUIRED) @NotNull UUID productId,
            @Schema(description = "Defaults to 1") @Min(1) @Max(99) Integer qty,
            @Schema(description = "From the 409 ALLERGEN_WARNING response, after the parent confirmed") UUID ackToken) {
    }

    @Schema(description = "Change a line's quantity")
    public record UpdateQtyRequest(
            @Schema(requiredMode = REQUIRED) @Min(1) @Max(99) int qty) {
    }
}
