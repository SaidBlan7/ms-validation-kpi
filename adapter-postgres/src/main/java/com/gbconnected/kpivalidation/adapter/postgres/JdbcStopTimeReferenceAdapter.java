package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.StopTimeReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.StopTimeReferencePort;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/** Read-only Spec 04 adapter aligned with canonical DDL v1.2. */
public final class JdbcStopTimeReferenceAdapter implements StopTimeReferencePort {
    private final DataSource dataSource;
    private final Map<String, String> programmingTypeByCode;

    public JdbcStopTimeReferenceAdapter(DataSource dataSource) {
        this(dataSource, Map.of());
    }

    public JdbcStopTimeReferenceAdapter(DataSource dataSource, Map<String, String> programmingTypeByCode) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.programmingTypeByCode = normalizeMapping(programmingTypeByCode);
    }

    @Override
    public List<StopTimeReferenceEntry> findObservedTimes(long plantId, long lineId,
            LocalDate businessDate, ZoneId zoneId) {
        Instant from = businessDate.atStartOfDay(zoneId).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        String sql = """
                WITH bounds AS (
                    SELECT ?::timestamptz AS day_from,
                           ?::timestamptz AS day_to
                ),
                plant_org AS (
                    SELECT pca.organization_id
                      FROM core.plant_context_assignment pca
                     WHERE pca.plant_id = ?
                       AND pca.valid_from <= CURRENT_TIMESTAMP
                       AND (pca.valid_to IS NULL OR pca.valid_to > CURRENT_TIMESTAMP)
                     ORDER BY pca.valid_from DESC
                     LIMIT 1
                ),
                scoped_catalog AS (
                    SELECT c.stop_classification_id,
                           c.classification_code,
                           c.classification_name,
                           c.is_active
                      FROM stops.stop_classification c
                     WHERE c.organization_id IS NULL
                        OR c.organization_id = (SELECT organization_id FROM plant_org)
                ),
                classified_fragments AS (
                    SELECT f.stop_fragment_id,
                           e.stop_event_id,
                           latest.stop_classification_id,
                           GREATEST(
                               0::bigint,
                               LEAST(
                                   COALESCE(f.line_downtime_seconds::bigint,
                                       EXTRACT(EPOCH FROM (
                                           LEAST(COALESCE(f.ended_at, b.day_to), b.day_to)
                                           - GREATEST(f.started_at, b.day_from)
                                       ))::bigint),
                                   EXTRACT(EPOCH FROM (
                                       LEAST(COALESCE(f.ended_at, b.day_to), b.day_to)
                                       - GREATEST(f.started_at, b.day_from)
                                   ))::bigint
                               )
                           ) AS line_downtime_seconds
                    FROM bounds b
                    JOIN stops.stop_fragment f
                      ON f.started_at < b.day_to
                     AND COALESCE(f.ended_at, b.day_to) > b.day_from
                    JOIN stops.stop_event e
                      ON e.stop_event_id = f.stop_event_id
                     AND e.line_id = ?
                    JOIN LATERAL (
                        SELECT ch.stop_classification_id
                          FROM stops.classification_history ch
                         WHERE ch.stop_event_id = e.stop_event_id
                         ORDER BY ch.changed_at DESC, ch.classification_history_id DESC
                         LIMIT 1
                    ) latest ON TRUE
                ),
                observed AS (
                    SELECT cf.stop_classification_id,
                           COALESCE(SUM(cf.line_downtime_seconds), 0) AS recorded_seconds,
                           COUNT(cf.stop_fragment_id) AS fragment_count,
                           COUNT(DISTINCT cf.stop_event_id) AS stop_event_count
                      FROM classified_fragments cf
                     GROUP BY cf.stop_classification_id
                ),
                candidate AS (
                    SELECT stop_classification_id
                      FROM scoped_catalog
                     WHERE is_active = TRUE
                    UNION
                    SELECT stop_classification_id
                      FROM observed
                )
                SELECT c.stop_classification_id,
                       c.classification_code,
                       c.classification_name,
                       c.is_active,
                       COALESCE(o.recorded_seconds, 0) AS recorded_seconds,
                       COALESCE(o.fragment_count, 0) AS fragment_count,
                       COALESCE(o.stop_event_count, 0) AS stop_event_count
                  FROM candidate k
                  JOIN scoped_catalog c
                    ON c.stop_classification_id = k.stop_classification_id
                  LEFT JOIN observed o
                    ON o.stop_classification_id = k.stop_classification_id
                 ORDER BY c.classification_name, c.stop_classification_id
                """;

        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setTimestamp(1, Timestamp.from(from));
            p.setTimestamp(2, Timestamp.from(to));
            p.setLong(3, plantId);
            p.setLong(4, lineId);
            try (ResultSet r = p.executeQuery()) {
                List<StopTimeReferenceEntry> result = new ArrayList<>();
                while (r.next()) {
                    long classificationId = r.getLong("stop_classification_id");
                    String classificationCode = r.getString("classification_code");
                    result.add(new StopTimeReferenceEntry(
                            classificationId,
                            classificationCode,
                            r.getString("classification_name"),
                            r.getBoolean("is_active"),
                            r.getLong("recorded_seconds"),
                            r.getLong("fragment_count"),
                            r.getLong("stop_event_count"),
                            programmingType(classificationCode),
                            aggregateKey(lineId, businessDate, classificationId)));
                }
                return List.copyOf(result);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read canonical v1.2 stop-time reference", ex);
        }
    }

    @Override
    public boolean classificationExistsInPlantScope(long plantId, long stopClassificationId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM stops.stop_classification c
                     WHERE c.stop_classification_id = ?
                       AND (
                            c.organization_id IS NULL
                            OR EXISTS (
                                SELECT 1
                                  FROM core.plant_context_assignment pca
                                 WHERE pca.plant_id = ?
                                   AND pca.organization_id = c.organization_id
                                   AND pca.valid_from <= CURRENT_TIMESTAMP
                                   AND (pca.valid_to IS NULL OR pca.valid_to > CURRENT_TIMESTAMP)
                            )
                       )
                )
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, stopClassificationId);
            p.setLong(2, plantId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getBoolean(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to validate canonical v1.2 stop classification", ex);
        }
    }

    @Override public String source() { return "CANONICAL_STOPS_V1_2"; }

    @Override
    public String programmingTypeState() {
        return programmingTypeByCode.isEmpty() ? "CONFIGURATION_REQUIRED" : "CONFIGURED_BY_CLASSIFICATION_CODE";
    }

    private String programmingType(String classificationCode) {
        if (classificationCode == null) return null;
        return programmingTypeByCode.get(classificationCode.trim().toUpperCase(Locale.ROOT));
    }

    private static String aggregateKey(long lineId, LocalDate businessDate, long classificationId) {
        return "stops:line:" + lineId + ":date:" + businessDate + ":classification:" + classificationId;
    }

    private static Map<String, String> normalizeMapping(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>();
        raw.forEach((code, type) -> {
            if (code == null || code.isBlank() || type == null || type.isBlank()) return;
            String normalizedType = type.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("PROGRAMMED", "UNPROGRAMMED").contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported stop programming type for " + code + ": " + type);
            }
            out.put(code.trim().toUpperCase(Locale.ROOT), normalizedType);
        });
        return Map.copyOf(out);
    }
}
