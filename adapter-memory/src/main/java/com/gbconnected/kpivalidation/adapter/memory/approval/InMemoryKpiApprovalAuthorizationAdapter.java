package com.gbconnected.kpivalidation.adapter.memory.approval;
import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalAuthorizationPort;
import java.util.UUID;
public final class InMemoryKpiApprovalAuthorizationAdapter implements KpiApprovalAuthorizationPort {
 private final UUID tenant;private final UUID object;private final long user;private final long plant;
 public InMemoryKpiApprovalAuthorizationAdapter(UUID tenant,UUID object,long user,long plant){this.tenant=tenant;this.object=object;this.user=user;this.plant=plant;}
 public boolean authorized(ActorContext a,long u,long p){return a!=null&&tenant.equals(a.tenantId())&&object.equals(a.objectId())&&user==u&&plant==p;}
 public String source(){return "MEMORY_KPI_APPROVAL_AUTHORIZATION";}
}
