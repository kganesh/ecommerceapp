package com.kganesh.ecommerce.taxonomy;

import com.kganesh.ecommerce.common.ApiExceptions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Reads the allergen taxonomy and expands avoid-list entries to everything they cover. */
@Service
public class TaxonomyService {

    private final JdbcClient jdbc;

    TaxonomyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Allergen> listAll() {
        return jdbc.sql("""
                        SELECT id, parent_id, display_name, synonyms
                        FROM allergen
                        ORDER BY sort_order, id
                        """)
                .query((rs, rowNum) -> new Allergen(
                        rs.getString("id"),
                        rs.getString("parent_id"),
                        rs.getString("display_name"),
                        toList(rs.getArray("synonyms"))))
                .list();
    }

    /**
     * Returns every allergen id covered by the given ids: each id itself plus all its
     * descendants. Avoiding {@code TREE_NUT} therefore covers {@code TREE_NUT.CASHEW}.
     *
     * @throws ApiExceptions.BadRequest if any id is not in the taxonomy; an unknown id
     *                                  must never be silently ignored (FR-11 fails toward warning)
     */
    public Set<String> expand(Collection<String> allergenIds) {
        if (allergenIds.isEmpty()) {
            return Set.of();
        }
        requireKnown(allergenIds);
        return new TreeSet<>(jdbc.sql("""
                        SELECT DISTINCT descendant_id
                        FROM allergen_closure
                        WHERE ancestor_id IN (:ids)
                        """)
                .param("ids", allergenIds)
                .query(String.class)
                .list());
    }

    /** Rejects ids that are not in the taxonomy. */
    public void requireKnown(Collection<String> allergenIds) {
        if (allergenIds.isEmpty()) {
            return;
        }
        Set<String> known = new TreeSet<>(jdbc.sql("SELECT id FROM allergen WHERE id IN (:ids)")
                .param("ids", allergenIds)
                .query(String.class)
                .list());
        Set<String> unknown = new TreeSet<>(allergenIds);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new ApiExceptions.BadRequest("UNKNOWN_ALLERGEN", "Unknown allergen ids: " + unknown);
        }
    }

    private static List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
