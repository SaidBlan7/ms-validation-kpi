package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.MassPerformanceReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.MassPerformanceReferencePort;
import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Read-only Spec 05 adapter for canonical v1.2.
 *
 * Preferred source: telemetry.derived_metric using configurable semantic metric codes and an exact
 * plant-local D-1 line window. Only line-level rows (machine/manufacturing definition null) are used.
 *
 * AUTO mode falls back per variable when no line-level derived metric exists:
 * - ELABORATED_MASS -> scheduling.batch_execution.quantity for executions whose manufacturing
 *   definition contains a dough, grouped by MASS UOM.
 * - DELIVERED_MASS -> scheduling.finished_product_output.quantity, grouped by MASS UOM.
 *
 * The fallback is deliberately exposed as provisional in each entry; it is not presented as the
 * official OEE performance formula.
 */
public final class JdbcMassPerformanceReferenceAdapter implements MassPerformanceReferencePort {
    private static final Set<String> SUPPORTED_MODES = Set.of("AUTO", "DERIVED_METRIC_ONLY", "SCHEDULING_FALLBACK_ONLY");

    private final DataSource dataSource;
    private final String sourceMode;
    private final String elaboratedMetricCode;
    private final String deliveredMetricCode;

    public JdbcMassPerformanceReferenceAdapter(DataSource dataSource) {
        this(dataSource, "AUTO", "ELABORATED_MASS", "DELIVERED_MASS");
    }

