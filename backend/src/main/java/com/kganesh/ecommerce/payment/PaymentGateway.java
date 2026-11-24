package com.kganesh.ecommerce.payment;

import java.util.UUID;

/**
 * Charges a payment through a hosted provider (NFR-11: card data never reaches our
 * servers; the browser gets a token from the provider and we only see that token).
 * The production provider is still to be chosen; {@link TestPaymentGateway} stands in.
 */
public interface PaymentGateway {

    /**
     * @param idempotencyKey passed to the provider so a retried checkout is never charged twice
     */
    record ChargeRequest(UUID userId, long amountCents, String currency, String paymentToken, String idempotencyKey) {
    }

    record PaymentResult(boolean approved, String reference, String declineReason) {

        public static PaymentResult approved(String reference) {
            return new PaymentResult(true, reference, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }

    String provider();

    PaymentResult charge(ChargeRequest request);
}
