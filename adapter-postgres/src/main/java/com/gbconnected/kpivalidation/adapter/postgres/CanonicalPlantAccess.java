package com.gbconnected.kpivalidation.adapter.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Distinguishes organization-wide grants from KPI module permission markers in the canonical shared table. */
public final class CanonicalPlantAccess {
    private CanonicalPlantAccess() {}
    public static final String GENERAL_REASON_FILTER = """
        COALESCE(reason,'') NOT LIKE 'KPI_MANUALES:%'
        AND COALESCE(reason,'') NOT LIKE 'KPI_APROBACION:%'
        AND COALESCE(reason,'') NOT LIKE 'KPI_OEE_VALIDATION_SCOPE %'
        """;

    /** Require a separately provisioned organizational grant; a KPI grant is never an organizational grant. */
    public static void requireGeneralGrant(Connection c, long userId, long plantId) throws SQLException {
        String sql = """
            SELECT a.user_plant_access_id FROM app_security.user_plant_access a
            JOIN app_security.user_identity u ON u.user_id=a.user_id AND u.is_active=TRUE
            WHERE a.user_id=? AND a.plant_id=?
              AND a.valid_from<=clock_timestamp()
              AND (a.valid_to IS NULL OR a.valid_to>clock_timestamp())
              AND COALESCE(a.reason,'') NOT LIKE 'KPI_MANUALES:%'
              AND COALESCE(a.reason,'') NOT LIKE 'KPI_APROBACION:%'
              AND COALESCE(a.reason,'') NOT LIKE 'KPI_OEE_VALIDATION_SCOPE %'
            FOR SHARE OF a,u
            """;
        try (PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setLong(1,userId);ps.setLong(2,plantId);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) throw new com.gbconnected.kpivalidation.application.AuthorizationException(
                    "A current general plant assignment is required independently of the KPI module grant");
            }
        }
    }
}
