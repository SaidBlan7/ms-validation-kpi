package com.gbconnected.kpivalidation.appservice.web;
import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.approval.scope.*;
import com.gbconnected.kpivalidation.domain.approval.KpiApproverPlantAssignment;
import java.util.List;
import org.slf4j.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping({"/api/v1/kpi-approval/scope","/api/kpi-approval/scope"})
public final class KpiApprovalScopeController {
    private static final Logger LOG=LoggerFactory.getLogger(KpiApprovalScopeController.class);
    private final AdministerKpiApprovalScopeUseCase useCase;
    public KpiApprovalScopeController(AdministerKpiApprovalScopeUseCase useCase){this.useCase=useCase;}
    @GetMapping("/policy") public KpiApprovalScopePolicyView policy(){return useCase.policy();}
    @GetMapping("/admin/assignments") public List<KpiApproverPlantAssignment> assignments(@AuthenticationPrincipal Jwt jwt,@RequestParam long plantId){return useCase.list(RequestActorContextResolver.resolve(jwt),plantId);}
    @PostMapping("/admin/assignments") public KpiApproverPlantAssignment assign(@AuthenticationPrincipal Jwt jwt,@RequestBody PlantAssignmentRequest body){if(body==null)throw new IllegalArgumentException("JSON body is required");ActorContext a=RequestActorContextResolver.resolve(jwt);var out=useCase.assign(a,new AssignKpiApproverPlantCommand(body.targetUserId(),body.plantId(),body.reason()),correlation());LOG.info("event=kpi_approval_scope_assigned actorObject={} targetUserId={} plantId={} assignmentId={} correlationId={} result=OK",a.objectId(),out.userId(),out.plantId(),out.assignmentId(),correlation());return out;}
    @DeleteMapping("/admin/assignments") public KpiApproverPlantAssignment revoke(@AuthenticationPrincipal Jwt jwt,@RequestBody PlantAssignmentRequest body){if(body==null)throw new IllegalArgumentException("JSON body is required");ActorContext a=RequestActorContextResolver.resolve(jwt);var out=useCase.revoke(a,new RevokeKpiApproverPlantCommand(body.targetUserId(),body.plantId(),body.reason()),correlation());LOG.info("event=kpi_approval_scope_revoked actorObject={} targetUserId={} plantId={} assignmentId={} correlationId={} result=OK",a.objectId(),out.userId(),out.plantId(),out.assignmentId(),correlation());return out;}
    public record PlantAssignmentRequest(long targetUserId,long plantId,String reason){}
    private static String correlation(){String x=MDC.get("correlationId");return x==null?"":x;}
}
