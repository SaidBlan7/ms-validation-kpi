package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.port.out.ValidatorAssignmentPort;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Canonical v1.2 KPI validator authorization.
 *
 * A validator is assigned to a plant only when the user identity is active and
 * has a current, plant-scoped user_role_assignment for the configured role,
 * whose role currently owns the configured capability. Generic plant access is
 * deliberately checked separately by AccessScopePort.
 */
public final class JdbcValidatorAssignmentAdapter implements ValidatorAssignmentPort {
    private final DataSource dataSource;
    private final String roleCode;
    private final String capabilityCode;

    public JdbcValidatorAssignmentAdapter(DataSource dataSource, String roleCode, String capabilityCode) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.roleCode = normalizeRequired(roleCode, "roleCode");
        this.capabilityCode = normalizeRequired(capabilityCode, "capabilityCode");
    }

    @Override
    public boolean configured() {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM app_security.role r
                      JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                      JOIN app_security.capability c ON c.capability_id = rc.capability_id
                     WHERE r.role_code = ?
                       AND r.is_active = TRUE
                       AND c.capability_code = ?
                       AND c.is_active = TRUE
                       AND rc.valid_from <= CURRENT_TIMESTAMP
                       AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        return exists(sql, p -> {
            p.setString(1, roleCode);
            p.setString(2, capabilityCode);
        }, "Unable to resolve KPI validator role/capability configuration");
    }

    @Override
    public boolean isAssigned(UUID tenantId, UUID objectId, long plantId) {
        if (tenantId == null || objectId == null || plantId <= 0) return false;
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM app_security.user_identity u
                      JOIN app_security.user_role_assignment ura ON ura.user_id = u.user_id
                      JOIN app_security.role r ON r.role_id = ura.role_id
                      JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                      JOIN app_security.capability c ON c.capability_id = rc.capability_id
                     WHERE u.entra_tenant_id = ?
                       AND u.entra_object_id = ?
                       AND u.is_active = TRUE
                       AND ura.plant_id = ?
                       AND ura.valid_from <= CURRENT_TIMESTAMP
                       AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                       AND r.role_code = ?
                       AND r.is_active = TRUE
                       AND c.capability_code = ?
                       AND c.is_active = TRUE
                       AND rc.valid_from <= CURRENT_TIMESTAMP
                       AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        return exists(sql, p -> {
            p.setObject(1, tenantId);
            p.setObject(2, objectId);
            p.setLong(3, plantId);
            p.setString(4, roleCode);
            p.setString(5, capabilityCode);
        }, "Unable to validate KPI validator assignment");
    }

    @Override
    public Set<Long> assignedPlants(UUID tenantId, UUID objectId) {
        if (tenantId == null || objectId == null) return Set.of();
        String sql = """
                SELECT DISTINCT ura.plant_id
                  FROM app_security.user_identity u
                  JOIN app_security.user_role_assignment ura ON ura.user_id = u.user_id
                  JOIN app_security.role r ON r.role_id = ura.role_id
                  JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                  JOIN app_security.capability c ON c.capability_id = rc.capability_id
                 WHERE u.entra_tenant_id = ?
                   AND u.entra_object_id = ?
                   AND u.is_active = TRUE
                   AND ura.plant_id IS NOT NULL
                   AND ura.valid_from <= CURRENT_TIMESTAMP
                   AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                   AND r.role_code = ?
                   AND r.is_active = TRUE
                   AND c.capability_code = ?
                   AND c.is_active = TRUE
                   AND rc.valid_from <= CURRENT_TIMESTAMP
                   AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                 ORDER BY ura.plant_id
                """;
        TreeSet<Long> out = new TreeSet<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement p = connection.prepareStatement(sql)) {
            p.setObject(1, tenantId);
            p.setObject(2, objectId);
            p.setString(3, roleCode);
            p.setString(4, capabilityCode);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(r.getLong(1));
            }
            return Collections.unmodifiableSet(out);
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to resolve KPI validator plants", ex);
        }
    }

    @Override
    public int assignedValidatorCount(long plantId) {
        if (plantId <= 0) return 0;
        String sql = """
                SELECT COUNT(DISTINCT u.user_id)
                  FROM app_security.user_identity u
                  JOIN app_security.user_role_assignment ura ON ura.user_id = u.user_id
                  JOIN app_security.user_plant_access upa
                    ON upa.user_id = u.user_id
                   AND upa.plant_id = ura.plant_id
                   AND upa.valid_from <= CURRENT_TIMESTAMP
                   AND (upa.valid_to IS NULL OR upa.valid_to > CURRENT_TIMESTAMP)
                  JOIN app_security.role r ON r.role_id = ura.role_id
                  JOIN app_security.role_capability rc ON rc.role_id = r.role_id
                  JOIN app_security.capability c ON c.capability_id = rc.capability_id
                 WHERE u.is_active = TRUE
                   AND ura.plant_id = ?
                   AND ura.valid_from <= CURRENT_TIMESTAMP
                   AND (ura.valid_to IS NULL OR ura.valid_to > CURRENT_TIMESTAMP)
                   AND r.role_code = ?
                   AND r.is_active = TRUE
                   AND c.capability_code = ?
                   AND c.is_active = TRUE
                   AND rc.valid_from <= CURRENT_TIMESTAMP
                   AND (rc.valid_to IS NULL OR rc.valid_to > CURRENT_TIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement p = connection.prepareStatement(sql)) {
            p.setLong(1, plantId);
            p.setString(2, roleCode);
            p.setString(3, capabilityCode);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to count KPI validators for plant", ex);
        }
    }

    @Override public String source() { return "app_security.user_role_assignment+role_capability+user_plant_access(v1.2)"; }
    @Override public boolean enforcesRoleCapability() { return true; }
    @Override public String roleCode() { return roleCode; }
    @Override public String capabilityCode() { return capabilityCode; }

    private boolean exists(String sql, SqlBinder binder, String errorMessage) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement p = connection.prepareStatement(sql)) {
            binder.bind(p);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getBoolean(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private static String normalizeRequired(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    @FunctionalInterface
    private interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
}
