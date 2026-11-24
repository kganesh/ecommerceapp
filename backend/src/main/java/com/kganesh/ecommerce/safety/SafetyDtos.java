package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Shapes for allergen-check results and warnings (spec: FR-5, FR-7, FR-10, FR-11). */
public final class SafetyDtos {

    private SafetyDtos() {
    }

    public enum SafetyStatus {
        /** No profile's avoid list matches this product's current allergen version. Not a promise of safety. */
        CLEAR,
        /** At least one profile's avoid list matches (CONTAINS or MAY_CONTAIN). */
        WARNING,
        /** The product has no allergen data: unable to verify, treated as a warning (FR-11). */
        UNVERIFIED,
        /** The check could not run. Treated as a warning; never as clear (FR-11). */
        UNAVAILABLE
    }

    @Schema(description = "One child whose avoid list matches one allergen on the product's label")
    public record SafetyMatch(
            @Schema(requiredMode = REQUIRED) UUID profileId,
            @Schema(requiredMode = REQUIRED, example = "Child 1") String profileLabel,
            @Schema(requiredMode = REQUIRED, example = "TREE_NUT.CASHEW") String allergenId,
            @Schema(requiredMode = REQUIRED, example = "Cashew") String allergenName,
            @Schema(requiredMode = REQUIRED) MatchType matchType) {
    }

    @Schema(description = "A child profile affected by a warning")
    public record AffectedProfile(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) String label) {
    }

    @Schema(description = "The allergen check result for one product, for the signed-in parent's children")
    public record ProductSafety(
            @Schema(requiredMode = REQUIRED) SafetyStatus status,
            @Schema(requiredMode = REQUIRED) List<SafetyMatch> matches,
            @Schema(requiredMode = REQUIRED, description = "Children this result concerns; for UNVERIFIED, every child with an avoid list")
            List<AffectedProfile> affectedProfiles) {

        public static ProductSafety unavailable() {
            return new ProductSafety(SafetyStatus.UNAVAILABLE, List.of(), List.of());
        }
    }

    @Schema(description = """
            409 body from add-to-cart: nothing was added. Show every match (child, allergen, \
            contains / may contain) and, if the parent confirms, resend the request with ackToken.""")
    public record AllergenWarning(
            @Schema(requiredMode = REQUIRED, allowableValues = "ALLERGEN_WARNING") String code,
            @Schema(requiredMode = REQUIRED) UUID productId,
            @Schema(requiredMode = REQUIRED) String productName,
            @Schema(description = "Allergen version the warning is based on; absent when the product has no allergen data")
            Integer allergenVersion,
            @Schema(requiredMode = REQUIRED, description = "True when the product has no allergen data: unable to verify")
            boolean unverified,
            @Schema(requiredMode = REQUIRED) List<SafetyMatch> matches,
            @Schema(requiredMode = REQUIRED) List<AffectedProfile> affectedProfiles,
            @Schema(requiredMode = REQUIRED, description = "Single use; void if the product's allergens or any avoid list changes")
            UUID ackToken,
            @Schema(requiredMode = REQUIRED) Instant ackExpiresAt) {
    }
}
