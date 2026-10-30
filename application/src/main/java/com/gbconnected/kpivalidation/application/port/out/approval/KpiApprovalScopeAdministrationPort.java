package com.gbconnected.kpivalidation.application.port.out.approval;
import com.gbconnected.kpivalidation.domain.approval.KpiApproverPlantAssignment;
import java.util.*;
public interface KpiApprovalScopeAdministrationPort {
    boolean available();
    String adminCapabilityCode();
    boolean canAdminister(UUID tenantId,UUID objectId,long plantId);
    List<KpiApproverPlantAssignment> list(long plantId);
    KpiApproverPlantAssignment assign(long actorUserId,long targetUserId,long plantId,String reason,String correlationId);
    KpiApproverPlantAssignment revoke(long actorUserId,long targetUserId,long plantId,String reason,String correlationId);
    static KpiApprovalScopeAdministrationPort unavailable(){return new KpiApprovalScopeAdministrationPort(){
        public boolean available(){return false;} public String adminCapabilityCode(){return null;}
        public boolean canAdminister(UUID t,UUID o,long p){return false;} public List<KpiApproverPlantAssignment> list(long p){return List.of();}
        public KpiApproverPlantAssignment assign(long a,long u,long p,String r,String c){throw new UnsupportedOperationException("KPI Approval scope administration unavailable");}
        public KpiApproverPlantAssignment revoke(long a,long u,long p,String r,String c){throw new UnsupportedOperationException("KPI Approval scope administration unavailable");}
    };}
}
