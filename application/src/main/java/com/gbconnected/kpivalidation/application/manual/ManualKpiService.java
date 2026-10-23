package com.gbconnected.kpivalidation.application.manual;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.application.port.out.manual.*;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDatePolicy;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDateSelection;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ManualKpiService implements ManualKpiUseCase {
    private static final int EDIT_HOURS = 8;
    private final ManualKpiDatePolicy datePolicy = new ManualKpiDatePolicy();
    private final AccessScopePort access;
    private final OperationalContextPort context;
    private final ActorIdentityPort identities;
    private final FeatureActivationPort activation;
    private final ManualKpiAuthorizationPort authorization;
    private final ManualKpiReferencePort references;
    private final ManualKpiPersistencePort persistence;
    private final Clock clock;

    public ManualKpiService(AccessScopePort access, OperationalContextPort context, ActorIdentityPort identities,
            FeatureActivationPort activation, ManualKpiAuthorizationPort authorization, ManualKpiReferencePort references,
            ManualKpiPersistencePort persistence, Clock clock) {
        this.access=access; this.context=context; this.identities=identities; this.activation=activation;
        this.authorization=authorization; this.references=references; this.persistence=persistence; this.clock=clock;
    }

    @Override public ManualKpiPolicyView policy() {
        return new ManualKpiPolicyView(ManualKpiDatePolicy.HISTORY_HOURS, EDIT_HOURS, ManualKpiDatePolicy.OEE_PERIOD_HOURS,
                "PLANT_LOCAL_DATE_START_WITHIN_EXACT_ROLLING_96_HOURS", true, true, true, false,
                "FIXED_24_HOUR_INTERVAL_FROM_PLANT_LOCAL_DAY_START", "ABSOLUTE_8_HOURS_FROM_FIRST_SAVE", true,
                "AUTOMATIC_WINS_MANUAL_RETAINED_FOR_TRACEABILITY", "HISTORICAL_READ_ONLY_AFTER_DEACTIVATION",
                activation.configurationCode(), authorization.capabilityCode(), persistence.available(), persistence.source());
    }

    @Override public ManualKpiDateSelection dateSelection(ActorContext actor,long plantId,long lineId,LocalDate businessDate) {
        PlantContext plant = authorizeScope(actor, plantId, lineId, true);
        return datePolicy.evaluate(businessDate, zone(plant.timezoneName()), clock.instant());
    }

    @Override public ManualKpiRecord get(ActorContext actor,long plantId,long lineId,LocalDate businessDate) {
        authorizeScope(actor, plantId, lineId, false);
        if (!persistence.available()) throw new ManualKpiStorageUnavailableException("Canonical Manual KPI persistence is not available");
        return persistence.find(plantId,lineId,businessDate).orElseThrow(() -> new ManualKpiNotFoundException("Manual KPI record was not found"));
    }

    @Override public ManualKpiRecord save(ActorContext actor, SaveManualKpiCommand command, String correlationId) {
        Objects.requireNonNull(command, "command is required");
        PlantContext plant = authorizeScope(actor, command.plantId(), command.lineId(), true);
        if (!persistence.available()) throw new ManualKpiStorageUnavailableException(
                "Canonical database has no Manual KPI business entity. Data must provide canonical persistence before PostgreSQL writes can be enabled.");
        validatePayload(command);
        ZoneId zone = zone(plant.timezoneName());
        Instant now = clock.instant();
        long userId = canonicalUser(actor);
        Optional<ManualKpiRecord> existing = persistence.find(command.plantId(), command.lineId(), command.businessDate());
        if (existing.isEmpty()) {
            ManualKpiDateSelection selection = datePolicy.evaluate(command.businessDate(), zone, now);
            if (!selection.selectable()) throw new DomainValidationException(selection.message());
            return persistence.create(command, plant.timezoneName(), userId, now, now.plus(Duration.ofHours(EDIT_HOURS)), correlationId);
        }
        // Spec 03: once created while selectable, the independent 8-hour edit window governs updates.
        // Crossing the 96-hour selection boundary does not truncate an already-open edit window.
        if (!existing.get().editableAt(now)) throw new DomainValidationException("Manual KPI record is locked because the 8-hour edit window expired");
        return persistence.update(existing.get(), command, userId, now, correlationId);
    }

    private PlantContext authorizeScope(ActorContext actor,long plantId,long lineId,boolean requireActiveFeature) {
        if (actor==null || actor.tenantId()==null || actor.objectId()==null) throw new AuthorizationException("Authenticated Entra actor is required");
        if (plantId<=0 || lineId<=0) throw new IllegalArgumentException("plantId and lineId must be positive");
        if (!access.hasPlantAccess(actor.tenantId(), actor.objectId(), plantId)) throw new AuthorizationException("Actor has no plant access");
        if (!access.hasLineAccess(actor.tenantId(), actor.objectId(), lineId)) throw new AuthorizationException("Actor has no line access");
        PlantContext plant = context.findPlant(plantId).orElseThrow(() -> new IllegalArgumentException("Plant was not found"));
        if (!plant.active()) throw new DomainValidationException("Plant is inactive");
        if (!context.lineBelongsToPlant(lineId,plantId)) throw new DomainValidationException("Line does not belong to plant");
        if (!authorization.configured()) throw new AuthorizationException("Manual KPI authorization capability is not configured");
        if (!authorization.authorized(actor.tenantId(),actor.objectId(),plantId)) throw new AuthorizationException("Actor is not authorized for Manual KPI capture");
        if (requireActiveFeature) {
            if (!activation.configured()) throw new DomainValidationException("Manual KPI feature configuration is not configured");
            if (!activation.enabledForScope(plantId,lineId)) throw new DomainValidationException("Manual KPI module is disabled for the requested plant/line");
        }
        return plant;
    }

    private long canonicalUser(ActorContext actor) {
        ActorIdentitySnapshot i = identities.findByEntraIdentity(actor.tenantId(),actor.objectId())
                .orElseThrow(() -> new AuthorizationException("Actor is not registered in app_security.user_identity"));
        if (!i.active() || i.canonicalUserId()==null) throw new AuthorizationException("Canonical actor is inactive or unresolved");
        return i.canonicalUserId();
    }

    private void validatePayload(SaveManualKpiCommand c) {
        if (c.businessDate()==null) throw new IllegalArgumentException("businessDate is required");
        ManualOeeSection oee = c.oee()==null ? new ManualOeeSection(List.of(),List.of()) : c.oee();
        ManualWasteSection waste = c.waste()==null ? new ManualWasteSection(List.of(),null) : c.waste();
        if (oee.empty() && waste.empty()) throw new DomainValidationException("At least one OEE or waste value is required");
        long seconds = 0;
        Set<Long> stopIds = new HashSet<>();
        for (ManualStopTimeInput x:oee.stopTimes()) {
            if (x.stopClassificationId()<=0 || x.seconds()<0) throw new DomainValidationException("Stop time values must use a valid classification and non-negative seconds");
            if (!stopIds.add(x.stopClassificationId())) throw new DomainValidationException("Duplicate stop classification in OEE section");
            if (!references.activeStopClassification(x.stopClassificationId())) throw new DomainValidationException("Unknown or inactive stop classification: "+x.stopClassificationId());
            seconds = Math.addExact(seconds,x.seconds());
        }
        if (seconds>86400) throw new DomainValidationException("Total daily stop time cannot exceed 86400 seconds");
        Set<Long> metricIds = new HashSet<>();
        for (ManualPerformanceInput x:oee.performance()) {
            if (x.metricDefinitionId()<=0 || x.uomId()<=0 || negative(x.value())) throw new DomainValidationException("Performance values must be non-negative and reference metric/UOM");
            if (!metricIds.add(x.metricDefinitionId())) throw new DomainValidationException("Duplicate performance metric in OEE section");
            if (!references.activeMetricDefinition(x.metricDefinitionId()) || !references.activeUom(x.uomId())) throw new DomainValidationException("Unknown or inactive metric/UOM in OEE section");
        }
        Set<String> wasteKeys = new HashSet<>();
        for (ManualWasteInput x:waste.waste()) {
            if (x.wasteClassificationId()<=0 || negative(x.kilograms())) throw new DomainValidationException("Waste kilograms must be non-negative and reference a classification");
            String key=x.wasteClassificationId()+":"+x.wasteSubclassificationId();
            if (!wasteKeys.add(key)) throw new DomainValidationException("Duplicate waste classification/subclassification entry");
            if (!references.validWasteClassification(x.wasteClassificationId(),x.wasteSubclassificationId())) throw new DomainValidationException("Unknown or inactive waste classification/subclassification");
        }
        if (waste.producedTonnes()!=null && waste.producedTonnes().signum()<0) throw new DomainValidationException("producedTonnes must be non-negative");
    }
    private static boolean negative(BigDecimal v){ return v==null || v.signum()<0; }
    private static ZoneId zone(String id){ try{return ZoneId.of(id);}catch(Exception e){throw new DomainValidationException("Plant timezone_name is invalid");} }
}
