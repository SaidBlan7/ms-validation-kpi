package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidatorAssignmentAdministrationPort;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;

/** Canonical Spec 02 write path over app_security.user_role_assignment. */
public final class JdbcValidatorAssignmentAdministrationAdapter implements ValidatorAssignmentAdministrationPort {
    public static final int MAX_VALIDATORS_PER_PLANT = 2;

    private final DataSource dataSource;
    private final String validatorRoleCode;
    private final String validatorCapabilityCode;
    private final String adminCapabilityCode;

    public JdbcValidatorAssignmentAdministrationAdapter(DataSource dataSource, String validatorRoleCode,
            String validatorCapabilityCode, String adminCapabilityCode) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.validatorRoleCode = required(validatorRoleCode, "validatorRoleCode");
        this.validatorCapabilityCode = required(validatorCapabilityCode, "validatorCapabilityCode");
        this.adminCapabilityCode = required(adminCapabilityCode, "adminCapabilityCode");
    }

    @Override public boolean available() { return configuredCapability(adminCapabilityCode) && configuredValidatorRole(); }
    @Override public String adminCapabilityCode() { return adminCapabilityCode; }

    @Override
    public boolean canAdminister(UUID tenantId, UUID objectId, long plantId) {
        if (tenantId == null || objectId == null || plantId <= 0) return false;
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM app_security.user_identity u
                      JOIN app_security.user_role_assignment ura ON ura.user_id = u.user_id
                      JOIN app_security.role r ON r.role_id = ura.role_id
                      JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                      JOIN app_security.capability c ON c.capability_id = rc.capability_id
                     WHERE u.entra_tenant_id = ? AND u.entra_object_id = ? AND u.is_active = TRUE
                       AND (ura.plant_id IS NULL OR ura.plant_id = ?)
                       AND ura.valid_from <= CURRENT_TIMESTAMP
                       AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                       AND r.is_active = TRUE
                       AND c.capability_code = ? AND c.is_active = TRUE
                       AND rc.valid_from <= CURRENT_TIMESTAMP
                       AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        return exists(sql, p -> {
            p.setObject(1, tenantId); p.setObject(2, objectId); p.setLong(3, plantId); p.setString(4, adminCapabilityCode);
        });
    }

    @Override
    public List<ValidatorAssignmentAdminView> list(long plantId) {
        String sql = """
                SELECT ura.user_role_assignment_id, ura.user_id, ura.plant_id, u.display_name, u.corporate_email,
                       ura.valid_from, ura.valid_to, ura.assigned_by_user_id, ura.reason,
                       (u.is_active AND r.is_active
                        AND ura.valid_from <= CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                        AND EXISTS (SELECT 1 FROM app_security.user_plant_access upa
                                     WHERE upa.user_id = u.user_id AND upa.plant_id = ura.plant_id
                                       AND upa.valid_from <= CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to > CURRENT_TIMESTAMP))
                        AND EXISTS (SELECT 1 FROM app_security.role_capability rc
                                     JOIN app_security.capability cap ON cap.capability_id = rc.capability_id
                                    WHERE rc.role_id = r.role_id AND cap.capability_code = ?
                                      AND cap.is_active = TRUE AND rc.valid_from <= CURRENT_TIMESTAMP
                                      AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP))) AS effective
                  FROM app_security.user_role_assignment ura
                  JOIN app_security.user_identity u ON u.user_id = ura.user_id
                  JOIN app_security.role r ON r.role_id = ura.role_id
                 WHERE ura.plant_id = ? AND r.role_code = ?
                 ORDER BY ura.valid_from DESC, ura.user_role_assignment_id DESC
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, validatorCapabilityCode); p.setLong(2, plantId); p.setString(3, validatorRoleCode);
            try (ResultSet rs = p.executeQuery()) {
                List<ValidatorAssignmentAdminView> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return List.copyOf(out);
            }
        } catch (SQLException ex) { throw new IllegalStateException("Unable to list KPI validator assignments", ex); }
    }

    @Override
    public ValidatorAssignmentChangeResult assign(long actorUserId, long targetUserId, long plantId,
            String externalApprovalReference, String reason, String correlationId) {
        return write("ASSIGNED", actorUserId, targetUserId, plantId, null, externalApprovalReference, reason,
                correlationId, false, false);
    }

    @Override
    public ValidatorAssignmentChangeResult modify(long actorUserId, long targetUserId, long plantId,
            String externalApprovalReference, String reason, String correlationId) {
        return write("MODIFIED", actorUserId, targetUserId, plantId, null, externalApprovalReference, reason,
                correlationId, true, false);
    }

    @Override
    public ValidatorAssignmentChangeResult revoke(long actorUserId, long targetUserId, long plantId, String reason,
            String correlationId) {
        return write("REVOKED", actorUserId, targetUserId, plantId, Instant.now(), null, reason,
                correlationId, true, true);
    }

    private ValidatorAssignmentChangeResult write(String action, long actorUserId, long targetUserId, long plantId,
            Instant validTo, String externalApprovalReference, String reason, String correlationId,
            boolean existingRequired, boolean revoke) {
        if (actorUserId <= 0 || targetUserId <= 0 || plantId <= 0) throw new IllegalArgumentException("Positive actorUserId, targetUserId and plantId are required");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setAuditContext(c, actorUserId, correlationId);
                advisoryLock(c, plantId);
                long roleId = resolveValidatorRoleId(c);
                ensurePlantActive(c, plantId);
                ensureTargetEligible(c, targetUserId, plantId);
                Long assignmentId = currentAssignmentId(c, targetUserId, plantId, roleId);

                if (!existingRequired && assignmentId != null) {
                    throw new ValidatorAssignmentConflictException("Target user already has an effective KPI OEE validator assignment for the plant");
                }
                if (existingRequired && assignmentId == null) {
                    throw new ValidatorAssignmentConflictException("No effective KPI OEE validator assignment exists for the target user and plant");
                }

                Timestamp effectiveValidTo = validTo == null ? null : Timestamp.from(validTo);
                Instant now = Instant.now();

                long changedAssignmentId;
                if (assignmentId == null) {
                    int count = effectiveValidatorCount(c, plantId);
                    if (count >= MAX_VALIDATORS_PER_PLANT) {
                        throw new ValidatorAssignmentConflictException("Plant already has the maximum of two effective KPI OEE validators");
                    }
                    String storedReason = assignmentReason(externalApprovalReference, reason);
                    try (PreparedStatement p = c.prepareStatement("""
                            INSERT INTO app_security.user_role_assignment
                                (user_id, role_id, plant_id, valid_from, valid_to, assigned_by_user_id, reason)
                            VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)
                            RETURNING user_role_assignment_id
                            """)) {
                        p.setLong(1, targetUserId); p.setLong(2, roleId); p.setLong(3, plantId);
                        if (effectiveValidTo == null) p.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE); else p.setTimestamp(4, effectiveValidTo);
                        p.setLong(5, actorUserId); p.setString(6, storedReason);
                        try (ResultSet rs = p.executeQuery()) { rs.next(); changedAssignmentId = rs.getLong(1); }
                    }
                } else {
                    changedAssignmentId = assignmentId;
                    String storedReason = revoke
                            ? revocationReason(reason)
                            : assignmentReason(externalApprovalReference, reason);
                    try (PreparedStatement p = c.prepareStatement("""
                            UPDATE app_security.user_role_assignment
                               SET valid_to = ?, reason = ?
                             WHERE user_role_assignment_id = ?
                            """)) {
                        if (revoke) p.setTimestamp(1, Timestamp.from(now));
                        else if (effectiveValidTo == null) p.setNull(1, Types.TIMESTAMP_WITH_TIMEZONE);
                        else p.setTimestamp(1, effectiveValidTo);
                        p.setString(2, storedReason); p.setLong(3, changedAssignmentId); p.executeUpdate();
                    }
                }

                int count = effectiveValidatorCount(c, plantId);
                ValidatorAssignmentAdminView row = findById(c, changedAssignmentId);
                c.commit();
                return new ValidatorAssignmentChangeResult(action, row.assignmentId(), row.targetUserId(), row.plantId(),
                        row.validFrom(), row.validTo(), actorUserId, now, count, MAX_VALIDATORS_PER_PLANT);
            } catch (RuntimeException | SQLException ex) {
                try { c.rollback(); } catch (SQLException ignored) { }
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to change KPI validator assignment", ex);
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        } catch (SQLException ex) { throw new IllegalStateException("Unable to change KPI validator assignment", ex); }
    }

    private long resolveValidatorRoleId(Connection c) throws SQLException {
        String sql = """
                SELECT r.role_id
                  FROM app_security.role r
                  JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                  JOIN app_security.capability cap ON cap.capability_id = rc.capability_id
                 WHERE r.role_code = ? AND r.is_active = TRUE
                   AND cap.capability_code = ? AND cap.is_active = TRUE
                   AND rc.valid_from <= CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, validatorRoleCode); p.setString(2, validatorCapabilityCode);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Canonical KPI OEE validator role/capability reference data is not configured");
                return rs.getLong(1);
            }
        }
    }

    private void ensurePlantActive(Connection c, long plantId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT is_active FROM core.plant WHERE plant_id = ?")) {
            p.setLong(1, plantId); try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Plant not found");
                if (!rs.getBoolean(1)) throw new IllegalArgumentException("Plant is inactive");
            }
        }
    }

    private void ensureTargetEligible(Connection c, long userId, long plantId) throws SQLException {
        String sql = """
                SELECT u.is_active,
                       EXISTS (SELECT 1 FROM app_security.user_plant_access upa
                                WHERE upa.user_id = u.user_id AND upa.plant_id = ?
                                  AND upa.valid_from <= CURRENT_TIMESTAMP
                                  AND (upa.valid_to IS NULL OR upa.valid_to > CURRENT_TIMESTAMP)) AS has_plant_access
                  FROM app_security.user_identity u WHERE u.user_id = ?
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId); p.setLong(2, userId);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Target user not found");
                if (!rs.getBoolean("is_active")) throw new IllegalArgumentException("Target user is inactive");
                if (!rs.getBoolean("has_plant_access")) throw new ValidatorAssignmentConflictException("Target user does not have current generic access to the plant");
            }
        }
    }

    private Long currentAssignmentId(Connection c, long userId, long plantId, long roleId) throws SQLException {
        String sql = """
                SELECT user_role_assignment_id FROM app_security.user_role_assignment
                 WHERE user_id = ? AND plant_id = ? AND role_id = ?
                   AND valid_from <= CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP)
                 ORDER BY valid_from DESC, user_role_assignment_id DESC
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId); p.setLong(2, plantId); p.setLong(3, roleId);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                long id = rs.getLong(1);
                if (rs.next()) throw new IllegalStateException("Multiple effective KPI OEE validator assignments exist for the same user and plant");
                return id;
            }
        }
    }

    private int effectiveValidatorCount(Connection c, long plantId) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT u.user_id)
                  FROM app_security.user_identity u
                  JOIN app_security.user_role_assignment ura ON ura.user_id = u.user_id
                  JOIN app_security.user_plant_access upa ON upa.user_id = u.user_id AND upa.plant_id = ura.plant_id
                  JOIN app_security.role r ON r.role_id = ura.role_id
                  JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                  JOIN app_security.capability cap ON cap.capability_id = rc.capability_id
                 WHERE ura.plant_id = ? AND u.is_active = TRUE
                   AND ura.valid_from <= CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                   AND upa.valid_from <= CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to > CURRENT_TIMESTAMP)
                   AND r.role_code = ? AND r.is_active = TRUE
                   AND cap.capability_code = ? AND cap.is_active = TRUE
                   AND rc.valid_from <= CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId); p.setString(2, validatorRoleCode); p.setString(3, validatorCapabilityCode);
            try (ResultSet rs = p.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private ValidatorAssignmentAdminView findById(Connection c, long id) throws SQLException {
        String sql = """
                SELECT ura.user_role_assignment_id, ura.user_id, ura.plant_id, u.display_name, u.corporate_email,
                       ura.valid_from, ura.valid_to, ura.assigned_by_user_id, ura.reason,
                       (u.is_active AND ura.valid_from <= CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                        AND EXISTS (SELECT 1 FROM app_security.user_plant_access upa
                                     WHERE upa.user_id = u.user_id AND upa.plant_id = ura.plant_id
                                       AND upa.valid_from <= CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to > CURRENT_TIMESTAMP))) AS effective
                  FROM app_security.user_role_assignment ura JOIN app_security.user_identity u ON u.user_id = ura.user_id
                 WHERE ura.user_role_assignment_id = ?
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id); try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Assignment disappeared after write");
                return map(rs);
            }
        }
    }

    private static ValidatorAssignmentAdminView map(ResultSet rs) throws SQLException {
        Timestamp from = rs.getTimestamp("valid_from"); Timestamp to = rs.getTimestamp("valid_to");
        long assignedBy = rs.getLong("assigned_by_user_id");
        Long assignedByValue = rs.wasNull() ? null : assignedBy;
        return new ValidatorAssignmentAdminView(rs.getLong("user_role_assignment_id"), rs.getLong("user_id"), rs.getLong("plant_id"),
                rs.getString("display_name"), rs.getString("corporate_email"), from.toInstant(), to == null ? null : to.toInstant(),
                assignedByValue, rs.getString("reason"), rs.getBoolean("effective"));
    }

    private void setAuditContext(Connection c, long actorUserId, String correlationId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.source_system', 'GBC_APP', true), set_config('app.correlation_id', ?, true)")) {
            p.setString(1, Long.toString(actorUserId)); p.setString(2, correlationId == null ? "" : correlationId); p.execute();
        }
    }

    private void advisoryLock(Connection c, long plantId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            p.setString(1, "kpi-oee-validator-assignment:" + plantId); p.execute();
        }
    }

    private boolean configuredCapability(String code) {
        return exists("SELECT EXISTS (SELECT 1 FROM app_security.capability WHERE capability_code = ? AND is_active = TRUE)", p -> p.setString(1, code));
    }

    private boolean configuredValidatorRole() {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM app_security.role r
                  JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                  JOIN app_security.capability c ON c.capability_id = rc.capability_id
                 WHERE r.role_code = ? AND r.is_active = TRUE AND c.capability_code = ? AND c.is_active = TRUE
                   AND rc.valid_from <= CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP))
                """, p -> { p.setString(1, validatorRoleCode); p.setString(2, validatorCapabilityCode); });
    }

    private boolean exists(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            binder.bind(p); try (ResultSet rs = p.executeQuery()) { return rs.next() && rs.getBoolean(1); }
        } catch (SQLException ex) { throw new IllegalStateException("Unable to resolve assignment administration configuration", ex); }
    }

    private static String assignmentReason(String approval, String reason) {
        String a = required(approval, "externalApprovalReference");
        String r = normalize(reason);
        return "KPI_OEE_VALIDATOR | externalApprovalReference=" + a + (r == null ? "" : " | reason=" + r);
    }

    private static String revocationReason(String reason) {
        return "KPI_OEE_VALIDATOR_REVOKED | reason=" + required(reason, "reason");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement p) throws SQLException; }
}
