package com.kganesh.ecommerce.cart;

import com.kganesh.ecommerce.cart.CartDtos.AddItemRequest;
import com.kganesh.ecommerce.cart.CartDtos.Cart;
import com.kganesh.ecommerce.cart.CartDtos.UpdateQtyRequest;
import com.kganesh.ecommerce.cart.CartService.AddResult;
import com.kganesh.ecommerce.cart.CartService.Added;
import com.kganesh.ecommerce.cart.CartService.NeedsAcknowledgment;
import com.kganesh.ecommerce.identity.CurrentUserService;
import com.kganesh.ecommerce.safety.SafetyDtos.AllergenWarning;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** FR-6, FR-7: the cart and the add-to-cart warning contract. SHOPPER only. */
@RestController
@RequestMapping(path = "/api/v1/carts/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "cart", description = "The signed-in parent's cart; every add is allergen-checked")
class CartController {

    private final CartService carts;
    private final CurrentUserService users;

    CartController(CartService carts, CurrentUserService users) {
        this.carts = carts;
        this.users = users;
    }

    @GetMapping
    @Operation(operationId = "getCart", summary = "The cart, with each line re-checked against current data")
    Cart get(Authentication auth) {
        return carts.get(users.userId(auth));
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "addCartItem", summary = "Add a product; 409 with an allergen warning if any child's avoid list matches",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Added",
                            content = @Content(schema = @Schema(implementation = Cart.class))),
                    @ApiResponse(responseCode = "409", description = "Allergen warning: nothing added; resend with ackToken to confirm",
                            content = @Content(schema = @Schema(implementation = AllergenWarning.class))),
                    @ApiResponse(responseCode = "503", description = "Allergen check unavailable: nothing added",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<Object> add(@Valid @RequestBody AddItemRequest request, Authentication auth) {
        int qty = request.qty() == null ? 1 : request.qty();
        AddResult result = carts.add(users.userId(auth), request.productId(), qty, request.ackToken());
        return switch (result) {
            case Added added -> ResponseEntity.status(HttpStatus.CREATED).body(added.cart());
            case NeedsAcknowledgment warning -> ResponseEntity.status(HttpStatus.CONFLICT).body(warning.warning());
        };
    }

    @PatchMapping(path = "/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCartItem", summary = "Change a line's quantity")
    Cart update(@PathVariable("productId") UUID productId, @Valid @RequestBody UpdateQtyRequest request,
                Authentication auth) {
        return carts.setQty(users.userId(auth), productId, request.qty());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(operationId = "removeCartItem", summary = "Remove a line")
    Cart remove(@PathVariable("productId") UUID productId, Authentication auth) {
        return carts.remove(users.userId(auth), productId);
    }
}
