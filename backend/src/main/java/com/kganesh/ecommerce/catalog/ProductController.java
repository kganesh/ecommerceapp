package com.kganesh.ecommerce.catalog;

import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public browse and search (FR-4). Per-child match flags arrive with the allergen check in M3. */
@RestController
@RequestMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "products", description = "Browse and search the catalog")
class ProductController {

    private final CatalogService catalog;

    ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(operationId = "searchProducts", summary = "Search active products by name or SKU")
    ProductPage search(@RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "20") int size) {
        return catalog.search(q, true, page, size);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProduct", summary = "An active product with its current allergens")
    Product get(@PathVariable("id") UUID id) {
        return catalog.get(id, true);
    }
}
