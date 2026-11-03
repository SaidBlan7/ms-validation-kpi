package com.gbconnected.kpivalidation.adapter.postgres.approval;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalAuthorizationPort;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Canonical authorization: current role and approval capability with explicit module-specific plant assignment. */
public final class JdbcKpiApprovalAuthorizationAdapter implements KpiApprovalAuthorizationPort {
    private final DataSource ds; private final String capability;
    public JdbcKpiApprovalAuthorizationAdapter(DataSource ds,String capability){this.ds=Objects.requireNonNull(ds);this.capability=Objects.requireNonNull(capability);}
    public boolean authorized(ActorContext actor,long userId,long plantId){
        String sql="""
          SELECT EXISTS (
            SELECT 1
              FROM app_security.user_identity u
              JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
              JOIN app_security.role_capability rc ON rc.role_id=r.role_id
              JOIN app_security.capability c ON c.capability_id=rc.capability_id AND c.is_active=TRUE
              JOIN app_security.user_plant_access upa ON upa.user_id=u.user_id
             WHERE u.user_id=? AND u.is_active=TRUE
               AND upa.plant_id=?
                AND EXISTS (SELECT 1 FROM app_security.user_plant_access general_grant
                    WHERE general_grant.user_id=u.user_id AND general_grant.plant_id=upa.plant_id
                    AND general_grant.valid_from<=CURRENT_TIMESTAMP
                    AND (general_grant.valid_to IS NULL OR general_grant.valid_to>CURRENT_TIMESTAMP)
                    AND COALESCE(general_grant.reason,'') NOT LIKE 'KPI_MANUALES:%'
                    AND COALESCE(general_grant.reason,'') NOT LIKE 'KPI_APROBACION:%'
                    AND COALESCE(general_grant.reason,'') NOT LIKE 'KPI_OEE_VALIDATION_SCOPE %') AND LEFT(upa.reason,15)='KPI_APROBACION:'
               AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
               AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
               AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id)
               AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)
               AND c.capability_code=? )
          """;
        try(Connection x=ds.getConnection();PreparedStatement p=x.prepareStatement(sql)){p.setLong(1,userId);p.setLong(2,plantId);p.setString(3,capability);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}
        catch(SQLException e){throw new IllegalStateException("Unable to authorize KPI approver",e);}
    }
    public String source(){return "APP_SECURITY_ROLE_CAPABILITY_PLUS_KPI_APROBACION_PLANT_ASSIGNMENT";}
}
