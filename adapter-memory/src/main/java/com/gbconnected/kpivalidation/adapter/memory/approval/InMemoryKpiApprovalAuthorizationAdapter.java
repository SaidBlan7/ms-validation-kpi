package com.gbconnected.kpivalidation.adapter.memory.approval;
import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalAuthorizationPort;
import java.util.*;
public final class InMemoryKpiApprovalAuthorizationAdapter implements KpiApprovalAuthorizationPort {
 private final UUID tenant;private final UUID object;private final Set<String> grants=new LinkedHashSet<>();
 public InMemoryKpiApprovalAuthorizationAdapter(UUID tenant,UUID object,long user,long plant){this.tenant=tenant;this.object=object;grant(user,plant);}
 public synchronized boolean authorized(ActorContext a,long u,long p){return a!=null&&tenant.equals(a.tenantId())&&object.equals(a.objectId())&&grants.contains(key(u,p));}
 public String source(){return "MEMORY_KPI_APPROVAL_AUTHORIZATION";}
 public synchronized void grant(long user,long plant){if(user>0&&plant>0)grants.add(key(user,plant));}
 public synchronized void revoke(long user,long plant){grants.remove(key(user,plant));}
 private static String key(long u,long p){return u+":"+p;}
}
