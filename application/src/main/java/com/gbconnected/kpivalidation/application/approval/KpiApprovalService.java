package com.gbconnected.kpivalidation.application.approval;
import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.application.port.out.approval.*;
import java.time.*;
import java.util.*;
public final class KpiApprovalService implements KpiApprovalUseCase {
    private final ActorIdentityPort identities; private final AccessScopePort access; private final OperationalContextPort context;
    private final FeatureActivationPort feature; private final KpiApprovalAuthorizationPort authorization;
    private final KpiApprovalPersistencePort persistence; private final Clock clock;
    public KpiApprovalService(ActorIdentityPort identities,AccessScopePort access,OperationalContextPort context,
            FeatureActivationPort feature,KpiApprovalAuthorizationPort authorization,KpiApprovalPersistencePort persistence,Clock clock){
        this.identities=Objects.requireNonNull(identities);this.access=Objects.requireNonNull(access);this.context=Objects.requireNonNull(context);
        this.feature=Objects.requireNonNull(feature);this.authorization=Objects.requireNonNull(authorization);this.persistence=Objects.requireNonNull(persistence);this.clock=Objects.requireNonNull(clock);
    }
    public List<ApprovalValidationView> pending(ActorContext actor){
        long user=actorUser(actor); persistence.expirePending(clock.instant(),null);
        return persistence.pending().stream().filter(v->authorized(actor,user,v)).toList();
    }
    public ApprovalValidationPageView list(ActorContext actor,ApprovalValidationQuery query){
        long user=actorUser(actor); persistence.expirePending(clock.instant(),null);
        ApprovalValidationQuery q=query==null?ApprovalValidationQuery.defaults():query;
        List<ApprovalValidationView> filtered=persistence.all().stream()
                .filter(v->authorized(actor,user,v))
                .filter(v->q.status()==null||q.status().equals(v.status()))
                .filter(v->q.validationDomain()==null||q.validationDomain().equals(v.validationDomain()))
                .filter(v->q.plantId()==null||q.plantId()==v.plantId())
                .filter(v->q.fromDate()==null||!v.businessDate().isBefore(q.fromDate()))
                .filter(v->q.toDate()==null||!v.businessDate().isAfter(q.toDate()))
                .sorted(Comparator.comparing(ApprovalValidationView::proposedAt).reversed()
                        .thenComparing(Comparator.comparingLong(ApprovalValidationView::validationId).reversed()))
                .toList();
        int from=Math.min(q.offset(),filtered.size());
        int to=Math.min(from+q.limit(),filtered.size());
        return new ApprovalValidationPageView(filtered.subList(from,to),q.offset(),q.limit(),filtered.size());
    }
    public ApprovalValidationView get(ActorContext actor,long id){
        if(id<=0)throw new IllegalArgumentException("validationId must be positive"); long user=actorUser(actor);persistence.expirePending(clock.instant(),null);
        ApprovalValidationView v=persistence.find(id).orElseThrow(()->new ApprovalNotFoundException("KPI validation not found")); requireAuthorized(actor,user,v); return v;
    }
    public ApprovalValidationView decide(ActorContext actor,long id,ApprovalDecision decision,String correlationId){
        if(id<=0)throw new IllegalArgumentException("validationId must be positive"); if(decision==null)throw new IllegalArgumentException("decision is required");
        long user=actorUser(actor); persistence.expirePending(clock.instant(),correlationId);
        ApprovalValidationView v=persistence.find(id).orElseThrow(()->new ApprovalNotFoundException("KPI validation not found")); requireAuthorized(actor,user,v);
        if(!"PENDING".equals(v.status())) throw new ApprovalConflictException("Validation is no longer pending");
        if(!clock.instant().isBefore(v.expiresAt()) && !clock.instant().equals(v.expiresAt())) throw new ApprovalConflictException("Validation has expired");
        return persistence.decide(id,user,decision,clock.instant(),correlationId);
    }
    public ApprovalPolicyView policy(){return new ApprovalPolicyView("PERSISTED_KPI_VALIDATION_EXPIRES_AT","ATOMIC_PENDING_STATE_TRANSITION",
            "FINAL_NO_REVERSAL_IN_THIS_FLOW",false,"SUPPORTED_CANONICAL_KPI_VALIDATION","SUPPORTED_VIA_KPI_VALIDATION_METRIC_SOURCE_DOMAIN_WASTE",
            "APPROVED_USES_PROPOSED_OTHER_STATUSES_USE_ORIGINAL");}
    private long actorUser(ActorContext actor){if(actor==null||actor.tenantId()==null||actor.objectId()==null)throw new SecurityException("Authenticated Entra actor is required");
        ActorIdentitySnapshot s=identities.findByEntraIdentity(actor.tenantId(),actor.objectId()).orElseThrow(()->new AuthorizationException("Canonical user identity is required"));
        if(!s.active() || s.canonicalUserId()==null)throw new AuthorizationException("Canonical user is inactive or unresolved");return s.canonicalUserId();}
    private boolean authorized(ActorContext actor,long user,ApprovalValidationView v){
        return feature.enabledForPlant(v.plantId()) && access.hasPlantAccess(actor.tenantId(),actor.objectId(),v.plantId()) &&
                (v.lineId()==null||context.lineBelongsToPlant(v.lineId(),v.plantId())) && authorization.authorized(actor,user,v.plantId());}
    private void requireAuthorized(ActorContext actor,long user,ApprovalValidationView v){if(!authorized(actor,user,v))throw new AuthorizationException("Actor is not authorized for KPI approval in this plant");}
}
