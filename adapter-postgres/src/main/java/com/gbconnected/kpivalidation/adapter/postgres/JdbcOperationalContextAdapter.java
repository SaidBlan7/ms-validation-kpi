package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.PlantContext;
import com.gbconnected.kpivalidation.application.port.out.AccessScopePort;
import com.gbconnected.kpivalidation.application.port.out.DatabaseHealthPort;
import com.gbconnected.kpivalidation.application.port.out.OperationalContextPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Operational/security reads aligned with canonical DDL v1.2. */
public final class JdbcOperationalContextAdapter implements AccessScopePort, OperationalContextPort, DatabaseHealthPort {
    private final DataSource dataSource;

    public JdbcOperationalContextAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public boolean hasPlantAccess(UUID tenantId, UUID objectId, long plantId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM app_security.user_identity u
                      JOIN app_security.user_plant_access a ON a.user_id = u.user_id
                     WHERE u.entra_tenant_id = ?
                       AND u.entra_object_id = ?
                       AND u.is_active = TRUE
                       AND a.plant_id = ?
                       AND a.valid_from <= CURRENT_TIMESTAMP
                       AND (a.valid_to IS NULL OR a.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId);
            p.setObject(2, objectId);
            p.setLong(3, plantId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to validate plant access", e);
        }
    }

    @Override
    public boolean hasLineAccess(UUID tenantId, UUID objectId, long lineId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM app_security.user_identity u
                      JOIN app_security.user_line_access a ON a.user_id = u.user_id
                     WHERE u.entra_tenant_id = ?
                       AND u.entra_object_id = ?
                       AND u.is_active = TRUE
                       AND a.line_id = ?
                       AND a.valid_from <= CURRENT_TIMESTAMP
                       AND (a.valid_to IS NULL OR a.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, objectId); p.setLong(3, lineId);
            try (ResultSet r = p.executeQuery()) { return r.next() && r.getBoolean(1); }
        } catch (SQLException e) { throw new IllegalStateException("Unable to validate line access", e); }
    }

    @Override
    public Optional<PlantContext> findPlant(long plantId) {
        String sql = "SELECT plant_id, timezone_name, is_active FROM core.plant WHERE plant_id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId);
            try (ResultSet r = p.executeQuery()) {
                return r.next()
                        ? Optional.of(new PlantContext(r.getLong("plant_id"), r.getString("timezone_name"), r.getBoolean("is_active")))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to resolve plant", e);
        }
    }

    @Override
    public boolean lineBelongsToPlant(long lineId, long plantId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM core.line l
                      JOIN core.line_plant_assignment a ON a.line_id = l.line_id
                     WHERE l.line_id = ?
                       AND l.is_active = TRUE
                       AND a.plant_id = ?
                       AND a.valid_from <= CURRENT_TIMESTAMP
                       AND (a.valid_to IS NULL OR a.valid_to > CURRENT_TIMESTAMP)
                )
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId);
            p.setLong(2, plantId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to validate line/plant scope", e);
        }
    }

    @Override
    public boolean healthy() {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1"); ResultSet r = p.executeQuery()) {
            return r.next();
        } catch (SQLException e) {
            return false;
        }
    }
}
