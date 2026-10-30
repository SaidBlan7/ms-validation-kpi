package com.gbconnected.kpivalidation.adapter.memory.approval;
import com.gbconnected.kpivalidation.application.approval.scope.KpiApprovalScopeConflictException;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalScopeAdministrationPort;
import com.gbconnected.kpivalidation.domain.approval.KpiApproverPlantAssignment;
import java.time.Instant;import java.util.*;
public final class InMemoryKpiApprovalScopeAdministrationAdapter implements KpiApprovalScopeAdministrationPort {
    private final UUID tenant;private final UUID object;private final long actorUser;private final String capability;private final InMemoryKpiApprovalAuthorizationAdapter authorization;private final Map<String,KpiApproverPlantAssignment> current=new LinkedHashMap<>();private long sequence=2000;
    public InMemoryKpiApprovalScopeAdministrationAdapter(UUID tenant,UUID object,long actorUser,long initialPlant,String capability,InMemoryKpiApprovalAuthorizationAdapter authorization){this.tenant=tenant;this.object=object;this.actorUser=actorUser;this.capability=capability;this.authorization=Objects.requireNonNull(authorization);current.put(key(actorUser,initialPlant),new KpiApproverPlantAssignment(++sequence,actorUser,initialPlant,Instant.now(),null,actorUser,"DEV_SEED",true));this.authorization.grant(actorUser,initialPlant);}
    public boolean available(){return true;} public String adminCapabilityCode(){return capability;} public boolean canAdminister(UUID t,UUID o,long p){return tenant.equals(t)&&object.equals(o)&&p>0;}
    public synchronized List<KpiApproverPlantAssignment> list(long plant){return current.values().stream().filter(x->x.plantId()==plant).toList();}
    public synchronized KpiApproverPlantAssignment assign(long actor,long user,long plant,String reason,String corr){String k=key(user,plant);var x=current.get(k);if(x!=null&&x.effective())throw new KpiApprovalScopeConflictException("Target user already has an effective KPI Approval assignment for this plant");var y=new KpiApproverPlantAssignment(++sequence,user,plant,Instant.now(),null,actor,reason,true);current.put(k,y);authorization.grant(user,plant);return y;}
    public synchronized KpiApproverPlantAssignment revoke(long actor,long user,long plant,String reason,String corr){String k=key(user,plant);var x=current.get(k);if(x==null||!x.effective())throw new KpiApprovalScopeConflictException("No effective KPI Approval assignment exists for target user and plant");var y=new KpiApproverPlantAssignment(x.assignmentId(),x.userId(),x.plantId(),x.validFrom(),Instant.now(),x.assignedByUserId(),reason,false);current.put(k,y);authorization.revoke(user,plant);return y;}
    private static String key(long u,long p){return u+":"+p;}
}
