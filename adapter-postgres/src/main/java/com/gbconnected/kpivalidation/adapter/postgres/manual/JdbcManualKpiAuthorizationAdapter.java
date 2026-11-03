package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiAuthorizationPort;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Canonical authorization: one current role + capability, scoped to plants by user_plant_access. */
public final class JdbcManualKpiAuthorizationAdapter implements ManualKpiAuthorizationPort {
    private final DataSource ds; private final String capability;
    public JdbcManualKpiAuthorizationAdapter(DataSource ds,String capability){this.ds=Objects.requireNonNull(ds);this.capability=Objects.requireNonNull(capability);}
    public boolean configured(){
        String q="SELECT EXISTS(SELECT 1 FROM app_security.capability WHERE capability_code=? AND is_active=TRUE)";
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){p.setString(1,capability);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to resolve Manual KPI capability",e);}
    }
    public boolean authorized(UUID tenant,UUID object,long plant){
        String q="""
            SELECT EXISTS(
              SELECT 1
              FROM app_security.user_identity u
              JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
              JOIN app_security.role_capability rc ON rc.role_id=r.role_id
              JOIN app_security.capability c ON c.capability_id=rc.capability_id AND c.is_active=TRUE
              JOIN app_security.user_plant_access upa ON upa.user_id=u.user_id AND upa.reason LIKE 'KPI_MANUALES:%'
              WHERE u.entra_tenant_id=? AND u.entra_object_id=? AND u.is_active=TRUE
                AND upa.plant_id=?
                AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
                AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
                AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id)
                AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)
                AND c.capability_code=?
            )
            """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){p.setObject(1,tenant);p.setObject(2,object);p.setLong(3,plant);p.setString(4,capability);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to authorize Manual KPI actor",e);}
    }
    public String capabilityCode(){return capability;}
    public String source(){return "APP_SECURITY_CURRENT_ROLE_CAPABILITY_PLUS_MODULE_TAGGED_USER_PLANT_ACCESS";}
}
