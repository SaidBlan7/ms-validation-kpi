package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidationPersistencePort;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Canonical Spec 01 persistence over kpi.kpi_validation + kpi.kpi_validation_detail. */
public final class JdbcValidationPersistenceAdapter implements ValidationPersistencePort {
    private final DataSource dataSource;

    public JdbcValidationPersistenceAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override public boolean available() { return true; }

    @Override
    public boolean existsForKpiResult(long kpiResultId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM kpi.kpi_validation_detail WHERE kpi_result_id = ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, kpiResultId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getBoolean(1); }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to check existing KPI validation", ex);
        }
    }

    @Override
    public OeeValidationResult create(ValidationPersistenceRequest request) {
        Objects.requireNonNull(request);
        try (Connection c = dataSource.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                setAuditContext(c, request.proposedByUserId(), request.correlationId());
                try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                    lock.setLong(1, request.kpiResultId());
                    lock.execute();
                }
                if (existsForKpiResult(c, request.kpiResultId())) {
                    throw new ValidationAlreadyExistsException("This KPI result already has a validation proposal");
                }

                long validationId;
                String headerSql = """
                        INSERT INTO kpi.kpi_validation
                            (plant_id, line_id, oee_date, status_code, reason_type, justification, cause,
                             action_plan, incident_reference, proposed_by_user_id, proposed_at, expires_at, created_at)
                        VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING kpi_validation_id
                        """;
                try (PreparedStatement p = c.prepareStatement(headerSql)) {
                    p.setLong(1, request.plantId());
                    p.setLong(2, request.lineId());
                    p.setObject(3, request.businessDate());
                    p.setString(4, request.reasonType().name());
                    setNullableString(p, 5, request.justification());
                    setNullableString(p, 6, request.operationalCause());
                    setNullableString(p, 7, request.actionPlan());
                    setNullableString(p, 8, request.incidentReference());
                    p.setLong(9, request.proposedByUserId());
                    p.setObject(10, utc(request.proposedAt()));
                    p.setObject(11, utc(request.expiresAt()));
                    p.setObject(12, utc(request.proposedAt()));
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("No kpi_validation_id returned");
                        validationId = r.getLong(1);
                    }
                }

                String detailSql = """
                        INSERT INTO kpi.kpi_validation_detail
                            (kpi_validation_id, kpi_result_id, stop_classification_id,
                             original_value, proposed_value, uom_id, created_at)
                        VALUES (?, ?, NULL, ?, ?, ?, ?)
                        """;
                try (PreparedStatement p = c.prepareStatement(detailSql)) {
                    p.setLong(1, validationId);
                    p.setLong(2, request.kpiResultId());
                    p.setBigDecimal(3, request.originalValue());
                    p.setBigDecimal(4, request.proposedValue());
                    if (request.uomId() == null) p.setNull(5, Types.BIGINT); else p.setLong(5, request.uomId());
                    p.setObject(6, utc(request.proposedAt()));
                    p.executeUpdate();
                }
                c.commit();
                c.setAutoCommit(oldAutoCommit);
                return findById(validationId).orElseThrow(() -> new IllegalStateException("Persisted validation could not be reloaded"));
            } catch (RuntimeException | SQLException ex) {
                try { c.rollback(); } catch (SQLException ignored) { }
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
                if (ex instanceof RuntimeException re) throw re;
                throw new IllegalStateException("Unable to persist KPI validation", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to open KPI validation transaction", ex);
        }
    }

    @Override
    public Optional<OeeValidationResult> findById(long validationId) {
        String sql = """
                SELECT v.kpi_validation_id, v.status_code, v.plant_id, v.line_id, v.oee_date,
                       kd.kpi_code, d.kpi_result_id, d.original_value, d.proposed_value, d.uom_id,
                       v.reason_type, v.justification, v.cause, v.action_plan, v.incident_reference,
                       v.proposed_by_user_id, v.proposed_at, v.expires_at,
                       v.approved_by_user_id, v.approved_at
                  FROM kpi.kpi_validation v
                  JOIN kpi.kpi_validation_detail d ON d.kpi_validation_id = v.kpi_validation_id
                  LEFT JOIN kpi.kpi_result kr ON kr.kpi_result_id = d.kpi_result_id
                  LEFT JOIN kpi.kpi_definition kd ON kd.kpi_definition_id = kr.kpi_definition_id
                 WHERE v.kpi_validation_id = ?
                   AND d.kpi_result_id IS NOT NULL
                 ORDER BY d.kpi_validation_detail_id
                 LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, validationId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(map(r));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read KPI validation", ex);
        }
    }



    @Override
    public boolean existsForStopTime(long lineId, LocalDate businessDate, long stopClassificationId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM kpi.kpi_validation v
                      JOIN kpi.kpi_validation_detail d
                        ON d.kpi_validation_id = v.kpi_validation_id
                     WHERE v.line_id = ?
                       AND v.oee_date = ?
                       AND d.stop_classification_id = ?
                )
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId);
            p.setObject(2, businessDate);
            p.setLong(3, stopClassificationId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getBoolean(1); }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to check existing stop-time validation", ex);
        }
    }

    @Override
    public StopTimeValidationResult createStopTime(StopTimeValidationPersistenceRequest request) {
        Objects.requireNonNull(request);
        try (Connection c = dataSource.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                setAuditContext(c, request.proposedByUserId(), request.correlationId());
                String lockKey = "STOP_TIME:" + request.lineId() + ":" + request.businessDate() + ":" + request.stopClassificationId();
                try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    lock.setString(1, lockKey);
                    lock.execute();
                }
                if (existsForStopTime(c, request.lineId(), request.businessDate(), request.stopClassificationId())) {
                    throw new ValidationAlreadyExistsException("This stop classification already has a validation proposal for the line/date");
                }

                long validationId;
                String headerSql = """
                        INSERT INTO kpi.kpi_validation
                            (plant_id, line_id, oee_date, status_code, reason_type, justification, cause,
                             action_plan, incident_reference, proposed_by_user_id, proposed_at, expires_at, created_at)
                        VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING kpi_validation_id
                        """;
                try (PreparedStatement p = c.prepareStatement(headerSql)) {
                    p.setLong(1, request.plantId());
                    p.setLong(2, request.lineId());
                    p.setObject(3, request.businessDate());
                    p.setString(4, request.reasonType().name());
                    setNullableString(p, 5, request.justification());
                    setNullableString(p, 6, request.operationalCause());
                    setNullableString(p, 7, request.actionPlan());
                    setNullableString(p, 8, request.incidentReference());
                    p.setLong(9, request.proposedByUserId());
                    p.setObject(10, utc(request.proposedAt()));
                    p.setObject(11, utc(request.expiresAt()));
                    p.setObject(12, utc(request.proposedAt()));
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("No kpi_validation_id returned");
                        validationId = r.getLong(1);
                    }
                }

                String detailSql = """
                        INSERT INTO kpi.kpi_validation_detail
                            (kpi_validation_id, kpi_result_id, stop_classification_id,
                             original_value, proposed_value, uom_id, created_at)
                        VALUES (?, NULL, ?, ?, ?, NULL, ?)
                        """;
                try (PreparedStatement p = c.prepareStatement(detailSql)) {
                    p.setLong(1, validationId);
                    p.setLong(2, request.stopClassificationId());
                    p.setBigDecimal(3, java.math.BigDecimal.valueOf(request.originalSeconds()));
                    p.setBigDecimal(4, java.math.BigDecimal.valueOf(request.proposedSeconds()));
                    p.setObject(5, utc(request.proposedAt()));
                    p.executeUpdate();
                }

                c.commit();
                c.setAutoCommit(oldAutoCommit);
                return findStopTimeById(validationId)
                        .orElseThrow(() -> new IllegalStateException("Persisted stop-time validation could not be reloaded"));
            } catch (RuntimeException | SQLException ex) {
                try { c.rollback(); } catch (SQLException ignored) { }
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
                if (ex instanceof RuntimeException re) throw re;
                throw new IllegalStateException("Unable to persist stop-time validation", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to open stop-time validation transaction", ex);
        }
    }

    @Override
    public Optional<StopTimeValidationResult> findStopTimeById(long validationId) {
        String sql = """
                SELECT v.kpi_validation_id, v.status_code, v.plant_id, v.line_id, v.oee_date,
                       d.stop_classification_id, c.classification_code, c.classification_name,
                       c.oee_stop_type_code, d.original_value, d.proposed_value,
                       v.reason_type, v.justification, v.cause, v.action_plan, v.incident_reference,
                       v.proposed_by_user_id, v.proposed_at, v.expires_at,
                       v.approved_by_user_id, v.approved_at
                  FROM kpi.kpi_validation v
                  JOIN kpi.kpi_validation_detail d ON d.kpi_validation_id = v.kpi_validation_id
                  JOIN stops.stop_classification c ON c.stop_classification_id = d.stop_classification_id
                 WHERE v.kpi_validation_id = ?
                   AND d.stop_classification_id IS NOT NULL
                 ORDER BY d.kpi_validation_detail_id
                 LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, validationId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                Long approvedBy = nullableLong(r, "approved_by_user_id");
                return Optional.of(new StopTimeValidationResult(
                        r.getLong("kpi_validation_id"), r.getString("status_code"), r.getLong("plant_id"),
                        nullableLong(r, "line_id"), r.getObject("oee_date", LocalDate.class),
                        r.getLong("stop_classification_id"), r.getString("classification_code"),
                        r.getString("classification_name"), r.getString("oee_stop_type_code"),
                        r.getBigDecimal("original_value").longValueExact(),
                        r.getBigDecimal("proposed_value").longValueExact(),
                        ValidationReasonType.valueOf(r.getString("reason_type")), r.getString("justification"),
                        r.getString("cause"), r.getString("action_plan"), r.getString("incident_reference"),
                        r.getLong("proposed_by_user_id"), instant(r, "proposed_at"), instant(r, "expires_at"),
                        approvedBy, instant(r, "approved_at"), List.of()));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read stop-time validation", ex);
        }
    }

    private boolean existsForStopTime(Connection c, long lineId, LocalDate businessDate, long stopClassificationId) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM kpi.kpi_validation v
                      JOIN kpi.kpi_validation_detail d ON d.kpi_validation_id = v.kpi_validation_id
                     WHERE v.line_id = ?
                       AND v.oee_date = ?
                       AND d.stop_classification_id = ?
                )
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId);
            p.setObject(2, businessDate);
            p.setLong(3, stopClassificationId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getBoolean(1); }
        }
    }

    private boolean existsForKpiResult(Connection c, long kpiResultId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT EXISTS (SELECT 1 FROM kpi.kpi_validation_detail WHERE kpi_result_id = ?)")) {
            p.setLong(1, kpiResultId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getBoolean(1); }
        }
    }

    private static void setAuditContext(Connection c, long proposedByUserId, String correlationId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.source_system', 'GBC_APP', true), set_config('app.correlation_id', ?, true)")) {
            p.setString(1, Long.toString(proposedByUserId));
            p.setString(2, correlationId == null ? "" : correlationId);
            p.execute();
        }
    }

    private static OeeValidationResult map(ResultSet r) throws SQLException {
        Long lineId = nullableLong(r, "line_id");
        Long kpiResultId = nullableLong(r, "kpi_result_id");
        Long uomId = nullableLong(r, "uom_id");
        Long approvedBy = nullableLong(r, "approved_by_user_id");
        return new OeeValidationResult(
                r.getLong("kpi_validation_id"), r.getString("status_code"), r.getLong("plant_id"), lineId,
                r.getObject("oee_date", java.time.LocalDate.class), r.getString("kpi_code"), kpiResultId,
                r.getBigDecimal("original_value"), r.getBigDecimal("proposed_value"), uomId,
                ValidationReasonType.valueOf(r.getString("reason_type")), r.getString("justification"),
                r.getString("cause"), r.getString("action_plan"), r.getString("incident_reference"),
                r.getLong("proposed_by_user_id"), instant(r, "proposed_at"), instant(r, "expires_at"),
                approvedBy, instant(r, "approved_at"), List.of());
    }

    private static Long nullableLong(ResultSet r, String name) throws SQLException {
        long value = r.getLong(name); return r.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet r, String name) throws SQLException {
        OffsetDateTime value = r.getObject(name, OffsetDateTime.class); return value == null ? null : value.toInstant();
    }
    private static OffsetDateTime utc(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static void setNullableString(PreparedStatement p, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) p.setNull(index, Types.VARCHAR); else p.setString(index, value);
    }
}
