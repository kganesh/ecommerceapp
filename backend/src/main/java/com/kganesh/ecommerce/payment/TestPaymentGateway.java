package com.kganesh.ecommerce.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Test payment provider for local development and tests, until the real provider is
 * chosen (spec: Items awaiting sign-off). Accepts only test tokens, never card numbers:
 * <ul>
 *   <li>{@code tok_test_approve}: approved</li>
 *   <li>{@code tok_test_decline}: declined (card_declined)</li>
 *   <li>anything else: declined (invalid_token)</li>
 * </ul>
 * The same idempotency key always yields the same reference, like a real provider.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "test", matchIfMissing = true)
class TestPaymentGateway implements PaymentGateway {

    static final String APPROVE = "tok_test_approve";
    static final String DECLINE = "tok_test_decline";

    @Override
    public String provider() {
        return "test";
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        return switch (request.paymentToken()) {
            case APPROVE -> PaymentResult.approved("test_ch_" + UUID.nameUUIDFromBytes(
                    (request.userId() + ":" + request.idempotencyKey()).getBytes(StandardCharsets.UTF_8)));
            case DECLINE -> PaymentResult.declined("card_declined");
            default -> PaymentResult.declined("invalid_token");
        };
    }
}
