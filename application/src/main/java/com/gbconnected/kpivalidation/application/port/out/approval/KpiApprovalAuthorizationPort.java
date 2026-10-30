package com.gbconnected.kpivalidation.application.port.out.approval;
import com.gbconnected.kpivalidation.application.ActorContext;
public interface KpiApprovalAuthorizationPort {
    boolean authorized(ActorContext actor,long canonicalUserId,long plantId);
    String source();
}
