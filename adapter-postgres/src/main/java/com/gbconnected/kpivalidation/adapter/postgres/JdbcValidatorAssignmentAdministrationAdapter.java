package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidatorAssignmentAdministrationPort;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;

/** Canonical Spec 02 write path: current role/capability + temporal user_plant_access. */
public final class JdbcValidatorAssignmentAdministrationAdapter implements ValidatorAssignmentAdministrationPort {
    public static final int MAX_VALIDATORS_PER_PLANT = 2;
    private final DataSource dataSource; private final String validatorRoleCode; private final String validatorCapabilityCode; private final String adminCapabilityCode;
    public JdbcValidatorAssignmentAdministrationAdapter(DataSource dataSource,String validatorRoleCode,String validatorCapabilityCode,String adminCapabilityCode){this.dataSource=Objects.requireNonNull(dataSource);this.validatorRoleCode=required(validatorRoleCode,"validatorRoleCode");this.validatorCapabilityCode=required(validatorCapabilityCode,"validatorCapabilityCode");this.adminCapabilityCode=required(adminCapabilityCode,"adminCapabilityCode");}
    @Override public boolean available(){return configuredCapability(adminCapabilityCode)&&configuredValidatorRole();}
    @Override public String adminCapabilityCode(){return adminCapabilityCode;}
    @Override public boolean canAdminister(UUID tenantId,UUID objectId,long plantId){
        if(tenantId==null||objectId==null||plantId<=0)return false;
        String sql="""
            SELECT EXISTS(SELECT 1 FROM app_security.user_identity u
              JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
              JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE
              JOIN app_security.role_capability rc ON rc.role_id=r.role_id
              JOIN app_security.capability c ON c.capability_id=rc.capability_id AND c.is_active=TRUE
             WHERE u.entra_tenant_id=? AND u.entra_object_id=? AND u.is_active=TRUE
               AND c.capability_code=?
               AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
               AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)
               AND (ura.plant_id IS NULL OR ura.plant_id=?))
            """;
        return exists(sql,p->{p.setObject(1,tenantId);p.setObject(2,objectId);p.setString(3,adminCapabilityCode);p.setLong(4,plantId);});
    }
    @Override public List<ValidatorAssignmentAdminView> list(long plantId){
        String sql="""
            SELECT upa.user_plant_access_id AS assignment_id,upa.user_id,upa.plant_id,u.display_name,u.corporate_email,
                   upa.valid_from,upa.valid_to,upa.granted_by_user_id AS assigned_by_user_id,upa.reason,
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
             WHERE upa.plant_id=? AND starts_with(upa.reason, 'KPI_OEE_VALIDATION_SCOPE | externalApprovalReference=')
               AND EXISTS(SELECT 1 FROM app_security.user_role_assignment ura JOIN app_security.role r ON r.role_id=ura.role_id WHERE ura.user_id=upa.user_id AND r.role_code=?)
             ORDER BY upa.valid_from DESC,upa.user_plant_access_id DESC
            """;
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,validatorRoleCode);p.setString(2,validatorCapabilityCode);p.setLong(3,plantId);p.setString(4,validatorRoleCode);try(ResultSet rs=p.executeQuery()){List<ValidatorAssignmentAdminView> out=new ArrayList<>();while(rs.next())out.add(map(rs));return List.copyOf(out);}}catch(SQLException ex){throw new IllegalStateException("Unable to list KPI validator assignments",ex);}
    }
    @Override public ValidatorAssignmentChangeResult assign(long actor,long target,long plant,String externalApproval,String reason,String correlation){return write("ASSIGNED",actor,target,plant,externalApproval,reason,correlation,false,false);}
    @Override public ValidatorAssignmentChangeResult modify(long actor,long target,long plant,String externalApproval,String reason,String correlation){return write("MODIFIED",actor,target,plant,externalApproval,reason,correlation,true,false);}
    @Override public ValidatorAssignmentChangeResult revoke(long actor,long target,long plant,String reason,String correlation){return write("REVOKED",actor,target,plant,null,reason,correlation,true,true);}
    private ValidatorAssignmentChangeResult write(String action,long actor,long target,long plant,String approval,String reason,String correlation,boolean existingRequired,boolean revoke){
        if(actor<=0||target<=0||plant<=0)throw new IllegalArgumentException("Positive actorUserId, targetUserId and plantId are required");
        try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{
            setAudit(c,actor,correlation);lock(c,plant);ensurePlantActive(c,plant);ensureTargetRole(c,target,plant);CanonicalPlantAccess.requireGeneralGrant(c,target,plant);Long id=currentId(c,target,plant);
            if(!existingRequired&&id!=null)throw new ValidatorAssignmentConflictException("Target user already has current plant access and is already an effective KPI OEE validator for this plant");
            if(existingRequired&&id==null)throw new ValidatorAssignmentConflictException("No current plant access exists for the target validator and plant");
            Instant now=Instant.now();long changed;
            if(id==null){int count=effectiveCount(c,plant);if(count>=MAX_VALIDATORS_PER_PLANT)throw new ValidatorAssignmentConflictException("Plant already has the maximum of two effective KPI OEE validators");
                try(PreparedStatement p=c.prepareStatement("INSERT INTO app_security.user_plant_access(user_id,plant_id,valid_from,granted_by_user_id,reason) VALUES(?,?,CURRENT_TIMESTAMP,?,?) RETURNING user_plant_access_id")){p.setLong(1,target);p.setLong(2,plant);p.setLong(3,actor);p.setString(4,assignmentReason(approval,reason));try(ResultSet r=p.executeQuery()){r.next();changed=r.getLong(1);}}
            }else{changed=id;String stored=revoke?revocationReason(reason):assignmentReason(approval,reason);try(PreparedStatement p=c.prepareStatement("UPDATE app_security.user_plant_access SET valid_to=?,reason=? WHERE user_plant_access_id=?")){if(revoke)p.setTimestamp(1,Timestamp.from(now));else p.setNull(1,Types.TIMESTAMP_WITH_TIMEZONE);p.setString(2,stored);p.setLong(3,changed);p.executeUpdate();}}
            int count=effectiveCount(c,plant);ValidatorAssignmentAdminView row=find(c,changed);c.commit();return new ValidatorAssignmentChangeResult(action,row.assignmentId(),row.targetUserId(),row.plantId(),row.validFrom(),row.validTo(),actor,now,count,MAX_VALIDATORS_PER_PLANT);
        }catch(RuntimeException|SQLException e){try{c.rollback();}catch(SQLException ignored){}if(e instanceof RuntimeException re)throw re;throw new IllegalStateException("Unable to change KPI validator assignment",e);}}catch(SQLException e){throw new IllegalStateException("Unable to change KPI validator assignment",e);}
    }
    private void ensureTargetRole(Connection c,long user,long plant)throws SQLException{String q="""
        SELECT EXISTS(SELECT 1 FROM app_security.user_identity u JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id
          JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE JOIN app_security.role_capability rc ON rc.role_id=r.role_id
          JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE
         WHERE u.user_id=? AND u.is_active=TRUE AND r.role_code=? AND cap.capability_code=?
           AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP)
           AND (ura.plant_id IS NULL OR ura.plant_id=?) AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))""";
        try(PreparedStatement p=c.prepareStatement(q)){p.setLong(1,user);p.setString(2,validatorRoleCode);p.setString(3,validatorCapabilityCode);p.setLong(4,plant);try(ResultSet r=p.executeQuery()){r.next();if(!r.getBoolean(1))throw new ValidatorAssignmentConflictException("Target user must have current canonical role "+validatorRoleCode+" with capability "+validatorCapabilityCode+" before plant scope can be granted");}}
    }
    private void ensurePlantActive(Connection c,long plant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT is_active FROM core.plant WHERE plant_id=?")){p.setLong(1,plant);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Plant not found");if(!r.getBoolean(1))throw new IllegalArgumentException("Plant is inactive");}}}
    private Long currentId(Connection c,long user,long plant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT user_plant_access_id FROM app_security.user_plant_access WHERE user_id=? AND plant_id=? AND starts_with(reason, 'KPI_OEE_VALIDATION_SCOPE | externalApprovalReference=') AND valid_from<=CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP) ORDER BY valid_from DESC,user_plant_access_id DESC")){p.setLong(1,user);p.setLong(2,plant);try(ResultSet r=p.executeQuery()){if(!r.next())return null;long id=r.getLong(1);if(r.next())throw new IllegalStateException("Multiple current user_plant_access rows exist for the same user and plant");return id;}}}
    private int effectiveCount(Connection c,long plant)throws SQLException{String q="""
        SELECT COUNT(DISTINCT u.user_id) FROM app_security.user_identity u JOIN app_security.user_plant_access upa ON upa.user_id=u.user_id
        JOIN app_security.user_role_assignment ura ON ura.user_id=u.user_id JOIN app_security.role r ON r.role_id=ura.role_id
        JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability cap ON cap.capability_id=rc.capability_id
        WHERE u.is_active=TRUE AND upa.plant_id=? AND starts_with(upa.reason, 'KPI_OEE_VALIDATION_SCOPE | externalApprovalReference=')
        AND EXISTS (SELECT 1 FROM app_security.user_plant_access gp
              WHERE gp.user_id=u.user_id AND gp.plant_id=upa.plant_id
                AND gp.valid_from<=CURRENT_TIMESTAMP AND (gp.valid_to IS NULL OR gp.valid_to>CURRENT_TIMESTAMP)
                AND COALESCE(gp.reason,'') NOT LIKE 'KPI_MANUALES:%'
                AND COALESCE(gp.reason,'') NOT LIKE 'KPI_APROBACION:%'
                AND COALESCE(gp.reason,'') NOT LIKE 'KPI_OEE_VALIDATION_SCOPE %')
        AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
          AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP) AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id)
          AND r.role_code=? AND r.is_active=TRUE AND cap.capability_code=? AND cap.is_active=TRUE AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP)""";
        try(PreparedStatement p=c.prepareStatement(q)){p.setLong(1,plant);p.setString(2,validatorRoleCode);p.setString(3,validatorCapabilityCode);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}
    }
    private ValidatorAssignmentAdminView find(Connection c,long id)throws SQLException{String q="""
        SELECT upa.user_plant_access_id assignment_id,upa.user_id,upa.plant_id,u.display_name,u.corporate_email,upa.valid_from,upa.valid_to,upa.granted_by_user_id assigned_by_user_id,upa.reason,
        (u.is_active AND starts_with(upa.reason, 'KPI_OEE_VALIDATION_SCOPE | externalApprovalReference=') AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP) AND EXISTS(SELECT 1 FROM app_security.user_role_assignment ura JOIN app_security.role r ON r.role_id=ura.role_id AND r.is_active=TRUE JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability cap ON cap.capability_id=rc.capability_id AND cap.is_active=TRUE WHERE ura.user_id=upa.user_id AND r.role_code=? AND cap.capability_code=? AND ura.valid_from<=CURRENT_TIMESTAMP AND (ura.valid_to IS NULL OR ura.valid_to>CURRENT_TIMESTAMP) AND (ura.plant_id IS NULL OR ura.plant_id=upa.plant_id) AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))) effective
        FROM app_security.user_plant_access upa JOIN app_security.user_identity u ON u.user_id=upa.user_id WHERE upa.user_plant_access_id=?""";
        try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,validatorRoleCode);p.setString(2,validatorCapabilityCode);p.setLong(3,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Assignment disappeared after write");return map(r);}}
    }
    private static ValidatorAssignmentAdminView map(ResultSet r)throws SQLException{Timestamp f=r.getTimestamp("valid_from"),t=r.getTimestamp("valid_to");long a=r.getLong("assigned_by_user_id");Long av=r.wasNull()?null:a;return new ValidatorAssignmentAdminView(r.getLong("assignment_id"),r.getLong("user_id"),r.getLong("plant_id"),r.getString("display_name"),r.getString("corporate_email"),f.toInstant(),t==null?null:t.toInstant(),av,r.getString("reason"),r.getBoolean("effective"));}
    private boolean configuredValidatorRole(){return exists("SELECT EXISTS(SELECT 1 FROM app_security.role r JOIN app_security.role_capability rc ON rc.role_id=r.role_id JOIN app_security.capability c ON c.capability_id=rc.capability_id WHERE r.role_code=? AND r.is_active=TRUE AND c.capability_code=? AND c.is_active=TRUE AND rc.valid_from<=CURRENT_TIMESTAMP AND (rc.valid_to IS NULL OR rc.valid_to>CURRENT_TIMESTAMP))",p->{p.setString(1,validatorRoleCode);p.setString(2,validatorCapabilityCode);});}
    private boolean configuredCapability(String code){return exists("SELECT EXISTS(SELECT 1 FROM app_security.capability WHERE capability_code=? AND is_active=TRUE)",p->p.setString(1,code));}
    private boolean exists(String q,Binder b){try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(q)){b.bind(p);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to resolve assignment administration configuration",e);}}
    private void setAudit(Connection c,long actor,String corr)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.user_id', ?, true),set_config('app.source_system','GBC_APP',true),set_config('app.correlation_id', ?, true)")){p.setString(1,Long.toString(actor));p.setString(2,corr==null?"":corr);p.execute();}}
    private void lock(Connection c,long plant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){p.setString(1,"kpi-oee-validator-assignment:"+plant);p.execute();}}
    private static String assignmentReason(String approval,String reason){String a=required(approval,"externalApprovalReference"),r=normalize(reason);return "KPI_OEE_VALIDATION_SCOPE | externalApprovalReference="+a+(r==null?"":" | reason="+r);}
    private static String revocationReason(String reason){return "KPI_OEE_VALIDATION_SCOPE_REVOKED | reason="+required(reason,"reason");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
    private static String normalize(String v){return v==null||v.isBlank()?null:v.trim();}
    @FunctionalInterface private interface Binder{void bind(PreparedStatement p)throws SQLException;}
}
