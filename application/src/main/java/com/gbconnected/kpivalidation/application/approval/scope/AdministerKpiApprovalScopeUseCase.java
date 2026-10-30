package com.gbconnected.kpivalidation.application.approval.scope;
import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.domain.approval.KpiApproverPlantAssignment;
import java.util.List;
public interface AdministerKpiApprovalScopeUseCase {
    List<KpiApproverPlantAssignment> list(ActorContext actor,long plantId);
    KpiApproverPlantAssignment assign(ActorContext actor,AssignKpiApproverPlantCommand command,String correlationId);
    KpiApproverPlantAssignment revoke(ActorContext actor,RevokeKpiApproverPlantCommand command,String correlationId);
    KpiApprovalScopePolicyView policy();
}
