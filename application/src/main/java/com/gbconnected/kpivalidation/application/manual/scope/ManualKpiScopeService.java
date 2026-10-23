package com.gbconnected.kpivalidation.application.manual.scope;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ActorIdentityPort;
import com.gbconnected.kpivalidation.application.port.out.FeatureActivationPort;
import com.gbconnected.kpivalidation.application.port.out.manual.*;
import com.gbconnected.kpivalidation.domain.manual.*;
import java.util.*;

public final class ManualKpiScopeService implements ManualKpiScopeUseCase, AdministerManualKpiScopeUseCase {
    private final ActorIdentityPort identities;
    private final FeatureActivationPort featureActivation;
    private final ManualKpiAuthorizationPort moduleAuthorization;
    private final ManualKpiScopePort scope;
    private final ManualKpiScopeAdministrationPort administration;

    public ManualKpiScopeService(ActorIdentityPort identities, FeatureActivationPort featureActivation,
            ManualKpiAuthorizationPort moduleAuthorization, ManualKpiScopePort scope, ManualKpiScopeAdministrationPort administration) {
        this.identities = Objects.requireNonNull(identities);
        this.featureActivation = Objects.requireNonNull(featureActivation);
        this.moduleAuthorization = Objects.requireNonNull(moduleAuthorization);
        this.scope = Objects.requireNonNull(scope);
        this.administration = Objects.requireNonNull(administration);
    }

    @Override
    public List<ManualKpiPlantScope> myScope(ActorContext actor) {
        requireActor(actor);
        if (!featureActivation.configured() || !moduleAuthorization.configured()) return List.of();
        return scope.findEffectiveScope(actor.tenantId(), actor.objectId(), featureActivation.configurationCode()).stream()
                .filter(p -> moduleAuthorization.authorized(actor.tenantId(), actor.objectId(), p.plantId()))
                .toList();
    }

    @Override
    public ManualKpiScopePolicyView policy() {
        return new ManualKpiScopePolicyView(
                "MULTIPLE_UNBOUNDED_BY_CANONICAL_MODEL",
                "CURRENT_USER_LINE_ACCESS_AND_CURRENT_LINE_PLANT_ASSIGNMENT_AND_MANUAL_KPI_FEATURE_ENABLED",
                "IMMEDIATE_ON_NEXT_SERVER_REQUEST",
                "core.plant.timezone_name",
                scope.source(),
                administration.available(),
                administration.adminCapabilityCode());
    }

    @Override
    public List<ManualKpiPlantAssignment> list(ActorContext actor, long plantId) {
        authorizeAdmin(actor, plantId);
        return administration.list(plantId);
    }

    @Override
    public ManualKpiPlantAssignment assign(ActorContext actor, AssignManualKpiPlantCommand command, String correlationId) {
        Objects.requireNonNull(command, "command is required");
        authorizeAdmin(actor, command.plantId());
        long actorUserId = canonicalUser(actor);
        if (command.targetUserId() <= 0 || command.plantId() <= 0) throw new IllegalArgumentException("targetUserId and plantId must be positive");
        return administration.assign(actorUserId, command.targetUserId(), command.plantId(), normalize(command.reason()), correlationId);
    }

    @Override
    public ManualKpiPlantAssignment revoke(ActorContext actor, RevokeManualKpiPlantCommand command, String correlationId) {
        Objects.requireNonNull(command, "command is required");
        authorizeAdmin(actor, command.plantId());
        long actorUserId = canonicalUser(actor);
        if (command.targetUserId() <= 0 || command.plantId() <= 0) throw new IllegalArgumentException("targetUserId and plantId must be positive");
        return administration.revoke(actorUserId, command.targetUserId(), command.plantId(), required(command.reason(), "reason"), correlationId);
    }

    private void authorizeAdmin(ActorContext actor, long plantId) {
        requireActor(actor);
        if (plantId <= 0) throw new IllegalArgumentException("plantId must be positive");
        if (!administration.available()) throw new AuthorizationException("Manual KPI scope administration is not configured");
        if (!administration.canAdminister(actor.tenantId(), actor.objectId(), plantId)) throw new AuthorizationException("Actor cannot administer Manual KPI plant access for this plant");
    }

    private long canonicalUser(ActorContext actor) {
        ActorIdentitySnapshot i = identities.findByEntraIdentity(actor.tenantId(), actor.objectId())
                .orElseThrow(() -> new AuthorizationException("Actor is not registered in app_security.user_identity"));
        if (!i.active() || i.canonicalUserId() == null) throw new AuthorizationException("Canonical actor is inactive or unresolved");
        return i.canonicalUserId();
    }

    private static void requireActor(ActorContext actor) {
        if (actor == null || actor.tenantId() == null || actor.objectId() == null) throw new AuthorizationException("Authenticated Entra actor is required");
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String required(String value,String field){ if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required"); return value.trim(); }
}
