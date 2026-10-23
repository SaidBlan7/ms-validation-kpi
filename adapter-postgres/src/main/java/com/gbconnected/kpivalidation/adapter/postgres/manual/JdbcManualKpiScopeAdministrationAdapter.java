package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.manual.scope.ManualKpiScopeConflictException;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiScopeAdministrationPort;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantAssignment;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Module-specific plant assignment through a plant-scoped KPI Manuales role. Generic user_plant_access remains a prerequisite. */
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
               AND (ura.plant_id IS NULL OR ura.plant_id=?)
            )
            """;
        return exists(q,p->{p.setObject(1,tenant);p.setObject(2,object);p.setString(3,adminCapability);p.setLong(4,plant);});
    }
    @Override public List<ManualKpiPlantAssignment> list(long plantId){
        String q="""
            SELECT ura.user_role_assignment_id AS assignment_id,ura.user_id,ura.plant_id,ura.valid_from,ura.valid_to,
                   ura.assigned_by_user_id AS granted_by_user_id,ura.reason,
                   (u.is_active AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
                    AND EXISTS(SELECT 1 FROM app_security.user_plant_access upa WHERE upa.user_id=ura.user_id AND upa.plant_id=ura.plant_id
                               AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP))) AS effective
              FROM app_security.user_role_assignment ura
              JOIN app_security.user_identity u ON u.user_id=ura.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id
             WHERE ura.plant_id=? AND r.role_code=?
             ORDER BY ura.valid_from DESC,ura.user_role_assignment_id DESC
            """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){p.setLong(1,plantId);p.setString(2,roleCode);try(ResultSet r=p.executeQuery()){List<ManualKpiPlantAssignment> out=new ArrayList<>();while(r.next())out.add(map(r));return List.copyOf(out);}}
        catch(SQLException e){throw new IllegalStateException("Unable to list Manual KPI plant assignments",e);}
    }
    @Override public ManualKpiPlantAssignment assign(long actor,long user,long plant,String reason,String correlation){
        validateIds(actor,user,plant);
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);try{
                setAudit(c,actor,correlation);lock(c,user,plant);long roleId=resolveRoleId(c);ensureTargetEligible(c,user,plant);
                if(currentId(c,user,plant,roleId)!=null)throw new ManualKpiScopeConflictException("Target user already has an effective KPI Manuales assignment for this plant");
                long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO app_security.user_role_assignment(user_id,role_id,plant_id,valid_from,assigned_by_user_id,reason) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?) RETURNING user_role_assignment_id")){
                    p.setLong(1,user);p.setLong(2,roleId);p.setLong(3,plant);p.setLong(4,actor);p.setString(5,reason);try(ResultSet r=p.executeQuery()){r.next();id=r.getLong(1);}
                }
                ManualKpiPlantAssignment out=find(c,id);c.commit();return out;
            }catch(RuntimeException|SQLException e){try{c.rollback();}catch(SQLException ignored){}if(e instanceof RuntimeException re)throw re;throw new IllegalStateException("Unable to assign Manual KPI plant scope",e);}
        }catch(SQLException e){throw new IllegalStateException("Unable to assign Manual KPI plant scope",e);}
    }
    @Override public ManualKpiPlantAssignment revoke(long actor,long user,long plant,String reason,String correlation){
        validateIds(actor,user,plant);required(reason,"reason");
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);try{
                setAudit(c,actor,correlation);lock(c,user,plant);long roleId=resolveRoleId(c);Long id=currentId(c,user,plant,roleId);if(id==null)throw new ManualKpiScopeConflictException("No effective KPI Manuales assignment exists for target user and plant");
                try(PreparedStatement p=c.prepareStatement("UPDATE app_security.user_role_assignment SET valid_to=CURRENT_TIMESTAMP, reason=? WHERE user_role_assignment_id=?")){p.setString(1,reason);p.setLong(2,id);p.executeUpdate();}
                ManualKpiPlantAssignment out=find(c,id);c.commit();return out;
            }catch(RuntimeException|SQLException e){try{c.rollback();}catch(SQLException ignored){}if(e instanceof RuntimeException re)throw re;throw new IllegalStateException("Unable to revoke Manual KPI plant scope",e);}
        }catch(SQLException e){throw new IllegalStateException("Unable to revoke Manual KPI plant scope",e);}
    }
    private long resolveRoleId(Connection c)throws SQLException{
        String q="SELECT r.role_id FROM app_security.role r JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability cap ON cap.capability_id=rc.capability_id WHERE r.role_code=? AND r.is_active=TRUE AND cap.capability_code=? AND cap.is_active=TRUE AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)";
        try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,roleCode);p.setString(2,entryCapability);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("KPI Manuales role/capability reference data is not configured");long id=r.getLong(1);if(r.next())throw new IllegalStateException("KPI Manuales role code is not unique");return id;}}
    }
    private void ensureTargetEligible(Connection c,long user,long plant)throws SQLException{
        String q="SELECT u.is_active,EXISTS(SELECT 1 FROM app_security.user_plant_access upa WHERE upa.user_id=u.user_id AND upa.plant_id=? AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)) AS plant_access FROM app_security.user_identity u WHERE u.user_id=?";
        try(PreparedStatement p=c.prepareStatement(q)){p.setLong(1,plant);p.setLong(2,user);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Target user not found");if(!r.getBoolean("is_active"))throw new IllegalArgumentException("Target user is inactive");if(!r.getBoolean("plant_access"))throw new ManualKpiScopeConflictException("Target user requires current generic access to the plant before KPI Manuales can be assigned");}}
    }
    private Long currentId(Connection c,long user,long plant,long role)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT user_role_assignment_id FROM app_security.user_role_assignment WHERE user_id=? AND plant_id=? AND role_id=? AND valid_from<=CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP) ORDER BY valid_from DESC,user_role_assignment_id DESC")){p.setLong(1,user);p.setLong(2,plant);p.setLong(3,role);try(ResultSet r=p.executeQuery()){if(!r.next())return null;long id=r.getLong(1);if(r.next())throw new IllegalStateException("Multiple effective KPI Manuales assignments exist for the same user and plant");return id;}}}
    private ManualKpiPlantAssignment find(Connection c,long id)throws SQLException{String q="SELECT ura.user_role_assignment_id AS assignment_id,ura.user_id,ura.plant_id,ura.valid_from,ura.valid_to,ura.assigned_by_user_id AS granted_by_user_id,ura.reason,(u.is_active AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP) AND EXISTS(SELECT 1 FROM app_security.user_plant_access upa WHERE upa.user_id=ura.user_id AND upa.plant_id=ura.plant_id AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP))) AS effective FROM app_security.user_role_assignment ura JOIN app_security.user_identity u ON u.user_id=ura.user_id WHERE ura.user_role_assignment_id=?";try(PreparedStatement p=c.prepareStatement(q)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("KPI Manuales assignment disappeared after write");return map(r);}}}
    private static ManualKpiPlantAssignment map(ResultSet r)throws SQLException{Timestamp f=r.getTimestamp("valid_from"),t=r.getTimestamp("valid_to");long g=r.getLong("granted_by_user_id");Long gv=r.wasNull()?null:g;return new ManualKpiPlantAssignment(r.getLong("assignment_id"),r.getLong("user_id"),r.getLong("plant_id"),f.toInstant(),t==null?null:t.toInstant(),gv,r.getString("reason"),r.getBoolean("effective"));}
    private boolean configuredRole(){return exists("SELECT EXISTS(SELECT 1 FROM app_security.role r JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability c ON c.capability_id=rc.capability_id WHERE r.role_code=? AND r.is_active=TRUE AND c.capability_code=? AND c.is_active=TRUE AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))",p->{p.setString(1,roleCode);p.setString(2,entryCapability);});}
    private boolean configuredCapability(String code){return exists("SELECT EXISTS(SELECT 1 FROM app_security.capability WHERE capability_code=? AND is_active=TRUE)",p->p.setString(1,code));}
    private void setAudit(Connection c,long actor,String corr)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.user_id', ?, true),set_config('app.source_system','GBC_APP',true),set_config('app.correlation_id', ?, true)")){p.setString(1,Long.toString(actor));p.setString(2,corr==null?"":corr);p.execute();}}
    private void lock(Connection c,long u,long p)throws SQLException{try(PreparedStatement x=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){x.setString(1,"manual-kpi-scope:"+u+":"+p);x.execute();}}
    private boolean exists(String q,Binder b){try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){b.bind(p);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to resolve Manual KPI scope administration configuration",e);}}
    private static void validateIds(long a,long u,long p){if(a<=0||u<=0||p<=0)throw new IllegalArgumentException("Positive actorUserId, targetUserId and plantId are required");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
    @FunctionalInterface private interface Binder{void bind(PreparedStatement p)throws SQLException;}
}
