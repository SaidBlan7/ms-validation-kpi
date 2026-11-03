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
  String sql="UPDATE kpi.kpi_validation SET status_code='EXPIRED' WHERE status_code='PENDING' AND expires_at < clock_timestamp()";
  try(Connection c=ds.getConnection()){boolean ac=c.getAutoCommit();c.setAutoCommit(false);auditContext(c,null,correlationId);try(PreparedStatement p=c.prepareStatement(sql)){p.executeUpdate();}c.commit();c.setAutoCommit(ac);}catch(SQLException e){throw new IllegalStateException("Unable to expire KPI validations",e);}
 }
 public ApprovalValidationView decide(long id,long userId,ApprovalDecision decision,Instant ignoredAt,String correlationId){
  Objects.requireNonNull(decision,"decision is required");
  // Serialize ALL requests against this validation, including exact retries.
  // The database clock is read only after the row lock has been obtained.
  String lockSql="SELECT status_code, expires_at, approved_by_user_id FROM kpi.kpi_validation WHERE kpi_validation_id=? FOR UPDATE";
  String markSql="UPDATE kpi.kpi_validation SET status_code=?, approved_by_user_id=?, approved_at=? WHERE kpi_validation_id=? AND status_code='PENDING'";
  try(Connection c=ds.getConnection()){
   boolean originalAutoCommit=c.getAutoCommit();
   c.setAutoCommit(false);
   try {
    auditContext(c,userId,correlationId);
    String currentStatus; Instant deadline; Long originalDecider;
    try(PreparedStatement lock=c.prepareStatement(lockSql)){
     lock.setLong(1,id);
     try(ResultSet row=lock.executeQuery()){
      if(!row.next())throw new ApprovalNotFoundException("KPI validation not found");
      currentStatus=row.getString("status_code");
      deadline=instant(row,"expires_at");
      originalDecider=objLong(row,"approved_by_user_id");
     }
    }
    if(!"PENDING".equals(currentStatus)){
     if(currentStatus.equals(decision.name()) && Objects.equals(originalDecider,userId)){
      c.commit(); // Same actor + same outcome: safe idempotent retry.
     } else {
      throw new ApprovalConflictException("Validation has an immutable final outcome: "+currentStatus);
     }
    } else {
     // Fail closed when a validation mixes waste and non-waste metric details.
     // The canonical validation header has no domain column: source_domain on
     // metric_definition is the authoritative discriminator for waste.
     assertCoherentWasteDomain(c,id);
     Instant dbNow;
     try(PreparedStatement now=c.prepareStatement("SELECT clock_timestamp()" );ResultSet t=now.executeQuery()){
      t.next();dbNow=instant(t,"clock_timestamp");
     }
     if(dbNow.isAfter(deadline)){
      try(PreparedStatement expired=c.prepareStatement("UPDATE kpi.kpi_validation SET status_code='EXPIRED' WHERE kpi_validation_id=? AND status_code='PENDING'")){
       expired.setLong(1,id);expired.executeUpdate();
      }
      c.commit(); // Expiration is permanent, with the canonical audit trigger.
      throw new ApprovalConflictException("Validation expired before decision");
     }
     // Persist exactly one final decision and the actor atomically.
     try(PreparedStatement update=c.prepareStatement(markSql)){
      update.setString(1,decision.name());update.setLong(2,userId);update.setObject(3,OffsetDateTime.ofInstant(dbNow,ZoneOffset.UTC));update.setLong(4,id);
      if(update.executeUpdate()!=1)throw new ApprovalConflictException("Validation is no longer pending");
     }
     c.commit();
    }
   } catch(SQLException|RuntimeException e){
    try{if(!c.getAutoCommit())c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
    throw e;
   } finally {c.setAutoCommit(originalAutoCommit);}
  }catch(ApprovalConflictException|ApprovalNotFoundException e){throw e;}
   catch(SQLException e){throw new IllegalStateException("Unable to decide KPI validation",e);}
  return find(id).orElseThrow(()->new ApprovalNotFoundException("KPI validation not found after decision"));
 }
 private static void assertCoherentWasteDomain(Connection c,long validationId) throws SQLException {
  // Lock the detail rows while deciding so the scope cannot change between
  // the domain check and the final state transition.
  String sql="""
      SELECT d.kpi_validation_detail_id, d.kpi_result_id, d.stop_classification_id,
             d.metric_definition_id, md.source_domain
      FROM kpi.kpi_validation_detail d
      LEFT JOIN kpi.metric_definition md ON md.metric_definition_id=d.metric_definition_id
      WHERE d.kpi_validation_id=?
      ORDER BY d.kpi_validation_detail_id FOR UPDATE OF d
      """;
  boolean anyWaste=false,anyOther=false;
  try(PreparedStatement statement=c.prepareStatement(sql)) {
   statement.setLong(1,validationId);
   try(ResultSet r=statement.executeQuery()) {
    while(r.next()) {
     String source=r.getString("source_domain");
     boolean waste="WASTE".equalsIgnoreCase(source==null?"":source.trim());
     boolean exclusivelyWaste=waste && objLong(r,"metric_definition_id")!=null
         && objLong(r,"kpi_result_id")==null && objLong(r,"stop_classification_id")==null;
     if(waste)anyWaste=true;
     if(!exclusivelyWaste)anyOther=true;
    }
   }
  }
  if(anyWaste && anyOther) throw new ApprovalConflictException(
      "Waste validation has mixed or ambiguous metric details; decision blocked");
 }
 public boolean supportsWasteValidations(){return true;}
 private List<ApprovalValidationView> query(Long id,boolean pendingOnly){
  StringBuilder sql=new StringBuilder("""
   SELECT v.kpi_validation_id,v.plant_id,p.plant_code,p.plant_name,v.line_id,l.line_code,l.line_name,v.oee_date,v.status_code,v.reason_type,v.justification,v.cause,v.action_plan,v.incident_reference,
          v.proposed_by_user_id,pu.display_name AS proposed_by_display_name,v.proposed_at,v.expires_at,
          v.approved_by_user_id,du.display_name AS decided_by_display_name,v.approved_at,
          d.kpi_validation_detail_id,d.kpi_result_id,kd.kpi_code,kd.kpi_name,
          d.stop_classification_id,sc.classification_code AS stop_classification_code,sc.classification_name AS stop_classification_name,sc.oee_stop_type_code,
          d.metric_definition_id,md.metric_code,md.metric_name,md.source_domain AS metric_source_domain,
          d.original_value,d.proposed_value,d.uom_id,u.uom_code,u.uom_name
     FROM kpi.kpi_validation v
     JOIN core.plant p ON p.plant_id=v.plant_id
     LEFT JOIN core.line l ON l.line_id=v.line_id
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
    // A detail is a canonical value change even when its optional reference FKs are null.
    // Dropping such rows hides the before/after evidence from the approver.
    if(objLong(r,"kpi_validation_detail_id")!=null){
     x.changes.add(new ApprovalValueChangeView(
             kr,r.getString("kpi_code"),r.getString("kpi_name"),
             sc,r.getString("stop_classification_code"),r.getString("stop_classification_name"),r.getString("oee_stop_type_code"),
             md,r.getString("metric_code"),r.getString("metric_name"),r.getString("metric_source_domain"),
             r.getBigDecimal("original_value"),r.getBigDecimal("proposed_value"),null,null,objLong(r,"uom_id"),r.getString("uom_code"),r.getString("uom_name")));
     String sourceDomain=r.getString("metric_source_domain");
     if(sourceDomain!=null&&!sourceDomain.isBlank())x.metricSourceDomains.add(sourceDomain.trim().toUpperCase(Locale.ROOT));
    }
   }}
  }catch(SQLException e){throw new IllegalStateException("Unable to read KPI approvals",e);}return b.values().stream().map(Builder::build).toList();
 }
 private static Long objLong(ResultSet r,String n)throws SQLException{long v=r.getLong(n);return r.wasNull()?null:v;}
 private static Instant instant(ResultSet r,String n)throws SQLException{Object o=r.getObject(n);if(o==null)return null;if(o instanceof OffsetDateTime x)return x.toInstant();if(o instanceof Timestamp t)return t.toInstant();throw new SQLException("Unsupported timestamp type");}
 private static void auditContext(Connection c,Long user,String corr)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.source_system','GBC_APP',true), set_config('app.correlation_id', ?, true)")){p.setString(1,user==null?"":Long.toString(user));p.setString(2,corr==null?"":corr);p.execute();}}
 private static final class Builder {long id,plant;Long line;LocalDate date;String plantCode,plantName,lineCode,lineName,status,reason,justification,cause,plan,incident,proposedByName,decidedByName;long proposedBy;Instant proposedAt,expires;Long decidedBy;Instant decidedAt;List<ApprovalValueChangeView> changes=new ArrayList<>();Set<String> metricSourceDomains=new LinkedHashSet<>();
  Builder(ResultSet r){try{id=r.getLong("kpi_validation_id");plant=r.getLong("plant_id");plantCode=r.getString("plant_code");plantName=r.getString("plant_name");line=objLong(r,"line_id");lineCode=r.getString("line_code");lineName=r.getString("line_name");date=r.getObject("oee_date",LocalDate.class);status=r.getString("status_code");reason=r.getString("reason_type");justification=r.getString("justification");cause=r.getString("cause");plan=r.getString("action_plan");incident=r.getString("incident_reference");proposedBy=r.getLong("proposed_by_user_id");proposedByName=r.getString("proposed_by_display_name");proposedAt=instant(r,"proposed_at");expires=instant(r,"expires_at");decidedBy=objLong(r,"approved_by_user_id");decidedByName=r.getString("decided_by_display_name");decidedAt=instant(r,"approved_at");}catch(SQLException e){throw new IllegalStateException(e);}}
  ApprovalValidationView build(){
   boolean stop=changes.stream().anyMatch(x->x.stopClassificationId()!=null),metric=changes.stream().anyMatch(x->x.metricDefinitionId()!=null),kpi=changes.stream().anyMatch(x->x.kpiResultId()!=null);
   // The canonical header does not declare OEE vs WASTE. Only rows backed by
   // WASTE metric definitions, with no OEE references, are waste validations.
   // Mixed or ambiguous rows must never be silently classified as WASTE.
   boolean hasWaste=metricSourceDomains.contains("WASTE");
   boolean waste=hasWaste && changes.stream().allMatch(x -> x.metricDefinitionId()!=null
           && "WASTE".equalsIgnoreCase(x.metricSourceDomain())
           && x.kpiResultId()==null && x.stopClassificationId()==null);
   boolean mixed=hasWaste && !waste;
   String domain=mixed?"MIXED":waste?"WASTE":"OEE";
   String kind=mixed?"MIXED_METRICS":waste?"WASTE_METRIC":stop?"STOP_TIME":metric?"MASS_PERFORMANCE":kpi?"KPI_RESULT":"OEE_OTHER";
   List<ApprovalValueChangeView> effectiveChanges=changes.stream().map(x->x.withEffectiveForStatus(status)).toList();
   return new ApprovalValidationView(id,domain,kind,status,plant,plantCode,plantName,line,lineCode,lineName,date,reason,justification,cause,plan,incident,proposedBy,proposedByName,proposedAt,expires,decidedBy,decidedByName,decidedAt,"PENDING".equals(status),effectiveChanges);
  }
 }
}
