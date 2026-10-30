package com.gbconnected.kpivalidation.adapter.postgres.approval;
import com.gbconnected.kpivalidation.application.approval.*;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalPersistencePort;
import java.sql.*;import java.time.*;import java.util.*;import javax.sql.DataSource;
public final class JdbcKpiApprovalPersistenceAdapter implements KpiApprovalPersistencePort {
 private final DataSource ds; public JdbcKpiApprovalPersistenceAdapter(DataSource ds){this.ds=Objects.requireNonNull(ds);}
 public List<ApprovalValidationView> pending(){return query(null,true);}
 public List<ApprovalValidationView> all(){return query(null,false);}
 public Optional<ApprovalValidationView> find(long id){List<ApprovalValidationView>x=query(id,false);return x.stream().findFirst();}
 public void expirePending(Instant now,String correlationId){
  String sql="UPDATE kpi.kpi_validation SET status_code='EXPIRED' WHERE status_code='PENDING' AND expires_at < ?";
  try(Connection c=ds.getConnection()){boolean ac=c.getAutoCommit();c.setAutoCommit(false);auditContext(c,null,correlationId);try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,OffsetDateTime.ofInstant(now,ZoneOffset.UTC));p.executeUpdate();}c.commit();c.setAutoCommit(ac);}catch(SQLException e){throw new IllegalStateException("Unable to expire KPI validations",e);}
 }
 public ApprovalValidationView decide(long id,long userId,ApprovalDecision decision,Instant at,String correlationId){
  String sql="UPDATE kpi.kpi_validation SET status_code=?,approved_by_user_id=?,approved_at=? WHERE kpi_validation_id=? AND status_code='PENDING' AND expires_at>=?";
  try(Connection c=ds.getConnection()){boolean ac=c.getAutoCommit();c.setAutoCommit(false);auditContext(c,userId,correlationId);int n;try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,decision.name());p.setLong(2,userId);p.setObject(3,OffsetDateTime.ofInstant(at,ZoneOffset.UTC));p.setLong(4,id);p.setObject(5,OffsetDateTime.ofInstant(at,ZoneOffset.UTC));n=p.executeUpdate();}if(n!=1){c.rollback();throw new ApprovalConflictException("Validation was already decided or expired");}c.commit();c.setAutoCommit(ac);return find(id).orElseThrow(()->new ApprovalNotFoundException("KPI validation not found after decision"));}
  catch(ApprovalConflictException e){throw e;}catch(SQLException e){throw new IllegalStateException("Unable to decide KPI validation",e);}
 }
 public boolean supportsWasteValidations(){return true;}
 private List<ApprovalValidationView> query(Long id,boolean pendingOnly){
  StringBuilder sql=new StringBuilder("""
   SELECT v.kpi_validation_id,v.plant_id,v.line_id,v.oee_date,v.status_code,v.reason_type,v.justification,v.cause,v.action_plan,v.incident_reference,
          v.proposed_by_user_id,pu.display_name AS proposed_by_display_name,v.proposed_at,v.expires_at,
          v.approved_by_user_id,du.display_name AS decided_by_display_name,v.approved_at,
          d.kpi_result_id,kd.kpi_code,kd.kpi_name,
          d.stop_classification_id,sc.classification_code AS stop_classification_code,sc.classification_name AS stop_classification_name,sc.oee_stop_type_code,
          d.metric_definition_id,md.metric_code,md.metric_name,md.source_domain AS metric_source_domain,
          d.original_value,d.proposed_value,d.uom_id,u.uom_code,u.uom_name
     FROM kpi.kpi_validation v
     LEFT JOIN app_security.user_identity pu ON pu.user_id=v.proposed_by_user_id
     LEFT JOIN app_security.user_identity du ON du.user_id=v.approved_by_user_id
     LEFT JOIN kpi.kpi_validation_detail d ON d.kpi_validation_id=v.kpi_validation_id
     LEFT JOIN kpi.kpi_result kr ON kr.kpi_result_id=d.kpi_result_id
     LEFT JOIN kpi.kpi_definition kd ON kd.kpi_definition_id=kr.kpi_definition_id
     LEFT JOIN stops.stop_classification sc ON sc.stop_classification_id=d.stop_classification_id
     LEFT JOIN kpi.metric_definition md ON md.metric_definition_id=d.metric_definition_id
     LEFT JOIN core.unit_of_measure u ON u.uom_id=d.uom_id
    WHERE 1=1
   """);
  if(id!=null)sql.append(" AND v.kpi_validation_id=?"); if(pendingOnly)sql.append(" AND v.status_code='PENDING'");
  sql.append(" ORDER BY v.proposed_at DESC,v.kpi_validation_id,d.kpi_validation_detail_id");
  LinkedHashMap<Long,Builder>b=new LinkedHashMap<>();try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql.toString())){
   if(id!=null)p.setLong(1,id);
   try(ResultSet r=p.executeQuery()){while(r.next()){
    long vid=r.getLong("kpi_validation_id");Builder x=b.computeIfAbsent(vid,k->new Builder(r));
    Long kr=objLong(r,"kpi_result_id"),sc=objLong(r,"stop_classification_id"),md=objLong(r,"metric_definition_id");
    if(kr!=null||sc!=null||md!=null){
     x.changes.add(new ApprovalValueChangeView(
             kr,r.getString("kpi_code"),r.getString("kpi_name"),
             sc,r.getString("stop_classification_code"),r.getString("stop_classification_name"),r.getString("oee_stop_type_code"),
             md,r.getString("metric_code"),r.getString("metric_name"),r.getString("metric_source_domain"),
             r.getBigDecimal("original_value"),r.getBigDecimal("proposed_value"),objLong(r,"uom_id"),r.getString("uom_code"),r.getString("uom_name")));
     String sourceDomain=r.getString("metric_source_domain");
     if(sourceDomain!=null&&!sourceDomain.isBlank())x.metricSourceDomains.add(sourceDomain.trim().toUpperCase(Locale.ROOT));
    }
   }}
  }catch(SQLException e){throw new IllegalStateException("Unable to read KPI approvals",e);}return b.values().stream().map(Builder::build).toList();
 }
 private static Long objLong(ResultSet r,String n)throws SQLException{long v=r.getLong(n);return r.wasNull()?null:v;}
 private static Instant instant(ResultSet r,String n)throws SQLException{Object o=r.getObject(n);if(o==null)return null;if(o instanceof OffsetDateTime x)return x.toInstant();if(o instanceof Timestamp t)return t.toInstant();throw new SQLException("Unsupported timestamp type");}
 private static void auditContext(Connection c,Long user,String corr)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.source_system','GBC_APP',true), set_config('app.correlation_id', ?, true)")){p.setString(1,user==null?"":Long.toString(user));p.setString(2,corr==null?"":corr);p.execute();}}
 private static final class Builder {long id,plant;Long line;LocalDate date;String status,reason,justification,cause,plan,incident,proposedByName,decidedByName;long proposedBy;Instant proposedAt,expires;Long decidedBy;Instant decidedAt;List<ApprovalValueChangeView> changes=new ArrayList<>();Set<String> metricSourceDomains=new LinkedHashSet<>();
  Builder(ResultSet r){try{id=r.getLong("kpi_validation_id");plant=r.getLong("plant_id");line=objLong(r,"line_id");date=r.getObject("oee_date",LocalDate.class);status=r.getString("status_code");reason=r.getString("reason_type");justification=r.getString("justification");cause=r.getString("cause");plan=r.getString("action_plan");incident=r.getString("incident_reference");proposedBy=r.getLong("proposed_by_user_id");proposedByName=r.getString("proposed_by_display_name");proposedAt=instant(r,"proposed_at");expires=instant(r,"expires_at");decidedBy=objLong(r,"approved_by_user_id");decidedByName=r.getString("decided_by_display_name");decidedAt=instant(r,"approved_at");}catch(SQLException e){throw new IllegalStateException(e);}}
  ApprovalValidationView build(){
   boolean stop=changes.stream().anyMatch(x->x.stopClassificationId()!=null),metric=changes.stream().anyMatch(x->x.metricDefinitionId()!=null),kpi=changes.stream().anyMatch(x->x.kpiResultId()!=null);
   boolean waste=!stop&&!metricSourceDomains.isEmpty()&&metricSourceDomains.stream().allMatch("WASTE"::equals);
   String domain=waste?"WASTE":"OEE";
   String kind=waste?"WASTE_METRIC":stop?"STOP_TIME":metric?"MASS_PERFORMANCE":kpi?"KPI_RESULT":"OEE_OTHER";
   return new ApprovalValidationView(id,domain,kind,status,plant,line,date,reason,justification,cause,plan,incident,proposedBy,proposedByName,proposedAt,expires,decidedBy,decidedByName,decidedAt,"PENDING".equals(status),changes);
  }
 }
}
