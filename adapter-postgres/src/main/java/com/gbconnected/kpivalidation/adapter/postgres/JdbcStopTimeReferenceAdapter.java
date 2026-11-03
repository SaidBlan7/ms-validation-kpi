package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.StopTimeReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.StopTimeReferencePort;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

public final class JdbcStopTimeReferenceAdapter implements StopTimeReferencePort {
    private final DataSource dataSource;

    public JdbcStopTimeReferenceAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
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
                           c.oee_stop_type_code,
                           c.is_active
                      FROM stops.stop_classification c
                     WHERE c.organization_id IS NULL
                        OR c.organization_id = (SELECT organization_id FROM plant_org)
                ),
                -- A fragment's physical seconds are clipped to the plant-local day.
                -- The full-event duration is used to apportion PARTIAL assignments
                -- without multiplying them on multi-day stops.
                fragment_seconds AS (
                    SELECT f.stop_fragment_id, e.stop_event_id,
                           GREATEST(0::bigint, LEAST(
                               COALESCE(f.line_downtime_seconds::bigint,
                                   EXTRACT(EPOCH FROM (LEAST(COALESCE(f.ended_at, b.day_to), b.day_to) - GREATEST(f.started_at, b.day_from)))::bigint),
                               EXTRACT(EPOCH FROM (LEAST(COALESCE(f.ended_at, b.day_to), b.day_to) - GREATEST(f.started_at, b.day_from)))::bigint
                           )) AS day_seconds
                      FROM bounds b
                      JOIN stops.stop_fragment f ON f.started_at < b.day_to
                         AND COALESCE(f.ended_at, b.day_to) > b.day_from
                      JOIN stops.stop_event e ON e.stop_event_id = f.stop_event_id AND e.line_id = ?
                ),
                -- Include fragments outside the target day in the denominator. A
                -- PARTIAL duration belongs to the event, not to each day/fragment.
                event_seconds AS (
                    SELECT e.stop_event_id,
                           COALESCE(SUM(GREATEST(0::bigint, COALESCE(f.line_downtime_seconds::bigint,
                               EXTRACT(EPOCH FROM (COALESCE(f.ended_at, b.day_to) - f.started_at))::bigint))), 0) AS full_seconds
                      FROM bounds b
                      JOIN stops.stop_event e ON e.line_id = ?
                      JOIN (SELECT DISTINCT stop_event_id FROM fragment_seconds) active
                        ON active.stop_event_id = e.stop_event_id
                      JOIN stops.stop_fragment f ON f.stop_event_id = e.stop_event_id
                     GROUP BY e.stop_event_id
                ),
                day_events AS (
                    SELECT fs.stop_event_id, SUM(fs.day_seconds) AS day_seconds,
                           COUNT(*) AS fragment_count
                      FROM fragment_seconds fs
                     GROUP BY fs.stop_event_id
                ),
                latest_total AS (
                    SELECT de.stop_event_id, ch.stop_classification_id,
                           ch.changed_at, ch.classification_history_id
                      FROM day_events de
                      LEFT JOIN LATERAL (
                          SELECT h.stop_classification_id, h.changed_at, h.classification_history_id
                            FROM stops.classification_history h
                           WHERE h.stop_event_id = de.stop_event_id
                             AND h.classification_scope = 'TOTAL'
                           ORDER BY h.changed_at DESC, h.classification_history_id DESC
                           LIMIT 1
                      ) ch ON TRUE
                ),
                partial_by_classification AS (
                    SELECT h.stop_event_id, h.stop_classification_id,
                           SUM(h.applied_duration_seconds::numeric) AS partial_seconds
                      FROM stops.classification_history h
                      JOIN day_events de ON de.stop_event_id = h.stop_event_id
                      JOIN latest_total lt ON lt.stop_event_id = h.stop_event_id
                     WHERE h.classification_scope = 'PARTIAL'
                       AND (lt.classification_history_id IS NULL OR
                           (h.changed_at, h.classification_history_id) >
                           (lt.changed_at, lt.classification_history_id))
                     GROUP BY h.stop_event_id, h.stop_classification_id
                ),
                partial_totals AS (
                    SELECT stop_event_id, SUM(partial_seconds) AS partial_seconds
                      FROM partial_by_classification GROUP BY stop_event_id
                ),
                allocated_partials AS (
                    SELECT p.stop_event_id, p.stop_classification_id,
                           FLOOR(de.day_seconds::numeric * p.partial_seconds /
                               GREATEST(es.full_seconds::numeric, pt.partial_seconds, 1::numeric))::bigint AS seconds
                      FROM partial_by_classification p
                      JOIN partial_totals pt ON pt.stop_event_id = p.stop_event_id
                      JOIN day_events de ON de.stop_event_id = p.stop_event_id
                      JOIN event_seconds es ON es.stop_event_id = p.stop_event_id
                ),
                event_allocation AS (
                    SELECT ap.stop_event_id, ap.stop_classification_id, ap.seconds AS recorded_seconds
                      FROM allocated_partials ap WHERE ap.seconds > 0
                    UNION ALL
                    SELECT de.stop_event_id, lt.stop_classification_id,
                           GREATEST(0::bigint, de.day_seconds - COALESCE((
                               SELECT SUM(ap.seconds) FROM allocated_partials ap
                                WHERE ap.stop_event_id = de.stop_event_id), 0)) AS recorded_seconds
                      FROM day_events de
                      JOIN latest_total lt ON lt.stop_event_id = de.stop_event_id
                     WHERE lt.stop_classification_id IS NOT NULL
                ),
                observed AS (
                    SELECT ea.stop_classification_id,
                           SUM(ea.recorded_seconds) AS recorded_seconds,
                           SUM(de.fragment_count) AS fragment_count,
                           COUNT(DISTINCT ea.stop_event_id) AS stop_event_count
                      FROM event_allocation ea
                      JOIN day_events de ON de.stop_event_id = ea.stop_event_id
                     WHERE ea.recorded_seconds > 0
                     GROUP BY ea.stop_classification_id
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
                       c.oee_stop_type_code,
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
            p.setLong(5, lineId);
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
                            normalizeProgrammingType(r.getString("oee_stop_type_code")),
                            aggregateKey(lineId, businessDate, classificationId)));
                }
                return List.copyOf(result);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read canonical v2 stop-time reference", ex);
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

    @Override public String source() { return "CANONICAL_STOPS_V2_PARTIAL_AWARE"; }

    @Override
    public String programmingTypeState() {
        return "CANONICAL_OEE_STOP_TYPE_CODE";
    }

    @Override
    public String programmingTypeConfigurationKey() {
        return "stops.stop_classification.oee_stop_type_code";
    }

    private static String normalizeProgrammingType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Set.of("PROGRAMMED", "UNPROGRAMMED").contains(normalized) ? normalized : null;
    }

    private static String aggregateKey(long lineId, LocalDate businessDate, long classificationId) {
        return "stops:line:" + lineId + ":date:" + businessDate + ":classification:" + classificationId;
    }
}
