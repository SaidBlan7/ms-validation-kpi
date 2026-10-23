package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.manual.scope.*;
import com.gbconnected.kpivalidation.domain.manual.*;
import java.util.List;
import org.slf4j.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/manual-kpis/scope", "/api/manual-kpis/scope"})
public class ManualKpiScopeController {
    private static final Logger LOG=LoggerFactory.getLogger(ManualKpiScopeController.class);
    private final ManualKpiScopeUseCase scope;
    private final AdministerManualKpiScopeUseCase administration;
    public ManualKpiScopeController(ManualKpiScopeUseCase scope,AdministerManualKpiScopeUseCase administration){this.scope=scope;this.administration=administration;}

    @GetMapping("/me")
    public List<ManualKpiPlantScope> me(@AuthenticationPrincipal Jwt jwt){
        ActorContext actor=RequestActorContextResolver.resolve(jwt);
        var result=scope.myScope(actor);
        LOG.info("event=manual_kpi_scope_read actorObject={} plants={} correlationId={} result=OK",actor.objectId(),result.size(),correlation());
        return result;
    }

    @GetMapping("/policy") public ManualKpiScopePolicyView policy(){return scope.policy();}

    @GetMapping("/admin/assignments")
    public List<ManualKpiPlantAssignment> assignments(@AuthenticationPrincipal Jwt jwt,@RequestParam long plantId){
        ActorContext actor=RequestActorContextResolver.resolve(jwt);return administration.list(actor,plantId);
    }

    @PostMapping("/admin/assignments")
    public ManualKpiPlantAssignment assign(@AuthenticationPrincipal Jwt jwt,@RequestBody PlantAssignmentRequest request){
        if(request==null)throw new IllegalArgumentException("JSON body is required");
        ActorContext actor=RequestActorContextResolver.resolve(jwt);
        var out=administration.assign(actor,new AssignManualKpiPlantCommand(request.targetUserId(),request.plantId(),request.reason()),correlation());
        LOG.info("event=manual_kpi_plant_access_assigned actorObject={} targetUserId={} plantId={} assignmentId={} correlationId={} result=OK",actor.objectId(),out.userId(),out.plantId(),out.assignmentId(),correlation());
        return out;
    }

    @DeleteMapping("/admin/assignments")
    public ManualKpiPlantAssignment revoke(@AuthenticationPrincipal Jwt jwt,@RequestBody PlantAssignmentRequest request){
        if(request==null)throw new IllegalArgumentException("JSON body is required");
        ActorContext actor=RequestActorContextResolver.resolve(jwt);
        var out=administration.revoke(actor,new RevokeManualKpiPlantCommand(request.targetUserId(),request.plantId(),request.reason()),correlation());
        LOG.info("event=manual_kpi_plant_access_revoked actorObject={} targetUserId={} plantId={} assignmentId={} correlationId={} result=OK",actor.objectId(),out.userId(),out.plantId(),out.assignmentId(),correlation());
        return out;
    }

    public record PlantAssignmentRequest(long targetUserId,long plantId,String reason){}
    private static String correlation(){String x=MDC.get("correlationId");return x==null?"":x;}
}
