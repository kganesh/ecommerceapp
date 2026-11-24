package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.safety.AllergenCheckService.Assessment;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyMatch;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Records warnings shown and parents' acknowledgments (FR-12), and validates ack tokens.
 *
 * <p>A token is the id shared by the warning rows shown together. It is accepted only if it
 * belongs to the same user and context, has not expired or been used, covers exactly the
 * products that need acknowledging now, and every product's fingerprint (allergen version +
 * all avoid-list versions) is unchanged since the warning was shown.
 */
@Service
public class WarningService {

    public enum Context { ADD_TO_CART, CHECKOUT }

    public record IssuedWarning(UUID token, Instant expiresAt) {
    }

    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final MeterRegistry meters;
    private final Clock clock;

    WarningService(JdbcClient jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
        this.clock = Clock.systemUTC();
    }

    /** Records that these warnings were shown together; returns the token that acknowledges them. */
    @Transactional
    public IssuedWarning issue(UUID userId, Context context, Collection<Assessment> assessments) {
        UUID token = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(TOKEN_LIFETIME);
        for (Assessment a : assessments) {
            UUID warningId = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO allergen_warning
                                (id, token, user_id, product_id, allergen_version, fingerprint, context, unverified, expires_at)
                            VALUES (:id, :token, :userId, :productId, :version, :fingerprint, :context, :unverified, :expiresAt)
                            """)
                    .param("id", warningId)
                    .param("token", token)
                    .param("userId", userId)
                    .param("productId", a.productId())
                    .param("version", a.allergenVersion(), java.sql.Types.INTEGER)
                    .param("fingerprint", a.fingerprint())
                    .param("context", context.name())
                    .param("unverified", a.status() == SafetyDtos.SafetyStatus.UNVERIFIED)
                    .param("expiresAt", Timestamp.from(expiresAt))
                    .update();
            for (SafetyMatch m : a.matches()) {
                jdbc.sql("""
                                INSERT INTO allergen_warning_match (warning_id, profile_id, allergen_id, match_type)
                                VALUES (:warningId, :profileId, :allergenId, :matchType)
                                """)
                        .param("warningId", warningId)
                        .param("profileId", m.profileId())
                        .param("allergenId", m.allergenId())
                        .param("matchType", m.matchType().name())
                        .update();
                meters.counter("allergen.warnings.shown", "match_type", m.matchType().name(),
                        "context", context.name()).increment();
            }
            if (a.matches().isEmpty()) {
                meters.counter("allergen.warnings.shown", "match_type", "UNVERIFIED", "context", context.name()).increment();
            }
        }
        return new IssuedWarning(token, expiresAt);
    }

    /**
     * Acknowledges the warnings behind {@code token} if it still describes exactly the
     * current situation. Returns warning id by product, or empty if the token is stale,
     * expired, used, or belongs to someone else (the caller shows a fresh warning).
     */
    @Transactional
    public Optional<Map<UUID, UUID>> redeem(UUID userId, UUID token, Context context, Collection<Assessment> current) {
        record Row(UUID id, UUID productId, String fingerprint, String context, Instant expiresAt, boolean used) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT w.id, w.product_id, w.fingerprint, w.context, w.expires_at,
                               EXISTS (SELECT 1 FROM allergen_ack k WHERE k.warning_id = w.id) AS used
                        FROM allergen_warning w
                        WHERE w.token = :token AND w.user_id = :userId
                        """)
                .param("token", token)
                .param("userId", userId)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                        rs.getString("fingerprint").trim(), rs.getString("context"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getBoolean("used")))
                .list();

        Map<UUID, String> needed = new HashMap<>();
        for (Assessment a : current) {
            needed.put(a.productId(), a.fingerprint());
        }
        Instant now = clock.instant();
        boolean valid = !rows.isEmpty()
                && rows.size() == needed.size()
                && rows.stream().allMatch(r -> r.context().equals(context.name())
                        && !r.used()
                        && r.expiresAt().isAfter(now)
                        && r.fingerprint().equals(needed.get(r.productId())));
        if (!valid) {
            return Optional.empty();
        }

        Map<UUID, UUID> acknowledged = new LinkedHashMap<>();
        for (Row r : rows) {
            jdbc.sql("INSERT INTO allergen_ack (id, warning_id, user_id) VALUES (:id, :warningId, :userId)")
                    .param("id", UUID.randomUUID())
                    .param("warningId", r.id())
                    .param("userId", userId)
                    .update();
            acknowledged.put(r.productId(), r.id());
            meters.counter("allergen.acks", "context", context.name()).increment();
        }
        return Optional.of(acknowledged);
    }

    /**
     * The most recent acknowledged warning for this product that still matches the current
     * fingerprint, from any context. An acknowledgment of older data never counts (FR-9).
     */
    @Transactional(readOnly = true)
    public Optional<UUID> currentAcknowledgment(UUID userId, UUID productId, String fingerprint) {
        return jdbc.sql("""
                        SELECT w.id
                        FROM allergen_warning w
                        JOIN allergen_ack k ON k.warning_id = w.id
                        WHERE w.user_id = :userId AND w.product_id = :productId AND w.fingerprint = :fingerprint
                        ORDER BY k.acked_at DESC
                        LIMIT 1
                        """)
                .param("userId", userId)
                .param("productId", productId)
                .param("fingerprint", fingerprint)
                .query(UUID.class)
                .optional();
    }

    /** A recorded warning as shown, for order history (FR-15). */
    public record RecordedWarning(UUID id, String context, boolean unverified, Integer allergenVersion,
                                  Instant shownAt, Instant ackedAt, List<SafetyMatch> matches) {
    }

    @Transactional(readOnly = true)
    public Map<UUID, RecordedWarning> recorded(Collection<UUID> warningIds) {
        Map<UUID, RecordedWarning> result = new LinkedHashMap<>();
        if (warningIds.isEmpty()) {
            return result;
        }
        Map<UUID, List<SafetyMatch>> matches = new HashMap<>();
        jdbc.sql("""
                        SELECT m.warning_id, m.profile_id, cp.label, m.allergen_id, a.display_name, m.match_type
                        FROM allergen_warning_match m
                        JOIN child_profile cp ON cp.id = m.profile_id
                        JOIN allergen a ON a.id = m.allergen_id
                        WHERE m.warning_id IN (:ids)
                        ORDER BY cp.created_at, a.sort_order
                        """)
                .param("ids", warningIds)
                .query(rs -> {
                    matches.computeIfAbsent(rs.getObject("warning_id", UUID.class), k -> new ArrayList<>())
                            .add(new SafetyMatch(rs.getObject("profile_id", UUID.class), rs.getString("label"),
                                    rs.getString("allergen_id"), rs.getString("display_name"),
                                    MatchType.valueOf(rs.getString("match_type"))));
                });
        jdbc.sql("""
                        SELECT w.id, w.context, w.unverified, w.allergen_version, w.shown_at, k.acked_at
                        FROM allergen_warning w
                        LEFT JOIN allergen_ack k ON k.warning_id = w.id
                        WHERE w.id IN (:ids)
                        """)
                .param("ids", warningIds)
                .query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    int version = rs.getInt("allergen_version");
                    Integer allergenVersion = rs.wasNull() ? null : version;
                    Timestamp acked = rs.getTimestamp("acked_at");
                    result.put(id, new RecordedWarning(id, rs.getString("context"), rs.getBoolean("unverified"),
                            allergenVersion, rs.getTimestamp("shown_at").toInstant(),
                            acked == null ? null : acked.toInstant(), matches.getOrDefault(id, List.of())));
                });
        return result;
    }
}
