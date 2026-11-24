package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The allergen check as explicit SQL (spec: Architecture > Allergen check).
 * Reads straight from Postgres, the system of record; nothing here is cached.
 */
@Repository
class AllergenCheckRepository {

    record ProductState(UUID id, String name, Integer allergenVersion, boolean active) {
    }

    record ProfileState(UUID id, String label, int avoidListVersion, boolean hasAvoidList) {
    }

    record MatchRow(UUID productId, UUID profileId, String profileLabel, String allergenId, String allergenName,
                    MatchType matchType) {
    }

    private final JdbcClient jdbc;

    AllergenCheckRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<UUID, ProductState> products(Collection<UUID> productIds) {
        Map<UUID, ProductState> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT id, name, current_allergen_version, active FROM product WHERE id IN (:ids)")
                .param("ids", productIds)
                .query(rs -> {
                    int version = rs.getInt("current_allergen_version");
                    Integer allergenVersion = rs.wasNull() ? null : version;
                    UUID id = rs.getObject("id", UUID.class);
                    result.put(id, new ProductState(id, rs.getString("name"), allergenVersion, rs.getBoolean("active")));
                });
        return result;
    }

    /** All the parent's active profiles, in creation order. */
    List<ProfileState> profiles(UUID userId) {
        return jdbc.sql("""
                        SELECT cp.id, cp.label, cp.avoid_list_version,
                               EXISTS (SELECT 1 FROM avoid_list_entry e WHERE e.profile_id = cp.id) AS has_avoid_list
                        FROM child_profile cp
                        WHERE cp.user_id = :userId AND cp.deleted_at IS NULL
                        ORDER BY cp.created_at, cp.id
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new ProfileState(
                        rs.getObject("id", UUID.class),
                        rs.getString("label"),
                        rs.getInt("avoid_list_version"),
                        rs.getBoolean("has_avoid_list")))
                .list();
    }

    /**
     * Every (product, child, allergen) match at each product's current allergen version.
     *
     * <p>A label allergen matches an avoided allergen when either one covers the other in the
     * taxonomy: avoiding {@code TREE_NUT} matches a label saying {@code TREE_NUT.CASHEW}, and
     * avoiding {@code TREE_NUT.CASHEW} matches a label that only says {@code TREE_NUT}
     * ("tree nuts" could be cashews). This fails toward warning.
     */
    List<MatchRow> matches(UUID userId, Collection<UUID> productIds) {
        List<MatchRow> rows = new ArrayList<>();
        if (productIds.isEmpty()) {
            return rows;
        }
        jdbc.sql("""
                        SELECT DISTINCT p.id AS product_id, cp.id AS profile_id, cp.label, cp.created_at,
                                        pa.allergen_id, a.display_name, a.sort_order, pa.match_type
                        FROM product p
                        JOIN product_allergen pa
                          ON pa.product_id = p.id AND pa.version = p.current_allergen_version
                        JOIN allergen a ON a.id = pa.allergen_id
                        JOIN child_profile cp ON cp.user_id = :userId AND cp.deleted_at IS NULL
                        JOIN avoid_list_entry ale ON ale.profile_id = cp.id
                        JOIN allergen_closure c
                          ON (c.ancestor_id = ale.allergen_id AND c.descendant_id = pa.allergen_id)
                          OR (c.ancestor_id = pa.allergen_id AND c.descendant_id = ale.allergen_id)
                        WHERE p.id IN (:productIds)
                        ORDER BY cp.created_at, cp.id, a.sort_order, pa.allergen_id
                        """)
                .param("userId", userId)
                .param("productIds", productIds)
                .query(rs -> {
                    rows.add(new MatchRow(
                            rs.getObject("product_id", UUID.class),
                            rs.getObject("profile_id", UUID.class),
                            rs.getString("label"),
                            rs.getString("allergen_id"),
                            rs.getString("display_name"),
                            MatchType.valueOf(rs.getString("match_type"))));
                });
        return rows;
    }
}
