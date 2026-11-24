package com.kganesh.ecommerce.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OrderRepository {

    record OrderRow(UUID id, String status, Instant placedAt, long totalCents, String currency, String paymentReference) {
    }

    record LineRow(UUID orderId, UUID productId, String productName, int qty, long unitPriceCents,
                   Integer allergenVersion, UUID acknowledgedWarningId) {
    }

    record NewLine(UUID productId, String productName, int qty, long unitPriceCents, Integer allergenVersion,
                   UUID acknowledgedWarningId) {
    }

    private final JdbcClient jdbc;

    OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<UUID> findByIdempotencyKey(UUID userId, String key) {
        return jdbc.sql("SELECT id FROM customer_order WHERE user_id = :userId AND idempotency_key = :key")
                .param("userId", userId)
                .param("key", key)
                .query(UUID.class)
                .optional();
    }

    UUID insert(UUID userId, String key, long totalCents, String currency, String provider, String reference,
                List<NewLine> lines) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO customer_order
                            (id, user_id, idempotency_key, status, total_cents, currency, payment_provider, payment_reference)
                        VALUES (:id, :userId, :key, 'PLACED', :total, :currency, :provider, :reference)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("key", key)
                .param("total", totalCents)
                .param("currency", currency)
                .param("provider", provider)
                .param("reference", reference)
                .update();
        int lineNo = 1;
        for (NewLine line : lines) {
            jdbc.sql("""
                            INSERT INTO order_line (order_id, line_no, product_id, product_name, qty, unit_price_cents,
                                                    allergen_version, acknowledged_warning_id)
                            VALUES (:orderId, :lineNo, :productId, :name, :qty, :price, :version, :warningId)
                            """)
                    .param("orderId", id)
                    .param("lineNo", lineNo++)
                    .param("productId", line.productId())
                    .param("name", line.productName())
                    .param("qty", line.qty())
                    .param("price", line.unitPriceCents())
                    .param("version", line.allergenVersion(), java.sql.Types.INTEGER)
                    .param("warningId", line.acknowledgedWarningId(), java.sql.Types.OTHER)
                    .update();
        }
        return id;
    }

    List<OrderRow> findForUser(UUID userId, UUID orderId) {
        String where = orderId == null ? "" : " AND id = :orderId";
        var spec = jdbc.sql("""
                        SELECT id, status, placed_at, total_cents, currency, payment_reference
                        FROM customer_order
                        WHERE user_id = :userId""" + where + " ORDER BY placed_at DESC, id")
                .param("userId", userId);
        if (orderId != null) {
            spec = spec.param("orderId", orderId);
        }
        return spec.query((rs, n) -> new OrderRow(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getTimestamp("placed_at").toInstant(), rs.getLong("total_cents"),
                        rs.getString("currency").trim(), rs.getString("payment_reference")))
                .list();
    }

    List<LineRow> lines(List<UUID> orderIds) {
        List<LineRow> rows = new ArrayList<>();
        if (orderIds.isEmpty()) {
            return rows;
        }
        jdbc.sql("""
                        SELECT order_id, product_id, product_name, qty, unit_price_cents, allergen_version,
                               acknowledged_warning_id
                        FROM order_line
                        WHERE order_id IN (:ids)
                        ORDER BY order_id, line_no
                        """)
                .param("ids", orderIds)
                .query(rs -> {
                    int version = rs.getInt("allergen_version");
                    Integer allergenVersion = rs.wasNull() ? null : version;
                    rows.add(new LineRow(rs.getObject("order_id", UUID.class), rs.getObject("product_id", UUID.class),
                            rs.getString("product_name"), rs.getInt("qty"), rs.getLong("unit_price_cents"),
                            allergenVersion, rs.getObject("acknowledged_warning_id", UUID.class)));
                });
        return rows;
    }
}
