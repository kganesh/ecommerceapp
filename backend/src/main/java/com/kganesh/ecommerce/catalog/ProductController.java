package com.kganesh.ecommerce.catalog;

import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductPage;
import com.kganesh.ecommerce.identity.CurrentUserService;
import com.kganesh.ecommerce.safety.AllergenCheckService;
import com.kganesh.ecommerce.safety.SafetyDtos.ProductSafety;
import com.kganesh.ecommerce.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public browse and search (FR-4). For a signed-in shopper each product also carries the
 * allergen check for their children (FR-5), and {@code hideUnsafe=true} leaves out matches.
 */
@RestController
@RequestMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "products", description = "Browse and search the catalog")
class ProductController {

    private final CatalogService catalog;
    private final AllergenCheckService check;
    private final CurrentUserService users;

    ProductController(CatalogService catalog, AllergenCheckService check, CurrentUserService users) {
        this.catalog = catalog;
        this.check = check;
        this.users = users;
    }

    @GetMapping
    @Operation(operationId = "searchProducts",
            summary = "Search active products; shoppers get per-child allergen results and can hide matches")
    ProductPage search(@RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "hideUnsafe", defaultValue = "false") boolean hideUnsafe,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "20") int size,
                       Authentication auth) {
        UUID shopper = shopperId(auth);
        ProductPage result = catalog.search(q, true, hideUnsafe ? shopper : null, page, size);
        if (shopper == null) {
            return result;
        }
        return new ProductPage(withSafety(shopper, result.items()), result.page(), result.size(), result.total());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProduct", summary = "An active product with its current allergens")
    Product get(@PathVariable("id") UUID id, Authentication auth) {
        Product product = catalog.get(id, true);
        UUID shopper = shopperId(auth);
        return shopper == null ? product : withSafety(shopper, List.of(product)).getFirst();
    }

    private List<Product> withSafety(UUID userId, List<Product> products) {
        Map<UUID, ProductSafety> safety = check.assessForDisplay(userId, products.stream().map(Product::id).toList());
        return products.stream()
                .map(p -> p.withSafety(safety.getOrDefault(p.id(), ProductSafety.unavailable())))
                .toList();
    }

    private UUID shopperId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser)) {
            return null;
        }
        boolean shopper = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + Roles.SHOPPER));
        return shopper ? users.userId(auth) : null;
    }
}