    public JdbcMassPerformanceReferenceAdapter(DataSource dataSource, String sourceMode,
            String elaboratedMetricCode, String deliveredMetricCode) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.sourceMode = normalizeMode(sourceMode);
        this.elaboratedMetricCode = requiredCode(elaboratedMetricCode, "elaboratedMetricCode");
        this.deliveredMetricCode = requiredCode(deliveredMetricCode, "deliveredMetricCode");
    }

    @Override
    public List<MassPerformanceReferenceEntry> findObservedMasses(long plantId, long lineId,
            LocalDate businessDate, ZoneId zoneId) {
        Instant from = businessDate.atStartOfDay(zoneId).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<MassPerformanceReferenceEntry> out = new ArrayList<>();

        loadVariable(out, plantId, lineId, businessDate, from, to,
                MassPerformanceVariable.ELABORATED_MASS, elaboratedMetricCode);
        loadVariable(out, plantId, lineId, businessDate, from, to,
                MassPerformanceVariable.DELIVERED_MASS, deliveredMetricCode);

        out.sort(Comparator.comparing((MassPerformanceReferenceEntry e) -> e.variable().name())
                .thenComparing(MassPerformanceReferenceEntry::uomCode, Comparator.nullsLast(String::compareTo))
                .thenComparingLong(MassPerformanceReferenceEntry::uomId));
        return List.copyOf(out);
    }

    private void loadVariable(List<MassPerformanceReferenceEntry> out, long plantId, long lineId,
            LocalDate businessDate, Instant from, Instant to, MassPerformanceVariable variable, String metricCode) {
        if (!"SCHEDULING_FALLBACK_ONLY".equals(sourceMode)) {
            List<MassPerformanceReferenceEntry> derived = findDerivedMetric(lineId, businessDate, from, to, variable, metricCode);
            if (!derived.isEmpty()) {
                out.addAll(derived);
                return;
            }
            if ("DERIVED_METRIC_ONLY".equals(sourceMode)) {
                return;
            }
        }

        out.addAll(variable == MassPerformanceVariable.ELABORATED_MASS
                ? findElaboratedFallback(plantId, lineId, businessDate, from, to)
                : findDeliveredFallback(plantId, lineId, businessDate, from, to));
    }

    private List<MassPerformanceReferenceEntry> findDerivedMetric(long lineId, LocalDate date,
            Instant from, Instant to, MassPerformanceVariable variable, String metricCode) {
        String sql = """
                SELECT DISTINCT ON (dm.value_uom_id)
                       dm.value_uom_id AS uom_id,
                       u.uom_code,
                       u.uom_name,
                       u.dimension_code,
                       dm.numeric_value,
                       dm.derived_metric_id
                FROM telemetry.derived_metric dm
                JOIN core.unit_of_measure u ON u.uom_id = dm.value_uom_id
                WHERE dm.line_id = ?
                  AND dm.machine_id IS NULL
                  AND dm.manufacturing_definition_id IS NULL
                  AND dm.metric_code = ?
                  AND dm.window_started_at = ?
                  AND dm.window_ended_at = ?
                  AND dm.numeric_value IS NOT NULL
                  AND u.dimension_code = 'MASS'
                ORDER BY dm.value_uom_id, dm.calculated_at DESC, dm.derived_metric_id DESC
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId);
            p.setString(2, metricCode);
            p.setTimestamp(3, Timestamp.from(from));
            p.setTimestamp(4, Timestamp.from(to));
            try (ResultSet r = p.executeQuery()) {
                List<MassPerformanceReferenceEntry> result = new ArrayList<>();
                while (r.next()) {
                    long uomId = r.getLong("uom_id");
                    result.add(entry(variable, uomId, r.getString("uom_code"), r.getString("uom_name"),
                            r.getString("dimension_code"), r.getBigDecimal("numeric_value"), 1L,
                            "TELEMETRY_DERIVED_METRIC", lineId, date));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read canonical derived mass metric " + metricCode, ex);
        }
    }

    private List<MassPerformanceReferenceEntry> findElaboratedFallback(long plantId, long lineId,
            LocalDate date, Instant from, Instant to) {
        String sql = """
                SELECT be.uom_id,
                       u.uom_code,
                       u.uom_name,
                       u.dimension_code,
                       SUM(be.quantity) AS quantity,
                       COUNT(*) AS source_count
                FROM scheduling.batch_execution be
                JOIN scheduling.production_execution pe
                  ON pe.production_execution_id = be.production_execution_id
                JOIN manufacturing.manufacturing_definition md
                  ON md.manufacturing_definition_id = pe.manufacturing_definition_id
                JOIN core.unit_of_measure u
                  ON u.uom_id = be.uom_id
                WHERE md.plant_id = ?
                  AND md.line_id = ?
                  AND md.dough_id IS NOT NULL
                  AND COALESCE(be.ended_at, be.started_at) >= ?
                  AND COALESCE(be.ended_at, be.started_at) < ?
                  AND u.dimension_code = 'MASS'
                GROUP BY be.uom_id, u.uom_code, u.uom_name, u.dimension_code
                """;
        return aggregateFallback(sql, plantId, lineId, date, from, to,
                MassPerformanceVariable.ELABORATED_MASS, "SCHEDULING_BATCH_EXECUTION_PROVISIONAL");
    }

    private List<MassPerformanceReferenceEntry> findDeliveredFallback(long plantId, long lineId,
            LocalDate date, Instant from, Instant to) {
        String sql = """
                SELECT fpo.uom_id,
                       u.uom_code,
                       u.uom_name,
                       u.dimension_code,
                       SUM(fpo.quantity) AS quantity,
                       COUNT(*) AS source_count
                FROM scheduling.finished_product_output fpo
                JOIN scheduling.production_execution pe
                  ON pe.production_execution_id = fpo.production_execution_id
                JOIN manufacturing.manufacturing_definition md
                  ON md.manufacturing_definition_id = pe.manufacturing_definition_id
                JOIN core.unit_of_measure u
                  ON u.uom_id = fpo.uom_id
                WHERE md.plant_id = ?
                  AND fpo.line_id = ?
                  AND fpo.recorded_at >= ?
                  AND fpo.recorded_at < ?
                  AND u.dimension_code = 'MASS'
                GROUP BY fpo.uom_id, u.uom_code, u.uom_name, u.dimension_code
                """;
        return aggregateFallback(sql, plantId, lineId, date, from, to,
                MassPerformanceVariable.DELIVERED_MASS, "SCHEDULING_FINISHED_PRODUCT_OUTPUT_PROVISIONAL");
    }

    private List<MassPerformanceReferenceEntry> aggregateFallback(String sql, long plantId, long lineId,
            LocalDate date, Instant from, Instant to, MassPerformanceVariable variable, String sourceType) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId);
            p.setLong(2, lineId);
            p.setTimestamp(3, Timestamp.from(from));
            p.setTimestamp(4, Timestamp.from(to));
            try (ResultSet r = p.executeQuery()) {
                List<MassPerformanceReferenceEntry> result = new ArrayList<>();
                while (r.next()) {
                    long uomId = r.getLong("uom_id");
                    result.add(entry(variable, uomId, r.getString("uom_code"), r.getString("uom_name"),
                            r.getString("dimension_code"), r.getBigDecimal("quantity"), r.getLong("source_count"),
                            sourceType, lineId, date));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read canonical scheduling mass fallback for " + variable, ex);
        }
    }

    private static MassPerformanceReferenceEntry entry(MassPerformanceVariable variable, long uomId,
            String uomCode, String uomName, String dimensionCode, BigDecimal value, long count,
            String sourceType, long lineId, LocalDate date) {
        return new MassPerformanceReferenceEntry(variable, uomId, uomCode, uomName, dimensionCode, value, count,
                sourceType, aggregateKey(lineId, date, variable, uomId));
    }

    private static String aggregateKey(long lineId, LocalDate date, MassPerformanceVariable variable, long uomId) {
        return "mass:line:" + lineId + ":date:" + date + ":variable:" + variable + ":uom:" + uomId;
    }

    @Override
    public boolean uomExists(long uomId) {
        return exists("SELECT EXISTS (SELECT 1 FROM core.unit_of_measure WHERE uom_id = ?)", uomId,
                "Unable to validate mass UOM");
    }

    @Override
    public boolean uomIsMassDimension(long uomId) {
        return exists("SELECT EXISTS (SELECT 1 FROM core.unit_of_measure WHERE uom_id = ? AND dimension_code = 'MASS')",
                uomId, "Unable to validate mass UOM dimension");
    }

    private boolean exists(String sql, long value, String error) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, value);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getBoolean(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(error, ex);
        }
    }

    @Override public String sourceMode() { return sourceMode; }
    @Override public String elaboratedMetricCode() { return elaboratedMetricCode; }
    @Override public String deliveredMetricCode() { return deliveredMetricCode; }

    private static String normalizeMode(String raw) {
        String value = raw == null || raw.isBlank() ? "AUTO" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_MODES.contains(value)) {
            throw new IllegalArgumentException("GBC_MASS_PERFORMANCE_SOURCE_MODE must be AUTO, DERIVED_METRIC_ONLY or SCHEDULING_FALLBACK_ONLY");
        }
        return value;
    }

    private static String requiredCode(String raw, String field) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return raw.trim();
    }
}

