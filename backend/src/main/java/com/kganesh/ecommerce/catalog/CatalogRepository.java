package com.kganesh.ecommerce.catalog;

import com.kganesh.ecommerce.catalog.CatalogDtos.AllergenEntry;
import com.kganesh.ecommerce.catalog.CatalogDtos.AuditAction;
import com.kganesh.ecommerce.catalog.CatalogDtos.AuditEntry;
import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.catalog.CatalogDtos.ProductAllergen;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Explicit SQL for the catalog. Allergen data sits next to the safety check, so it is
 * kept readable and testable rather than hidden behind ORM mapping.
 */
@Repository
class CatalogRepository {

    record ProductRow(UUID id, String sku, String name, String description, long priceCents,
                      String currency, boolean active, Integer currentAllergenVersion) {
    }

    private static final String PRODUCT_COLUMNS =
            "id, sku, name, description, price_cents, currency, active, current_allergen_version";

    private final JdbcClient jdbc;

    CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ProductRow> findPage(String query, boolean activeOnly, int limit, long offset) {
        Filter filter = Filter.of(query, activeOnly);
        return jdbc.sql("SELECT " + PRODUCT_COLUMNS + " FROM product" + filter.where()
                        + " ORDER BY lower(name), id LIMIT :limit OFFSET :offset")
                .params(filter.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(CatalogRepository::mapProduct)
                .list();
    }

    long count(String query, boolean activeOnly) {
        Filter filter = Filter.of(query, activeOnly);
        return jdbc.sql("SELECT count(*) FROM product" + filter.where())
                .params(filter.params())
                .query(Long.class)
                .single();
    }

    Optional<ProductRow> findById(UUID id) {
        return jdbc.sql("SELECT " + PRODUCT_COLUMNS + " FROM product WHERE id = :id")
                .param("id", id)
                .query(CatalogRepository::mapProduct)
                .optional();
    }

    /** Locks the product row so concurrent allergen edits get distinct version numbers. */
    Optional<ProductRow> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + PRODUCT_COLUMNS + " FROM product WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(CatalogRepository::mapProduct)
                .optional();
    }

    boolean skuExists(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM product WHERE sku = :sku)")
                .param("sku", sku)
                .query(Boolean.class)
                .single();
    }

    void insertProduct(UUID id, String sku, String name, String description, long priceCents, String currency) {
        jdbc.sql("""
                        INSERT INTO product (id, sku, name, description, price_cents, currency)
                        VALUES (:id, :sku, :name, :description, :priceCents, :currency)
                        """)
                .param("id", id)
                .param("sku", sku)
                .param("name", name)
                .param("description", description)
                .param("priceCents", priceCents)
                .param("currency", currency)
                .update();
    }

    void updateProduct(UUID id, String name, String description, long priceCents, boolean active) {
        jdbc.sql("""
                        UPDATE product
                        SET name = :name, description = :description, price_cents = :priceCents,
                            active = :active, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("name", name)
                .param("description", description)
                .param("priceCents", priceCents)
                .param("active", active)
                .update();
    }

    /** Writes a new immutable allergen version and points the product at it. Caller holds the row lock. */
    int insertAllergenVersion(UUID productId, List<AllergenEntry> allergens, String createdBy) {
        int version = jdbc.sql("""
                        SELECT coalesce(max(version), 0) + 1
                        FROM product_allergen_version
                        WHERE product_id = :productId
                        """)
                .param("productId", productId)
                .query(Integer.class)
                .single();
        jdbc.sql("""
                        INSERT INTO product_allergen_version (product_id, version, created_by)
                        VALUES (:productId, :version, :createdBy)
                        """)
                .param("productId", productId)
                .param("version", version)
                .param("createdBy", createdBy)
                .update();
        for (AllergenEntry entry : allergens) {
            jdbc.sql("""
                            INSERT INTO product_allergen (product_id, version, allergen_id, match_type)
                            VALUES (:productId, :version, :allergenId, :matchType)
                            """)
                    .param("productId", productId)
                    .param("version", version)
                    .param("allergenId", entry.allergenId())
                    .param("matchType", entry.matchType().name())
                    .update();
        }
        jdbc.sql("UPDATE product SET current_allergen_version = :version, updated_at = now() WHERE id = :productId")
                .param("productId", productId)
                .param("version", version)
                .update();
        return version;
    }

    /** Allergens of each product's current version, keyed by product id, in taxonomy order. */
    Map<UUID, List<ProductAllergen>> currentAllergens(Collection<UUID> productIds) {
        Map<UUID, List<ProductAllergen>> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT pa.product_id, pa.allergen_id, a.display_name, pa.match_type
                        FROM product p
                        JOIN product_allergen pa
                          ON pa.product_id = p.id AND pa.version = p.current_allergen_version
                        JOIN allergen a ON a.id = pa.allergen_id
                        WHERE p.id IN (:ids)
                        ORDER BY a.sort_order, a.id
                        """)
                .param("ids", productIds)
                .query(rs -> {
                    UUID productId = rs.getObject("product_id", UUID.class);
                    result.computeIfAbsent(productId, k -> new ArrayList<>()).add(new ProductAllergen(
                            rs.getString("allergen_id"),
                            rs.getString("display_name"),
                            MatchType.valueOf(rs.getString("match_type"))));
                });
        return result;
    }

    void audit(String actor, UUID productId, AuditAction action, Integer fromVersion, Integer toVersion) {
        jdbc.sql("""
                        INSERT INTO catalog_audit (actor, product_id, action, from_version, to_version)
                        VALUES (:actor, :productId, :action, :fromVersion, :toVersion)
                        """)
                .param("actor", actor)
                .param("productId", productId)
                .param("action", action.name())
                .param("fromVersion", fromVersion, java.sql.Types.INTEGER)
                .param("toVersion", toVersion, java.sql.Types.INTEGER)
                .update();
    }

    List<AuditEntry> auditTrail(UUID productId) {
        return jdbc.sql("""
                        SELECT action, actor, from_version, to_version, at
                        FROM catalog_audit
                        WHERE product_id = :productId
                        ORDER BY at, id
                        """)
                .param("productId", productId)
                .query((rs, rowNum) -> new AuditEntry(
                        AuditAction.valueOf(rs.getString("action")),
                        rs.getString("actor"),
                        nullableInt(rs, "from_version"),
                        nullableInt(rs, "to_version"),
                        rs.getTimestamp("at").toInstant()))
                .list();
    }

    private static ProductRow mapProduct(ResultSet rs, int rowNum) throws SQLException {
        return new ProductRow(
                rs.getObject("id", UUID.class),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("price_cents"),
                rs.getString("currency"),
                rs.getBoolean("active"),
                nullableInt(rs, "current_allergen_version"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Builds the WHERE clause without untyped NULL parameters, which Postgres cannot infer. */
    private record Filter(String where, Map<String, Object> params) {
        static Filter of(String query, boolean activeOnly) {
            List<String> clauses = new ArrayList<>();
            Map<String, Object> params = new LinkedHashMap<>();
            if (activeOnly) {
                clauses.add("active");
            }
            if (query != null && !query.isBlank()) {
                clauses.add("(lower(name) LIKE :pattern OR lower(sku) LIKE :pattern)");
                params.put("pattern", "%" + escapeLike(query.trim().toLowerCase()) + "%");
            }
            String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
            return new Filter(where, params);
        }

        private static String escapeLike(String value) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }
}
