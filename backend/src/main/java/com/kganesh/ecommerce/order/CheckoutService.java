package com.kganesh.ecommerce.order;

import com.kganesh.ecommerce.cart.CartRepository;
import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogService;
import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.order.OrderDtos.CheckoutWarning;
import com.kganesh.ecommerce.order.OrderDtos.LineWarning;
import com.kganesh.ecommerce.order.OrderDtos.Order;
import com.kganesh.ecommerce.payment.PaymentGateway;
import com.kganesh.ecommerce.payment.PaymentGateway.ChargeRequest;
import com.kganesh.ecommerce.payment.PaymentGateway.PaymentResult;
import com.kganesh.ecommerce.safety.AllergenCheckService;
import com.kganesh.ecommerce.safety.AllergenCheckService.Assessment;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyStatus;
import com.kganesh.ecommerce.safety.WarningService;
import com.kganesh.ecommerce.safety.WarningService.Context;
import com.kganesh.ecommerce.safety.WarningService.IssuedWarning;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * FR-8: checkout re-checks every line against current data. Lines whose warning was not
 * acknowledged for the current data must be confirmed (one token covers them all) before
 * anything is charged. Idempotency-Key makes a retried checkout return the same order.
 */
@Service
public class CheckoutService {

    public sealed interface Outcome permits Placed, Replayed, NeedsAcknowledgment, Declined {
    }

    public record Placed(Order order) implements Outcome {
    }

    public record Replayed(Order order) implements Outcome {
    }

    public record NeedsAcknowledgment(CheckoutWarning warning) implements Outcome {
    }

    public record Declined(String reason) implements Outcome {
    }

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final CartRepository carts;
    private final CatalogService catalog;
    private final AllergenCheckService check;
    private final WarningService warnings;
    private final PaymentGateway payments;
    private final OrderRepository orders;
    private final OrderService orderViews;

    CheckoutService(CartRepository carts, CatalogService catalog, AllergenCheckService check, WarningService warnings,
                    PaymentGateway payments, OrderRepository orders, OrderService orderViews) {
        this.carts = carts;
        this.catalog = catalog;
        this.check = check;
        this.warnings = warnings;
        this.payments = payments;
        this.orders = orders;
        this.orderViews = orderViews;
    }

    @Transactional
    public Outcome checkout(UUID userId, String idempotencyKey, String paymentToken, UUID ackToken) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw new ApiExceptions.BadRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "Send an Idempotency-Key header of 8 to 64 letters, digits, '-' or '_'");
        }
        // Serializes this user's checkouts, so a double-submit can't create two orders.
        carts.lockCart(userId);
        Optional<UUID> existing = orders.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return new Replayed(orderViews.get(userId, existing.get()));
        }

        List<CartRepository.Item> items = carts.items(userId);
        if (items.isEmpty()) {
            throw new ApiExceptions.BadRequest("CART_EMPTY", "The cart is empty");
        }
        List<UUID> ids = items.stream().map(CartRepository.Item::productId).toList();
        Map<UUID, Product> products = catalog.getMany(ids);
        List<String> unavailable = items.stream()
                .filter(i -> products.get(i.productId()) == null || !products.get(i.productId()).active())
                .map(i -> products.containsKey(i.productId()) ? products.get(i.productId()).name() : i.productId().toString())
                .toList();
        if (!unavailable.isEmpty()) {
            throw new ApiExceptions.Conflict("PRODUCT_UNAVAILABLE",
                    "No longer available; remove from the cart to check out: " + String.join(", ", unavailable));
        }

        // Re-check everything now (FR-8). Unavailable check -> 503, nothing charged (FR-11).
        Map<UUID, Assessment> assessments = check.assess(userId, ids);
        Map<UUID, UUID> acknowledged = new HashMap<>();
        List<Assessment> needing = new ArrayList<>();
        for (UUID id : ids) {
            Assessment a = assessments.get(id);
            if (a == null) {
                throw new ApiExceptions.Conflict("PRODUCT_UNAVAILABLE", "Product " + id + " is no longer available");
            }
            if (a.needsAcknowledgment()) {
                Optional<UUID> ack = warnings.currentAcknowledgment(userId, id, a.fingerprint());
                if (ack.isPresent()) {
                    acknowledged.put(id, ack.get());
                } else {
                    needing.add(a);
                }
            }
        }
        if (!needing.isEmpty()) {
            Optional<Map<UUID, UUID>> redeemed = ackToken == null
                    ? Optional.empty()
                    : warnings.redeem(userId, ackToken, Context.CHECKOUT, needing);
            if (redeemed.isEmpty()) {
                IssuedWarning issued = warnings.issue(userId, Context.CHECKOUT, needing);
                List<LineWarning> lines = needing.stream()
                        .map(a -> new LineWarning(a.productId(), a.productName(), a.allergenVersion(),
                                a.status() == SafetyStatus.UNVERIFIED, a.matches(), a.affectedProfiles()))
                        .toList();
                return new NeedsAcknowledgment(new CheckoutWarning("CHECKOUT_ALLERGEN_WARNING", lines,
                        issued.token(), issued.expiresAt()));
            }
            acknowledged.putAll(redeemed.get());
        }

        long total = 0;
        String currency = "USD";
        List<OrderRepository.NewLine> lines = new ArrayList<>();
        for (CartRepository.Item item : items) {
            Product product = products.get(item.productId());
            Assessment a = assessments.get(item.productId());
            total += product.priceCents() * item.qty();
            currency = product.currency();
            lines.add(new OrderRepository.NewLine(product.id(), product.name(), item.qty(), product.priceCents(),
                    a.allergenVersion(), acknowledged.get(product.id())));
        }

        PaymentResult payment = payments.charge(new ChargeRequest(userId, total, currency, paymentToken, idempotencyKey));
        if (!payment.approved()) {
            // Acknowledgments made in this request are kept; no order, cart unchanged.
            return new Declined(payment.declineReason());
        }

        UUID orderId = orders.insert(userId, idempotencyKey, total, currency, payments.provider(),
                payment.reference(), lines);
        carts.clear(carts.lockCart(userId));
        return new Placed(orderViews.get(userId, orderId));
    }
}
