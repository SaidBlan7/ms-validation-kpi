package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.MassPerformanceReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.MassPerformanceReferencePort;
import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/** Canonical Spec 05 source adapter confirmed by Data. */
public final class JdbcMassPerformanceReferenceAdapter implements MassPerformanceReferencePort {
    private final DataSource dataSource;
    private final String elaboratedMetricCode;
    private final String deliveredMetricCode;

    public JdbcMassPerformanceReferenceAdapter(DataSource dataSource) {
        this(dataSource, "SCHEDULING_CANONICAL", "ELABORATED_MASS", "DELIVERED_MASS");
    }

    /** sourceMode is retained for configuration compatibility; Spec 05 now always uses the canonical scheduling sources. */
    public JdbcMassPerformanceReferenceAdapter(DataSource dataSource, String sourceMode,
            String elaboratedMetricCode, String deliveredMetricCode) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.elaboratedMetricCode = requiredCode(elaboratedMetricCode, "elaboratedMetricCode");
        this.deliveredMetricCode = requiredCode(deliveredMetricCode, "deliveredMetricCode");
    }

    @Override
    public List<MassPerformanceReferenceEntry> findObservedMasses(long plantId, long lineId,
            LocalDate businessDate, ZoneId zoneId) {
        Instant from = businessDate.atStartOfDay(zoneId).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<MassPerformanceReferenceEntry> out = new ArrayList<>();
        out.addAll(findElaborated(plantId, lineId, businessDate, from, to));
        out.addAll(findDelivered(plantId, lineId, businessDate, from, to));
        out.sort(Comparator.comparing((MassPerformanceReferenceEntry e) -> e.variable().name())
                .thenComparing(MassPerformanceReferenceEntry::uomCode, Comparator.nullsLast(String::compareTo))
                .thenComparingLong(MassPerformanceReferenceEntry::uomId));
        return List.copyOf(out);
    }

    private List<MassPerformanceReferenceEntry> findElaborated(long plantId, long lineId,
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
                  JOIN core.unit_of_measure u ON u.uom_id = be.uom_id
                 WHERE md.plant_id = ?
                   AND md.line_id = ?
                   AND md.dough_id IS NOT NULL
                   AND COALESCE(be.captured_at, be.ended_at, be.started_at) >= ?
                   AND COALESCE(be.captured_at, be.ended_at, be.started_at) < ?
                   AND u.dimension_code = 'MASS'
                 GROUP BY be.uom_id, u.uom_code, u.uom_name, u.dimension_code
                """;
        return aggregate(sql, plantId, lineId, date, from, to,
                MassPerformanceVariable.ELABORATED_MASS, "SCHEDULING_BATCH_EXECUTION");
    }

    private List<MassPerformanceReferenceEntry> findDelivered(long plantId, long lineId,
            LocalDate date, Instant from, Instant to) {
        String sql = """
                WITH delivered AS (
                    SELECT fpo.uom_id AS mass_uom_id,
                           fpo.quantity AS mass_quantity
                      FROM scheduling.finished_product_output fpo
                      JOIN scheduling.production_execution pe
                        ON pe.production_execution_id = fpo.production_execution_id
                      JOIN manufacturing.manufacturing_definition md
                        ON md.manufacturing_definition_id = pe.manufacturing_definition_id
                      JOIN core.unit_of_measure source_uom ON source_uom.uom_id = fpo.uom_id
                     WHERE md.plant_id = ?
                       AND fpo.line_id = ?
                       AND COALESCE(fpo.captured_at, fpo.recorded_at) >= ?
                       AND COALESCE(fpo.captured_at, fpo.recorded_at) < ?
                       AND source_uom.dimension_code = 'MASS'
                    UNION ALL
                    SELECT pc.to_uom_id AS mass_uom_id,
                           fpo.quantity * pc.conversion_quantity AS mass_quantity
                      FROM scheduling.finished_product_output fpo
                      JOIN scheduling.production_execution pe
                        ON pe.production_execution_id = fpo.production_execution_id
                      JOIN manufacturing.manufacturing_definition md
                        ON md.manufacturing_definition_id = pe.manufacturing_definition_id
                      JOIN core.unit_of_measure source_uom ON source_uom.uom_id = fpo.uom_id
                      JOIN manufacturing.packaging_conversion pc
                        ON pc.product_id = fpo.product_id
                       AND pc.from_uom_id = fpo.uom_id
                       AND (pc.line_id IS NULL OR pc.line_id = fpo.line_id)
                       AND pc.valid_from <= COALESCE(fpo.captured_at, fpo.recorded_at)
                       AND (pc.valid_to IS NULL OR pc.valid_to > COALESCE(fpo.captured_at, fpo.recorded_at))
                      JOIN core.unit_of_measure target_uom ON target_uom.uom_id = pc.to_uom_id
                     WHERE md.plant_id = ?
                       AND fpo.line_id = ?
                       AND COALESCE(fpo.captured_at, fpo.recorded_at) >= ?
                       AND COALESCE(fpo.captured_at, fpo.recorded_at) < ?
                       AND source_uom.dimension_code <> 'MASS'
                       AND target_uom.dimension_code = 'MASS'
                )
                SELECT d.mass_uom_id AS uom_id,
                       u.uom_code,
                       u.uom_name,
                       u.dimension_code,
                       SUM(d.mass_quantity) AS quantity,
                       COUNT(*) AS source_count
                  FROM delivered d
                  JOIN core.unit_of_measure u ON u.uom_id = d.mass_uom_id
                 GROUP BY d.mass_uom_id, u.uom_code, u.uom_name, u.dimension_code
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindWindow(p, 1, plantId, lineId, from, to);
            bindWindow(p, 5, plantId, lineId, from, to);
            try (ResultSet r = p.executeQuery()) {
                List<MassPerformanceReferenceEntry> result = new ArrayList<>();
                while (r.next()) {
                    long uomId = r.getLong("uom_id");
                    result.add(entry(MassPerformanceVariable.DELIVERED_MASS, uomId,
                            r.getString("uom_code"), r.getString("uom_name"), r.getString("dimension_code"),
                            r.getBigDecimal("quantity"), r.getLong("source_count"),
                            "SCHEDULING_FINISHED_PRODUCT_OUTPUT_CONVERTED", lineId, date));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read canonical delivered mass", ex);
        }
    }

    private List<MassPerformanceReferenceEntry> aggregate(String sql, long plantId, long lineId,
            LocalDate date, Instant from, Instant to, MassPerformanceVariable variable, String sourceType) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindWindow(p, 1, plantId, lineId, from, to);
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
            throw new IllegalStateException("Unable to read canonical scheduling mass for " + variable, ex);
        }
    }

    private static void bindWindow(PreparedStatement p, int offset, long plantId, long lineId,
            Instant from, Instant to) throws SQLException {
        p.setLong(offset, plantId);
        p.setLong(offset + 1, lineId);
        p.setTimestamp(offset + 2, Timestamp.from(from));
        p.setTimestamp(offset + 3, Timestamp.from(to));
    }

    private static MassPerformanceReferenceEntry entry(MassPerformanceVariable variable, long uomId,
            String uomCode, String uomName, String dimensionCode, java.math.BigDecimal value, long count,
            String sourceType, long lineId, LocalDate date) {
        return new MassPerformanceReferenceEntry(variable, uomId, uomCode, uomName, dimensionCode, value, count,
                sourceType, aggregateKey(lineId, date, variable, uomId));
    }

    private static String aggregateKey(long lineId, LocalDate date, MassPerformanceVariable variable, long uomId) {
        return "mass:line:" + lineId + ":date:" + date + ":variable:" + variable + ":uom:" + uomId;
    }

    @Override public boolean uomExists(long uomId) {
        return exists("SELECT EXISTS (SELECT 1 FROM core.unit_of_measure WHERE uom_id = ?)", uomId,
                "Unable to validate mass UOM");
    }

    @Override public boolean uomIsMassDimension(long uomId) {
        return exists("SELECT EXISTS (SELECT 1 FROM core.unit_of_measure WHERE uom_id = ? AND dimension_code = 'MASS')",
                uomId, "Unable to validate mass UOM dimension");
    }

    private boolean exists(String sql, long value, String error) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, value);
            try (ResultSet r = p.executeQuery()) { return r.next() && r.getBoolean(1); }
        } catch (SQLException ex) { throw new IllegalStateException(error, ex); }
    }

    @Override public String source() { return "CANONICAL_SCHEDULING_MASS_REFERENCE"; }
    @Override public String sourceMode() { return "SCHEDULING_CANONICAL"; }
    @Override public String sourceMapping() {
        return "ELABORATED=batch_execution;DELIVERED=finished_product_output+packaging_conversion_to_MASS";
    }
    @Override public String elaboratedMetricCode() { return elaboratedMetricCode; }
    @Override public String deliveredMetricCode() { return deliveredMetricCode; }

    private static String requiredCode(String raw, String field) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return raw.trim();
    }
}

