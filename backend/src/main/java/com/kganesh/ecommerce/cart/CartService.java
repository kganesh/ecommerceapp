package com.kganesh.ecommerce.cart;

import com.kganesh.ecommerce.cart.CartDtos.Cart;
import com.kganesh.ecommerce.cart.CartDtos.CartLine;
import com.kganesh.ecommerce.cart.CartDtos.LineStatus;
import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogService;
import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.safety.AllergenCheckService;
import com.kganesh.ecommerce.safety.AllergenCheckService.Assessment;
import com.kganesh.ecommerce.safety.SafetyDtos.AllergenWarning;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyStatus;
import com.kganesh.ecommerce.safety.WarningService;
import com.kganesh.ecommerce.safety.WarningService.Context;
import com.kganesh.ecommerce.safety.WarningService.IssuedWarning;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cart with the allergen check on every add (FR-7) and on every read (FR-9): line statuses
 * are recomputed from current data, so a changed avoid list or product re-flags the line.
 */
@Service
public class CartService {

    /** Outcome of add-to-cart: either the updated cart, or a warning that must be acknowledged first. */
    public sealed interface AddResult permits Added, NeedsAcknowledgment {
    }

    public record Added(Cart cart) implements AddResult {
    }

    public record NeedsAcknowledgment(AllergenWarning warning) implements AddResult {
    }

    private final CartRepository carts;
    private final CatalogService catalog;
    private final AllergenCheckService check;
    private final WarningService warnings;

    CartService(CartRepository carts, CatalogService catalog, AllergenCheckService check, WarningService warnings) {
        this.carts = carts;
        this.catalog = catalog;
        this.check = check;
        this.warnings = warnings;
    }

    @Transactional
    public Cart get(UUID userId) {
        return view(userId);
    }

    /**
     * FR-7. Nothing is added while a warning applies, unless {@code ackToken} acknowledges
     * exactly the current warning. A failed check throws {@link AllergenCheckService.Unavailable}
     * and adds nothing (FR-11).
     */
    @Transactional
    public AddResult add(UUID userId, UUID productId, int qty, UUID ackToken) {
        UUID cartId = carts.lockCart(userId);
        Assessment assessment = check.assess(userId, List.of(productId)).get(productId);
        if (assessment == null || !assessment.active()) {
            throw new ApiExceptions.NotFound("Product " + productId + " not found");
        }

        if (assessment.needsAcknowledgment()) {
            boolean acknowledged = ackToken != null
                    && warnings.redeem(userId, ackToken, Context.ADD_TO_CART, List.of(assessment)).isPresent();
            if (!acknowledged) {
                IssuedWarning issued = warnings.issue(userId, Context.ADD_TO_CART, List.of(assessment));
                return new NeedsAcknowledgment(new AllergenWarning("ALLERGEN_WARNING", productId, assessment.productName(),
                        assessment.allergenVersion(), assessment.status() == SafetyStatus.UNVERIFIED,
                        assessment.matches(), assessment.affectedProfiles(), issued.token(), issued.expiresAt()));
            }
        }

        carts.addOrIncrement(cartId, productId, qty, assessment.allergenVersion());
        return new Added(view(userId));
    }

    @Transactional
    public Cart setQty(UUID userId, UUID productId, int qty) {
        UUID cartId = carts.lockCart(userId);
        if (!carts.setQty(cartId, productId, qty)) {
            throw new ApiExceptions.NotFound("Product " + productId + " is not in the cart");
        }
        return view(userId);
    }

    @Transactional
    public Cart remove(UUID userId, UUID productId) {
        UUID cartId = carts.lockCart(userId);
        if (!carts.remove(cartId, productId)) {
            throw new ApiExceptions.NotFound("Product " + productId + " is not in the cart");
        }
        return view(userId);
    }

    private Cart view(UUID userId) {
        List<CartRepository.Item> items = carts.items(userId);
        List<UUID> ids = items.stream().map(CartRepository.Item::productId).toList();
        Map<UUID, Product> products = catalog.getMany(ids);
        Map<UUID, Assessment> assessments = check.assess(userId, ids);

        List<CartLine> lines = new ArrayList<>();
        long total = 0;
        boolean needsAck = false;
        String currency = "USD";
        for (CartRepository.Item item : items) {
            Product product = products.get(item.productId());
            Assessment a = assessments.get(item.productId());
            if (product == null || a == null) {
                continue;
            }
            LineStatus status = LineStatus.CLEAR;
            if (a.needsAcknowledgment()) {
                Optional<UUID> ack = warnings.currentAcknowledgment(userId, a.productId(), a.fingerprint());
                status = ack.isPresent() ? LineStatus.ACKNOWLEDGED : LineStatus.NEEDS_ACKNOWLEDGMENT;
            }
            needsAck |= status == LineStatus.NEEDS_ACKNOWLEDGMENT;
            long lineTotal = product.priceCents() * item.qty();
            if (product.active()) {
                total += lineTotal;
            }
            currency = product.currency();
            lines.add(new CartLine(product, item.qty(), lineTotal, status, a.status() == SafetyStatus.UNVERIFIED,
                    a.matches(), a.affectedProfiles(), product.active()));
        }
        return new Cart(lines, total, currency, needsAck);
    }
}
