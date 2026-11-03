package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.manual.scope.ManualKpiScopeConflictException;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiScopeAdministrationPort;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantAssignment;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Canonical plant-scope administration for KPI Manuales.
 * The user's single current role carries the module capability; plant scope is
 * the module-tagged temporal history in app_security.user_plant_access.
 */
public final class JdbcManualKpiScopeAdministrationAdapter implements ManualKpiScopeAdministrationPort {
    private final DataSource ds; private final String roleCode; private final String entryCapability; private final String adminCapability;
    public JdbcManualKpiScopeAdministrationAdapter(DataSource ds,String roleCode,String entryCapability,String adminCapability){
        this.ds=Objects.requireNonNull(ds);this.roleCode=required(roleCode,"roleCode");this.entryCapability=required(entryCapability,"entryCapability");this.adminCapability=required(adminCapability,"adminCapability");
    }
    @Override public boolean available(){return configuredRole()&&configuredCapability(adminCapability);}
    @Override public String adminCapabilityCode(){return adminCapability;}
    @Override public boolean canAdminister(UUID tenant,UUID object,long plant){
        if(tenant==null||object==null||plant<=0)return false;
        String q="""
            SELECT EXISTS(
              SELECT 1 FROM app_security.user_identity u
              JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
              JOIN app_security.role_capability rc ON rc.role_id=r.role_id
              JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE
             WHERE u.entra_tenant_id=? AND u.entra_object_id=? AND u.is_active=TRUE AND cap.capability_code=?
               AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
               AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)
               AND (ura.plant_id IS NULL OR ura.plant_id=?))
            """;
        return exists(q,p->{p.setObject(1,tenant);p.setObject(2,object);p.setString(3,adminCapability);p.setLong(4,plant);});
    }
    @Override public List<ManualKpiPlantAssignment> list(long plantId){
        String q="""
            SELECT upa.user_plant_access_id AS assignment_id,upa.user_id,upa.plant_id,upa.valid_from,upa.valid_to,
                   upa.granted_by_user_id,upa.reason,
                   (u.is_active
                    AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
                    AND EXISTS(SELECT 1 FROM app_security.user_role_assignment ura
                               JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
                               JOIN app_security.role_capability rc ON rc.role_id=r.role_id
                               JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE
                              WHERE ura.user_id=upa.user_id AND r.role_code=? AND cap.capability_code=?
                                AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
                                AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id)
                                AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))) AS effective
              FROM app_security.user_plant_access upa
              JOIN app_security.user_identity u ON u.user_id=upa.user_id
             WHERE upa.plant_id=? AND upa.reason LIKE 'KPI_MANUALES:%'
               AND EXISTS(SELECT 1 FROM app_security.user_role_assignment ura
                          JOIN app_security.role r ON r.role_id=ura.role_id
                         WHERE ura.user_id=upa.user_id AND r.role_code=?)
             ORDER BY upa.valid_from DESC,upa.user_plant_access_id DESC
            """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){
            p.setString(1,roleCode);p.setString(2,entryCapability);p.setLong(3,plantId);p.setString(4,roleCode);
            try(ResultSet r=p.executeQuery()){List<ManualKpiPlantAssignment> out=new ArrayList<>();while(r.next())out.add(map(r));return List.copyOf(out);}
        }catch(SQLException e){throw new IllegalStateException("Unable to list Manual KPI plant assignments",e);}
    }
    @Override public ManualKpiPlantAssignment assign(long actor,long user,long plant,String reason,String correlation){
        validateIds(actor,user,plant);
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);try{
                setAudit(c,actor,correlation);lock(c,user,plant);ensurePlantActive(c,plant);ensureTargetRole(c,user,plant);com.gbconnected.kpivalidation.adapter.postgres.CanonicalPlantAccess.requireGeneralGrant(c,user,plant);
                if(currentId(c,user,plant)!=null)throw new ManualKpiScopeConflictException("Target user already has a current KPI Manuales plant assignment");
                long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO app_security.user_plant_access(user_id,plant_id,valid_from,granted_by_user_id,reason) VALUES(?,?,CURRENT_TIMESTAMP,?,?) RETURNING user_plant_access_id")){
                    p.setLong(1,user);p.setLong(2,plant);p.setLong(3,actor);p.setString(4,moduleReason(reason));try(ResultSet r=p.executeQuery()){r.next();id=r.getLong(1);}
                }
                ManualKpiPlantAssignment out=find(c,id);c.commit();return out;
            }catch(RuntimeException|SQLException e){try{c.rollback();}catch(SQLException ignored){}if(e instanceof RuntimeException re)throw re;throw new IllegalStateException("Unable to assign Manual KPI plant scope",e);}
        }catch(SQLException e){throw new IllegalStateException("Unable to assign Manual KPI plant scope",e);}
    }
    @Override public ManualKpiPlantAssignment revoke(long actor,long user,long plant,String reason,String correlation){
        validateIds(actor,user,plant);required(reason,"reason");
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);try{
                setAudit(c,actor,correlation);lock(c,user,plant);Long id=currentId(c,user,plant);if(id==null)throw new ManualKpiScopeConflictException("No current KPI Manuales plant assignment exists for target user and plant");
                try(PreparedStatement p=c.prepareStatement("UPDATE app_security.user_plant_access SET valid_to=CURRENT_TIMESTAMP,reason=? WHERE user_plant_access_id=?")){p.setString(1,moduleReason(required(reason,"reason")));p.setLong(2,id);p.executeUpdate();}
                ManualKpiPlantAssignment out=find(c,id);c.commit();return out;
            }catch(RuntimeException|SQLException e){try{c.rollback();}catch(SQLException ignored){}if(e instanceof RuntimeException re)throw re;throw new IllegalStateException("Unable to revoke Manual KPI plant scope",e);}
        }catch(SQLException e){throw new IllegalStateException("Unable to revoke Manual KPI plant scope",e);}
    }
    private void ensureTargetRole(Connection c,long user,long plant)throws SQLException{
        String q="""
            SELECT EXISTS(SELECT 1 FROM app_security.user_identity u
              JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
              JOIN app_security.role_capability rc ON rc.role_id=r.role_id
              JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE
             WHERE u.user_id=? AND u.is_active=TRUE AND r.role_code=? AND cap.capability_code=?
               AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
               AND (ura.plant_id IS NULL OR ura.plant_id=?)
               AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))
            """;
        try(PreparedStatement p=c.prepareStatement(q)){p.setLong(1,user);p.setString(2,roleCode);p.setString(3,entryCapability);p.setLong(4,plant);try(ResultSet r=p.executeQuery()){r.next();if(!r.getBoolean(1))throw new ManualKpiScopeConflictException("Target user must have the current canonical role "+roleCode+" with capability "+entryCapability+" before plant scope can be granted");}}
    }
    private void ensurePlantActive(Connection c,long plant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT is_active FROM core.plant WHERE plant_id=?")){p.setLong(1,plant);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Plant not found");if(!r.getBoolean(1))throw new IllegalArgumentException("Plant is inactive");}}}
    private Long currentId(Connection c,long user,long plant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT user_plant_access_id FROM app_security.user_plant_access WHERE user_id=? AND plant_id=? AND reason LIKE 'KPI_MANUALES:%' AND valid_from<=CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP) ORDER BY valid_from DESC,user_plant_access_id DESC")){p.setLong(1,user);p.setLong(2,plant);try(ResultSet r=p.executeQuery()){if(!r.next())return null;long id=r.getLong(1);if(r.next())throw new IllegalStateException("Multiple current user_plant_access rows exist for the same user and plant");return id;}}}
    private ManualKpiPlantAssignment find(Connection c,long id)throws SQLException{
        String q="""
            SELECT upa.user_plant_access_id assignment_id,upa.user_id,upa.plant_id,upa.valid_from,upa.valid_to,upa.granted_by_user_id,upa.reason,
                   (u.is_active AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
                    AND EXISTS(SELECT 1 FROM app_security.user_role_assignment ura
                               JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
                               JOIN app_security.role_capability rc ON rc.role_id=r.role_id
                               JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE
                              WHERE ura.user_id=upa.user_id AND r.role_code=? AND cap.capability_code=?
                                AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
                                AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id)
                                AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))) effective
              FROM app_security.user_plant_access upa JOIN app_security.user_identity u ON u.user_id=upa.user_id
             WHERE upa.user_plant_access_id=? AND upa.reason LIKE 'KPI_MANUALES:%'
            """;
        try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,roleCode);p.setString(2,entryCapability);p.setLong(3,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Manual KPI plant assignment disappeared after write");return map(r);}}
    }
    private static ManualKpiPlantAssignment map(ResultSet r)throws SQLException{Timestamp f=r.getTimestamp("valid_from"),t=r.getTimestamp("valid_to");long g=r.getLong("granted_by_user_id");Long gv=r.wasNull()?null:g;return new ManualKpiPlantAssignment(r.getLong("assignment_id"),r.getLong("user_id"),r.getLong("plant_id"),f.toInstant(),t==null?null:t.toInstant(),gv,r.getString("reason"),r.getBoolean("effective"));}
    private boolean configuredRole(){return exists("SELECT EXISTS(SELECT 1 FROM app_security.role r JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability c ON c.capability_id=rc.capability_id WHERE r.role_code=? AND r.is_active=TRUE AND c.capability_code=? AND c.is_active=TRUE AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))",p->{p.setString(1,roleCode);p.setString(2,entryCapability);});}
    private boolean configuredCapability(String code){return exists("SELECT EXISTS(SELECT 1 FROM app_security.capability WHERE capability_code=? AND is_active=TRUE)",p->p.setString(1,code));}
    private void setAudit(Connection c,long actor,String corr)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.user_id', ?, true),set_config('app.source_system','GBC_APP',true),set_config('app.correlation_id', ?, true)")){p.setString(1,Long.toString(actor));p.setString(2,corr==null?"":corr);p.execute();}}
    private void lock(Connection c,long u,long p)throws SQLException{try(PreparedStatement x=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){x.setString(1,"manual-kpi-scope:"+u+":"+p);x.execute();}}
    private boolean exists(String q,Binder b){try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){b.bind(p);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to resolve Manual KPI scope administration configuration",e);}}
    private static void validateIds(long a,long u,long p){if(a<=0||u<=0||p<=0)throw new IllegalArgumentException("Positive actorUserId, targetUserId and plantId are required");}
    private static String moduleReason(String value){return "KPI_MANUALES:"+(normalize(value)==null?"":normalize(value));}
    private static String normalize(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
    @FunctionalInterface private interface Binder{void bind(PreparedStatement p)throws SQLException;}
}
