package com.kganesh.ecommerce.order;

import com.kganesh.ecommerce.identity.CurrentUserService;
import com.kganesh.ecommerce.order.CheckoutService.Declined;
import com.kganesh.ecommerce.order.CheckoutService.NeedsAcknowledgment;
import com.kganesh.ecommerce.order.CheckoutService.Outcome;
import com.kganesh.ecommerce.order.CheckoutService.Placed;
import com.kganesh.ecommerce.order.CheckoutService.Replayed;
import com.kganesh.ecommerce.order.OrderDtos.CheckoutRequest;
import com.kganesh.ecommerce.order.OrderDtos.CheckoutWarning;
import com.kganesh.ecommerce.order.OrderDtos.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** FR-8, FR-15: checkout and order history. SHOPPER only. */
@RestController
@Tag(name = "orders", description = "Checkout and order history")
class OrderController {

    private final CheckoutService checkout;
    private final OrderService orders;
    private final CurrentUserService users;

    OrderController(CheckoutService checkout, OrderService orders, CurrentUserService users) {
        this.checkout = checkout;
        this.orders = orders;
        this.users = users;
    }

    @PostMapping(path = "/api/v1/carts/me/checkout", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "checkout", summary = "Re-check the cart, then charge and place the order",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order placed",
                            content = @Content(schema = @Schema(implementation = Order.class))),
                    @ApiResponse(responseCode = "200", description = "Same Idempotency-Key as a placed order: that order",
                            content = @Content(schema = @Schema(implementation = Order.class))),
                    @ApiResponse(responseCode = "409", description = "Allergen confirmation needed (CHECKOUT_ALLERGEN_WARNING), or PRODUCT_UNAVAILABLE",
                            content = @Content(schema = @Schema(implementation = CheckoutWarning.class))),
                    @ApiResponse(responseCode = "402", description = "Payment declined; no order placed",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "503", description = "Allergen check unavailable; nothing charged",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<Object> checkout(
            @Parameter(description = "Unique per checkout attempt; reuse it on retries", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            Authentication auth) {
        Outcome outcome = checkout.checkout(users.userId(auth), idempotencyKey, request.paymentToken(), request.ackToken());
        return switch (outcome) {
            case Placed placed -> ResponseEntity.status(HttpStatus.CREATED).body(placed.order());
            case Replayed replayed -> ResponseEntity.ok(replayed.order());
            case NeedsAcknowledgment warning -> ResponseEntity.status(HttpStatus.CONFLICT).body(warning.warning());
            case Declined declined -> {
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED,
                        "Payment was declined (" + declined.reason() + "). No order was placed.");
                problem.setProperty("code", "PAYMENT_DECLINED");
                yield ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
            }
        };
    }

    @GetMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listOrders", summary = "The signed-in parent's orders, newest first")
    List<Order> list(Authentication auth) {
        return orders.list(users.userId(auth));
    }

    @GetMapping(path = "/api/v1/orders/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getOrder", summary = "One order, with any allergen warnings acknowledged for it")
    Order get(@PathVariable("id") UUID id, Authentication auth) {
        return orders.get(users.userId(auth), id);
    }
}
