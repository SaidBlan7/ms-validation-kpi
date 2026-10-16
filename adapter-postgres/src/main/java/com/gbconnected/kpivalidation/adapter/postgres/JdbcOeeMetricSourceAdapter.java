package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.OeeMetricSnapshot;
import com.gbconnected.kpivalidation.application.port.out.OeeMetricSourcePort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * v1.2 canonical read-only OEE/KPI source.
 *
 * The exact OEE cutoff remains functionally open, therefore this adapter deliberately resolves only a
 * line-level KPI result whose period exactly matches the current provisional local-calendar D-1 window.
 * It does not fall back to shift/product/execution rows or partial periods because doing so would invent
 * aggregation semantics that are not defined by Spec 01/03.
 */
public final class JdbcOeeMetricSourceAdapter implements OeeMetricSourcePort {
    private final DataSource dataSource;

    public JdbcOeeMetricSourceAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<OeeMetricSnapshot> findMetric(long plantId, long lineId, LocalDate businessDate,
            ZoneId zoneId, String metricCode) {
        if (businessDate == null || zoneId == null || metricCode == null || metricCode.isBlank()) {
            return Optional.empty();
        }

        var periodStart = businessDate.atStartOfDay(zoneId).toInstant();
        var periodEnd = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        String sql = """
                SELECT kr.kpi_result_id,
                       kd.kpi_code,
                       kr.result_value
                  FROM kpi.kpi_result kr
                  JOIN kpi.kpi_definition kd
                    ON kd.kpi_definition_id = kr.kpi_definition_id
                 WHERE kr.plant_id = ?
                   AND kr.line_id = ?
                   AND kd.kpi_code = ?
                   AND kr.shift_id IS NULL
                   AND kr.product_id IS NULL
                   AND kr.production_execution_id IS NULL
                   AND kr.period_start = ?
                   AND kr.period_end = ?
                 ORDER BY kr.calculated_at DESC, kr.kpi_result_id DESC
                 LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId);
            p.setLong(2, lineId);
            p.setString(3, metricCode.trim());
            p.setObject(4, OffsetDateTime.ofInstant(periodStart, ZoneOffset.UTC));
            p.setObject(5, OffsetDateTime.ofInstant(periodEnd, ZoneOffset.UTC));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                long id = r.getLong("kpi_result_id");
                return Optional.of(new OeeMetricSnapshot(
                        plantId,
                        lineId,
                        businessDate,
                        r.getString("kpi_code"),
                        r.getBigDecimal("result_value"),
                        "kpi.kpi_result:" + id));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to resolve canonical KPI result", e);
        }
    }

    @Override
    public String source() {
        return "CANONICAL_KPI_RESULT_V1_2";
    }
}
