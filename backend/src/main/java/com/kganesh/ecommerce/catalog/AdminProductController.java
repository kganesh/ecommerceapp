package com.kganesh.ecommerce.catalog;

import com.kganesh.ecommerce.catalog.CatalogDtos.AuditEntry;
import com.kganesh.ecommerce.catalog.CatalogDtos.CreateProductRequest;
import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductPage;
import com.kganesh.ecommerce.catalog.CatalogDtos.SetAllergensRequest;
import com.kganesh.ecommerce.catalog.CatalogDtos.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catalog administration (FR-14). Requires the CATALOG_ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping(path = "/api/v1/admin/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "admin-products", description = "Catalog administration; CATALOG_ADMIN only")
class AdminProductController {

    private final CatalogService catalog;

    AdminProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(operationId = "adminSearchProducts", summary = "Search all products, including inactive ones")
    ProductPage search(@RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "20") int size) {
        return catalog.search(q, false, page, size);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "adminGetProduct", summary = "A product, active or not")
    Product get(@PathVariable("id") UUID id) {
        return catalog.get(id, false);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminCreateProduct", summary = "Create a product, optionally with allergen version 1")
    Product create(@Valid @RequestBody CreateProductRequest request, Authentication auth) {
        return catalog.create(request, auth.getName());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "adminUpdateProduct", summary = "Update name, description, price and active flag")
    Product update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateProductRequest request, Authentication auth) {
        return catalog.update(id, request, auth.getName());
    }

    @PutMapping(path = "/{id}/allergens", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "adminSetProductAllergens", summary = "Replace allergens; creates a new immutable version")
    Product setAllergens(@PathVariable("id") UUID id, @Valid @RequestBody SetAllergensRequest request,
                         Authentication auth) {
        return catalog.setAllergens(id, request.allergens(), request.labelExtractionId(), auth.getName());
    }

    @GetMapping("/{id}/audit")
    @Operation(operationId = "adminProductAudit", summary = "Every edit to a product, oldest first")
    List<AuditEntry> audit(@PathVariable("id") UUID id) {
        return catalog.auditTrail(id);
    }
}
