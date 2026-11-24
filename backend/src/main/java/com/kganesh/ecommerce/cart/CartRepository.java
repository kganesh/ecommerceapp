package com.kganesh.ecommerce.cart;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CartRepository {

    public record Item(UUID productId, int qty, Integer allergenVersionAtAdd) {
    }

    static final int MAX_QTY = 99;

    private final JdbcClient jdbc;

    CartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The user's cart id, creating the cart if needed, locked for the rest of the transaction. */
    public UUID lockCart(UUID userId) {
        jdbc.sql("INSERT INTO cart (id, user_id) VALUES (:id, :userId) ON CONFLICT (user_id) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .update();
        return jdbc.sql("SELECT id FROM cart WHERE user_id = :userId FOR UPDATE")
                .param("userId", userId)
                .query(UUID.class)
                .single();
    }

    public List<Item> items(UUID userId) {
        return jdbc.sql("""
                        SELECT i.product_id, i.qty, i.allergen_version_at_add
                        FROM cart c JOIN cart_item i ON i.cart_id = c.id
                        WHERE c.user_id = :userId
                        ORDER BY i.added_at, i.product_id
                        """)
                .param("userId", userId)
                .query((rs, n) -> {
                    int version = rs.getInt("allergen_version_at_add");
                    return new Item(rs.getObject("product_id", UUID.class), rs.getInt("qty"), rs.wasNull() ? null : version);
                })
                .list();
    }

    void addOrIncrement(UUID cartId, UUID productId, int qty, Integer allergenVersion) {
        jdbc.sql("""
                        INSERT INTO cart_item (cart_id, product_id, qty, allergen_version_at_add)
                        VALUES (:cartId, :productId, :qty, :version)
                        ON CONFLICT (cart_id, product_id)
                        DO UPDATE SET qty = LEAST(cart_item.qty + EXCLUDED.qty, :maxQty),
                                      allergen_version_at_add = EXCLUDED.allergen_version_at_add
                        """)
                .param("cartId", cartId)
                .param("productId", productId)
                .param("qty", qty)
                .param("version", allergenVersion, java.sql.Types.INTEGER)
                .param("maxQty", MAX_QTY)
                .update();
        touch(cartId);
    }

    boolean setQty(UUID cartId, UUID productId, int qty) {
        int updated = jdbc.sql("UPDATE cart_item SET qty = :qty WHERE cart_id = :cartId AND product_id = :productId")
                .param("cartId", cartId)
                .param("productId", productId)
                .param("qty", qty)
                .update();
        touch(cartId);
        return updated == 1;
    }

    boolean remove(UUID cartId, UUID productId) {
        int removed = jdbc.sql("DELETE FROM cart_item WHERE cart_id = :cartId AND product_id = :productId")
                .param("cartId", cartId)
                .param("productId", productId)
                .update();
        touch(cartId);
        return removed == 1;
    }

    public void clear(UUID cartId) {
        jdbc.sql("DELETE FROM cart_item WHERE cart_id = :cartId").param("cartId", cartId).update();
        touch(cartId);
    }

    private void touch(UUID cartId) {
        jdbc.sql("UPDATE cart SET updated_at = now() WHERE id = :cartId").param("cartId", cartId).update();
    }
}
