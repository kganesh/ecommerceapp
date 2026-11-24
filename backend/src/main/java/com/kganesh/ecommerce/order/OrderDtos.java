package com.kganesh.ecommerce.order;

import com.kganesh.ecommerce.safety.SafetyDtos.AffectedProfile;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyMatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public final class OrderDtos {

    private OrderDtos() {
    }

    @Schema(description = "Place the order. Send the same Idempotency-Key header on every retry of this checkout.")
    public record CheckoutRequest(
            @Schema(requiredMode = REQUIRED, description = "Payment token from the provider. Test provider: tok_test_approve or tok_test_decline")
            @NotBlank @Size(max = 200) String paymentToken,
            @Schema(description = "From the 409 CHECKOUT_ALLERGEN_WARNING response, after the parent confirmed")
            UUID ackToken) {
    }

    @Schema(description = "A cart line that needs the parent's confirmation before the order is placed")
    public record LineWarning(
            @Schema(requiredMode = REQUIRED) UUID productId,
            @Schema(requiredMode = REQUIRED) String productName,
            Integer allergenVersion,
            @Schema(requiredMode = REQUIRED) boolean unverified,
            @Schema(requiredMode = REQUIRED) List<SafetyMatch> matches,
            @Schema(requiredMode = REQUIRED) List<AffectedProfile> affectedProfiles) {
    }

    @Schema(description = """
            409 body from checkout: no order placed, nothing charged. Show every line's matches; \
            if the parent confirms, resend checkout with ackToken and the same Idempotency-Key.""")
    public record CheckoutWarning(
            @Schema(requiredMode = REQUIRED, allowableValues = "CHECKOUT_ALLERGEN_WARNING") String code,
            @Schema(requiredMode = REQUIRED) List<LineWarning> lines,
            @Schema(requiredMode = REQUIRED) UUID ackToken,
            @Schema(requiredMode = REQUIRED) Instant ackExpiresAt) {
    }

    @Schema(description = "The warning the parent acknowledged for an order line")
    public record AcknowledgedWarning(
            @Schema(requiredMode = REQUIRED, description = "ADD_TO_CART or CHECKOUT") String context,
            @Schema(requiredMode = REQUIRED) boolean unverified,
            @Schema(requiredMode = REQUIRED) Instant shownAt,
            Instant acknowledgedAt,
            @Schema(requiredMode = REQUIRED) List<SafetyMatch> matches) {
    }

    @Schema(description = "One line of a placed order, as sold")
    public record OrderLine(
            @Schema(requiredMode = REQUIRED) UUID productId,
            @Schema(requiredMode = REQUIRED) String productName,
            @Schema(requiredMode = REQUIRED) int qty,
            @Schema(requiredMode = REQUIRED) long unitPriceCents,
            @Schema(requiredMode = REQUIRED) long lineTotalCents,
            @Schema(description = "Allergen version sold; absent if the product had no allergen data") Integer allergenVersion,
            @Schema(description = "Present when the parent confirmed an allergen warning for this line")
            AcknowledgedWarning acknowledgedWarning) {
    }

    @Schema(description = "A placed order")
    public record Order(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED, allowableValues = "PLACED") String status,
            @Schema(requiredMode = REQUIRED) Instant placedAt,
            @Schema(requiredMode = REQUIRED) long totalCents,
            @Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) String paymentReference,
            @Schema(requiredMode = REQUIRED) List<OrderLine> lines) {
    }
}
