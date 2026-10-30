package com.gbconnected.kpivalidation.application.approval;
import com.gbconnected.kpivalidation.application.ActorContext;
import java.util.List;
public interface KpiApprovalUseCase {
    List<ApprovalValidationView> pending(ActorContext actor);
    ApprovalValidationView get(ActorContext actor,long validationId);
    ApprovalValidationView decide(ActorContext actor,long validationId,ApprovalDecision decision,String correlationId);
    ApprovalPolicyView policy();
}
