package com.gbconnected.kpivalidation.application.approval.scope;
import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ActorIdentityPort;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalScopeAdministrationPort;
import com.gbconnected.kpivalidation.domain.approval.KpiApproverPlantAssignment;
import java.util.*;
public final class KpiApprovalScopeService implements AdministerKpiApprovalScopeUseCase {
    private final ActorIdentityPort identities; private final KpiApprovalScopeAdministrationPort administration;
    public KpiApprovalScopeService(ActorIdentityPort identities,KpiApprovalScopeAdministrationPort administration){this.identities=Objects.requireNonNull(identities);this.administration=Objects.requireNonNull(administration);}
    public List<KpiApproverPlantAssignment> list(ActorContext actor,long plantId){authorize(actor,plantId);return administration.list(plantId);}
    public KpiApproverPlantAssignment assign(ActorContext actor,AssignKpiApproverPlantCommand command,String correlationId){Objects.requireNonNull(command,"command is required");authorize(actor,command.plantId());long a=canonical(actor);validate(command.targetUserId(),command.plantId());return administration.assign(a,command.targetUserId(),command.plantId(),normalize(command.reason()),correlationId);}
    public KpiApproverPlantAssignment revoke(ActorContext actor,RevokeKpiApproverPlantCommand command,String correlationId){Objects.requireNonNull(command,"command is required");authorize(actor,command.plantId());long a=canonical(actor);validate(command.targetUserId(),command.plantId());return administration.revoke(a,command.targetUserId(),command.plantId(),required(command.reason(),"reason"),correlationId);}
    public KpiApprovalScopePolicyView policy(){return new KpiApprovalScopePolicyView("MULTIPLE_UNBOUNDED_BY_CANONICAL_MODEL","PLANT_SCOPED_ROLE_ASSIGNMENT","IMMEDIATE_ON_NEXT_SERVER_REQUEST","PENDING_REMAINS_WITH_PLANT_AND_BECOMES_VISIBLE_TO_CURRENT_APPROVERS","EXTERNAL_TO_GBC",administration.available(),administration.adminCapabilityCode());}
    private void authorize(ActorContext actor,long plant){requireActor(actor);if(plant<=0)throw new IllegalArgumentException("plantId must be positive");if(!administration.available())throw new AuthorizationException("KPI Approval scope administration is not configured");if(!administration.canAdminister(actor.tenantId(),actor.objectId(),plant))throw new AuthorizationException("Actor cannot administer KPI Approval assignments for this plant");}
    private long canonical(ActorContext actor){ActorIdentitySnapshot i=identities.findByEntraIdentity(actor.tenantId(),actor.objectId()).orElseThrow(()->new AuthorizationException("Actor is not registered in app_security.user_identity"));if(!i.active()||i.canonicalUserId()==null)throw new AuthorizationException("Canonical actor is inactive or unresolved");return i.canonicalUserId();}
    private static void requireActor(ActorContext a){if(a==null||a.tenantId()==null||a.objectId()==null)throw new AuthorizationException("Authenticated Entra actor is required");}
    private static void validate(long u,long p){if(u<=0||p<=0)throw new IllegalArgumentException("targetUserId and plantId must be positive");}
    private static String normalize(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
