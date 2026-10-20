package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.port.out.FeatureActivationPort;
import java.sql.*;
import java.util.Objects;
import javax.sql.DataSource;

/** Resolves the plant feature flag from the canonical configuration domain. */
public final class JdbcFeatureActivationAdapter implements FeatureActivationPort {
    private final DataSource dataSource;
    private final String code;

    public JdbcFeatureActivationAdapter(DataSource dataSource, String code) {
        this.dataSource = Objects.requireNonNull(dataSource);
        if (code == null || code.isBlank()) throw new IllegalArgumentException("configuration code is required");
        this.code = code.trim();
    }

    @Override
    public boolean configured() {
        String sql = "SELECT EXISTS (SELECT 1 FROM configuration.configuration_definition WHERE configuration_code=? AND is_active=TRUE)";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, code);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getBoolean(1); }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to resolve KPI validation feature configuration", ex);
        }
    }

    @Override
    public boolean enabledForPlant(long plantId) {
        if (plantId <= 0) return false;
        String sql = """
                SELECT ca.boolean_value
                  FROM configuration.configuration_definition cd
                  JOIN configuration.configuration_assignment ca
                    ON ca.configuration_definition_id = cd.configuration_definition_id
                 WHERE cd.configuration_code = ?
                   AND cd.is_active = TRUE
                   AND ca.boolean_value IS NOT NULL
                   AND ca.valid_from <= CURRENT_TIMESTAMP
                   AND (ca.valid_to IS NULL OR ca.valid_to > CURRENT_TIMESTAMP)
                   AND (
                        ca.plant_id = ?
                        OR (
                            ca.organization_id IS NULL AND ca.plant_id IS NULL AND ca.line_id IS NULL
                            AND ca.product_id IS NULL AND ca.dough_id IS NULL AND ca.machine_id IS NULL
                        )
                   )
                 ORDER BY CASE WHEN ca.plant_id = ? THEN 0 ELSE 1 END, ca.valid_from DESC,
                          ca.configuration_assignment_id DESC
                 LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, code);
            p.setLong(2, plantId);
            p.setLong(3, plantId);
            try (ResultSet r = p.executeQuery()) { return r.next() && r.getBoolean(1); }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to resolve plant KPI validation feature flag", ex);
        }
    }

    @Override public String source() { return "configuration.configuration_definition+configuration_assignment"; }
    @Override public String configurationCode() { return code; }
}
