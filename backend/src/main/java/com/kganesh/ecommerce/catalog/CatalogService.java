package com.kganesh.ecommerce.catalog;

import com.kganesh.ecommerce.catalog.CatalogDtos.AllergenEntry;
import com.kganesh.ecommerce.catalog.CatalogDtos.AuditAction;
import com.kganesh.ecommerce.catalog.CatalogDtos.AuditEntry;
import com.kganesh.ecommerce.catalog.CatalogDtos.CreateProductRequest;
import com.kganesh.ecommerce.catalog.CatalogDtos.Product;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductAllergen;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductPage;
import com.kganesh.ecommerce.catalog.CatalogDtos.UpdateProductRequest;
import com.kganesh.ecommerce.catalog.CatalogRepository.ProductRow;
import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.taxonomy.TaxonomyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogService {

    static final int MAX_PAGE_SIZE = 100;

    private final CatalogRepository repository;
    private final TaxonomyService taxonomy;

    CatalogService(CatalogRepository repository, TaxonomyService taxonomy) {
        this.repository = repository;
        this.taxonomy = taxonomy;
    }

    @Transactional(readOnly = true)
    public ProductPage search(String query, boolean activeOnly, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiExceptions.BadRequest("BAD_PAGE",
                    "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        List<ProductRow> rows = repository.findPage(query, activeOnly, size, (long) page * size);
        long total = repository.count(query, activeOnly);
        return new ProductPage(toProducts(rows), page, size, total);
    }

    @Transactional(readOnly = true)
    public Product get(UUID id, boolean activeOnly) {
        ProductRow row = repository.findById(id)
                .filter(p -> !activeOnly || p.active())
                .orElseThrow(() -> new ApiExceptions.NotFound("Product " + id + " not found"));
        return toProducts(List.of(row)).getFirst();
    }

    @Transactional
    public Product create(CreateProductRequest request, String actor) {
        if (repository.skuExists(request.sku())) {
            throw new ApiExceptions.Conflict("DUPLICATE_SKU", "SKU " + request.sku() + " already exists");
        }
        if (request.allergens() != null) {
            validate(request.allergens());
        }
        UUID id = UUID.randomUUID();
        String currency = request.currency() == null ? "USD" : request.currency();
        repository.insertProduct(id, request.sku(), request.name(), request.description(), request.priceCents(), currency);
        repository.audit(actor, id, AuditAction.PRODUCT_CREATED, null, null);
        if (request.allergens() != null) {
            int version = repository.insertAllergenVersion(id, request.allergens(), actor);
            repository.audit(actor, id, AuditAction.ALLERGENS_CHANGED, null, version);
        }
        return get(id, false);
    }

    @Transactional
    public Product update(UUID id, UpdateProductRequest request, String actor) {
        repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Product " + id + " not found"));
        repository.updateProduct(id, request.name(), request.description(), request.priceCents(), request.active());
        repository.audit(actor, id, AuditAction.PRODUCT_UPDATED, null, null);
        return get(id, false);
    }

    /** Creates a new immutable allergen version (FR-14). Earlier versions stay untouched. */
    @Transactional
    public Product setAllergens(UUID id, List<AllergenEntry> allergens, String actor) {
        ProductRow product = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Product " + id + " not found"));
        validate(allergens);
        int version = repository.insertAllergenVersion(id, allergens, actor);
        repository.audit(actor, id, AuditAction.ALLERGENS_CHANGED, product.currentAllergenVersion(), version);
        return get(id, false);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> auditTrail(UUID id) {
        repository.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Product " + id + " not found"));
        return repository.auditTrail(id);
    }

    private void validate(List<AllergenEntry> allergens) {
        Set<String> seen = new HashSet<>();
        for (AllergenEntry entry : allergens) {
            if (!seen.add(entry.allergenId())) {
                throw new ApiExceptions.BadRequest("DUPLICATE_ALLERGEN",
                        "Allergen " + entry.allergenId() + " is listed more than once; pick one match type");
            }
        }
        taxonomy.requireKnown(seen);
    }

    private List<Product> toProducts(List<ProductRow> rows) {
        Map<UUID, List<ProductAllergen>> allergens =
                repository.currentAllergens(rows.stream().map(ProductRow::id).toList());
        return rows.stream()
                .map(row -> new Product(
                        row.id(), row.sku(), row.name(), row.description(), row.priceCents(), row.currency(),
                        row.active(), row.currentAllergenVersion(), row.currentAllergenVersion() != null,
                        allergens.getOrDefault(row.id(), List.of())))
                .toList();
    }
}
