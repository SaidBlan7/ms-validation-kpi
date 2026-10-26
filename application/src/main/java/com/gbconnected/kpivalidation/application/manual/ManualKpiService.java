package com.gbconnected.kpivalidation.application.manual;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.application.port.out.manual.*;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDatePolicy;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDateSelection;
import com.gbconnected.kpivalidation.domain.manual.ManualStopTimePolicy;
import com.gbconnected.kpivalidation.domain.manual.ManualMassPerformancePolicy;
import com.gbconnected.kpivalidation.domain.manual.ManualWastePolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ManualKpiService implements ManualKpiUseCase {
    private static final int EDIT_HOURS = 8;
    private final ManualKpiDatePolicy datePolicy = new ManualKpiDatePolicy();
    private final ManualStopTimePolicy stopTimePolicy = new ManualStopTimePolicy();
    private final ManualMassPerformancePolicy massPerformancePolicy = new ManualMassPerformancePolicy();
    private final ManualWastePolicy wastePolicy = new ManualWastePolicy();
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
                ManualStopTimePolicy.CAPTURE_UNIT, ManualStopTimePolicy.DAILY_HOURS.intValueExact(), true,
                List.of(ManualMassPerformancePolicy.ELABORATED_VARIABLE, ManualMassPerformancePolicy.DELIVERED_VARIABLE), true,
                "NO_ORDERING_CONSTRAINT_DELIVERED_MAY_EXCEED_ELABORATED", "PRESERVE_DECIMAL_NO_APPLICATION_ROUNDING",
                ManualWastePolicy.WASTE_CAPTURE_UNIT, ManualWastePolicy.PRODUCED_QUANTITY_UNIT, true,
                "PRESERVE_DECIMAL_NO_APPLICATION_ROUNDING",
                "AUTOMATIC_WINS_MANUAL_RETAINED_FOR_TRACEABILITY", "HISTORICAL_READ_ONLY_AFTER_DEACTIVATION",
                activation.configurationCode(), authorization.capabilityCode(), persistence.available(), persistence.source());
    }

    @Override public ManualKpiDateSelection dateSelection(ActorContext actor,long plantId,long lineId,LocalDate businessDate) {
        PlantContext plant = authorizeScope(actor, plantId, lineId, true);
        return datePolicy.evaluate(businessDate, zone(plant.timezoneName()), clock.instant());
    }

    @Override public List<ManualStopClassificationView> stopClassifications(ActorContext actor,long plantId,long lineId) {
        authorizeScope(actor, plantId, lineId, true);
        return references.activeStopClassifications(plantId);
    }

    @Override public List<ManualWasteCatalogEntryView> wasteCatalog(ActorContext actor,long plantId,long lineId) {
        authorizeScope(actor, plantId, lineId, true);
        return references.activeWasteSubclassifications(plantId);
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
        ZoneId zone = zone(plant.timezoneName());
        Instant now = clock.instant();
        long userId = canonicalUser(actor);
        Optional<ManualKpiRecord> existing = persistence.find(command.plantId(), command.lineId(), command.businessDate());
        validatePayload(command, existing.orElse(null));
        if (existing.isEmpty()) {
            if (command.expectedVersion()!=null) throw new ManualKpiConflictException("expectedVersion must be omitted when creating a new Manual KPI record");
            ManualKpiDateSelection selection = datePolicy.evaluate(command.businessDate(), zone, now);
            if (!selection.selectable()) throw new DomainValidationException(selection.message());
            return persistence.create(command, plant.timezoneName(), userId, now, now.plus(Duration.ofHours(EDIT_HOURS)), correlationId);
        }
        // Specs 01/03: the independent 8-hour edit window governs updates after valid creation.
        if (!existing.get().editableAt(now)) throw new DomainValidationException("Manual KPI record is locked because the 8-hour edit window expired");
        // Spec 04: updates are optimistic. The client must write the version it actually read.
        if (command.expectedVersion()==null) throw new ManualKpiConflictException("expectedVersion is required when updating an existing Manual KPI record");
        if (command.expectedVersion().longValue()!=existing.get().version()) {
            throw new ManualKpiConflictException("Manual KPI record changed since it was read; reload it before saving");
        }
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

    private void validatePayload(SaveManualKpiCommand c, ManualKpiRecord existing) {
        if (c.businessDate()==null) throw new IllegalArgumentException("businessDate is required");
        ManualOeeSection oee = c.oee()==null ? new ManualOeeSection(List.of(),null) : c.oee();
        ManualWasteSection waste = c.waste()==null ? new ManualWasteSection(List.of(),null) : c.waste();
        if (oee.empty() && waste.empty()) throw new DomainValidationException("At least one OEE or waste value is required");

        Map<Long,ManualStopClassificationView> allowedStops = new HashMap<>();
        for (ManualStopClassificationView x : references.activeStopClassifications(c.plantId())) allowedStops.put(x.stopClassificationId(), x);
        Set<Long> stopIds = new HashSet<>();
        List<BigDecimal> stopHours = new ArrayList<>();
        for (ManualStopTimeInput x:oee.stopTimes()) {
            if (x==null || x.stopClassificationId()<=0) throw new DomainValidationException("Stop time values must reference a valid stop classification");
            if (!stopIds.add(x.stopClassificationId())) throw new DomainValidationException("Duplicate stop classification in OEE section");
            if (!allowedStops.containsKey(x.stopClassificationId())) throw new DomainValidationException("Unknown, inactive or out-of-scope stop classification: "+x.stopClassificationId());
            stopHours.add(x.hours());
        }
        stopTimePolicy.validate(stopHours);

        if (oee.massPerformance()!=null) {
            massPerformancePolicy.validate(oee.massPerformance().elaboratedMass(), oee.massPerformance().deliveredMass());
        }
        Map<String,ManualWasteCatalogEntryView> activeWaste = new HashMap<>();
        for (ManualWasteCatalogEntryView x : references.activeWasteSubclassifications(c.plantId())) {
            activeWaste.put(wasteKey(x.wasteClassificationId(), x.wasteSubclassificationId()), x);
        }
        Set<String> historicalWaste = new HashSet<>();
        if (existing != null && existing.waste() != null) {
            for (ManualWasteInput x : existing.waste().waste()) {
                historicalWaste.add(wasteKey(x.wasteClassificationId(), x.wasteSubclassificationId()));
            }
        }
        Set<String> wasteKeys = new HashSet<>();
        for (ManualWasteInput x:waste.waste()) {
            if (x==null || x.wasteClassificationId()<=0 || x.wasteSubclassificationId()<=0) {
                throw new DomainValidationException("Waste kilograms must be non-negative and reference a canonical waste subclassification");
            }
            wastePolicy.validateKilograms(x.kilograms());
            String key=wasteKey(x.wasteClassificationId(),x.wasteSubclassificationId());
            if (!wasteKeys.add(key)) throw new DomainValidationException("Duplicate waste subclassification entry");
            boolean currentlyActive = activeWaste.containsKey(key);
            boolean previouslyCaptured = historicalWaste.contains(key)
                    && references.wasteSubclassificationExists(c.plantId(), x.wasteClassificationId(), x.wasteSubclassificationId());
            if (!currentlyActive && !previouslyCaptured) {
                throw new DomainValidationException("Unknown, inactive or out-of-scope waste subclassification: "+x.wasteSubclassificationId());
            }
        }
        wastePolicy.validateProducedTonnes(waste.producedTonnes());
    }
    private static String wasteKey(long classificationId,long subclassificationId){ return classificationId+":"+subclassificationId; }
    private static ZoneId zone(String id){ try{return ZoneId.of(id);}catch(Exception e){throw new DomainValidationException("Plant timezone_name is invalid");} }
}
