package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.application.ValidationIssue.Severity;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.domain.*;
import java.time.*;
import java.util.*;

public final class OeeValidationService implements CheckEligibilityUseCase, PrevalidateOeeCorrectionUseCase,
        CapabilitiesUseCase, GetMyValidatorAssignmentsUseCase, GetTemporalContextUseCase,
        GetStopTimeReferenceUseCase, PrevalidateStopTimeCorrectionUseCase,
        GetMassPerformanceReferenceUseCase, PrevalidateMassPerformanceCorrectionUseCase,
        GetJustificationPolicyUseCase, GetAuditPolicyUseCase, GetWorkflowPolicyUseCase, GetOeeMetricReferenceUseCase,
        GetValidatorAssignmentPolicyUseCase, GetStopTimePolicyUseCase, GetMassPerformancePolicyUseCase {

    private static final String DATE_POLICY_WARNING_CODE = "OEE_BUSINESS_DAY_CUTOFF_PROVISIONAL";

    private final AccessScopePort accessScope;
    private final OperationalContextPort context;
    private final ValidatorAssignmentPort validatorAssignments;
    private final StopTimeReferencePort stopTimeReference;
    private final MassPerformanceReferencePort massPerformanceReference;
    private final OeeMetricSourcePort oeeMetricSource;
    private final BusinessDatePolicy businessDatePolicy;
    private final Clock clock;
    private final String requiredValidatorRole;

    public OeeValidationService(AccessScopePort accessScope,
            OperationalContextPort context,
            ValidatorAssignmentPort validatorAssignments,
            StopTimeReferencePort stopTimeReference,
            MassPerformanceReferencePort massPerformanceReference,
            OeeMetricSourcePort oeeMetricSource,
            BusinessDatePolicy businessDatePolicy,
            Clock clock,
            String requiredValidatorRole) {
        this.accessScope = Objects.requireNonNull(accessScope);
        this.context = Objects.requireNonNull(context);
        this.validatorAssignments = Objects.requireNonNull(validatorAssignments);
        this.stopTimeReference = Objects.requireNonNull(stopTimeReference);
        this.massPerformanceReference = Objects.requireNonNull(massPerformanceReference);
        this.oeeMetricSource = Objects.requireNonNull(oeeMetricSource);
        this.businessDatePolicy = Objects.requireNonNull(businessDatePolicy);
        this.clock = Objects.requireNonNull(clock);
        this.requiredValidatorRole = requiredValidatorRole == null ? "" : requiredValidatorRole.trim();
    }

    @Override
    public EligibilityResult check(ActorContext actor, long plantId, long lineId, LocalDate businessDate) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (plantId <= 0) issues.add(ValidationIssue.blocker("INVALID_PLANT_ID", "plantId must be positive"));
        if (lineId <= 0) issues.add(ValidationIssue.blocker("INVALID_LINE_ID", "lineId must be positive"));
        if (businessDate == null) issues.add(ValidationIssue.blocker("BUSINESS_DATE_REQUIRED", "businessDate is required"));
        if (actor == null || actor.tenantId() == null || actor.objectId() == null) {
            issues.add(ValidationIssue.blocker("AUTHENTICATED_ACTOR_REQUIRED", "Authenticated Entra actor is required"));
            return result(false, plantId, lineId, businessDate, null, null, issues);
        }

        validateModuleEntitlement(actor, issues);
        validateSpecificKpiAssignment(actor, plantId, issues);

        // Configuration blockers must not leak plant/line metadata.
        if (issues.stream().anyMatch(i -> i.severity() == Severity.BLOCKER)) {
            return result(false, plantId, lineId, businessDate, null, null, issues);
        }

        if (plantId > 0 && !accessScope.hasPlantAccess(actor.tenantId(), actor.objectId(), plantId)) {
            throw new AuthorizationException("Actor is not authorized for the requested KPI plant scope");
        }

        Optional<PlantContext> plant = plantId > 0 ? context.findPlant(plantId) : Optional.empty();
        if (plant.isEmpty()) {
            issues.add(ValidationIssue.blocker("PLANT_NOT_FOUND", "Plant was not found"));
            return result(false, plantId, lineId, businessDate, null, null, issues);
        }
        if (!plant.get().active()) issues.add(ValidationIssue.blocker("PLANT_INACTIVE", "Plant is inactive"));
        if (lineId > 0 && !context.lineBelongsToPlant(lineId, plantId)) {
            issues.add(ValidationIssue.blocker("LINE_OUTSIDE_PLANT", "Line does not belong to the requested plant"));
        }

        // Recalculated for every request. A stale browser/API request cannot keep yesterday's eligibility
        // after the plant crosses midnight.
        ZoneId plantZone = resolvePlantZone(plant.get(), issues);
        LocalDate expected = plantZone == null ? null : businessDatePolicy.editableBusinessDate(plant.get(), clock);
        if (businessDate != null && expected != null && !businessDate.equals(expected)) {
            issues.add(ValidationIssue.blocker("ONLY_PREVIOUS_DAY_ALLOWED",
                    "Only the immediately previous local calendar day is eligible for validation; refresh temporal-context when the plant crosses midnight"));
        }
        addDatePolicyWarning(issues);

        boolean eligible = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return result(eligible, plantId, lineId, businessDate, expected, plant.get().timezoneName(), issues);
    }

    @Override
    public TemporalContextResult get(ActorContext actor, long plantId, long lineId) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (plantId <= 0) issues.add(ValidationIssue.blocker("INVALID_PLANT_ID", "plantId must be positive"));
        if (lineId <= 0) issues.add(ValidationIssue.blocker("INVALID_LINE_ID", "lineId must be positive"));
        if (actor == null || actor.tenantId() == null || actor.objectId() == null) {
            issues.add(ValidationIssue.blocker("AUTHENTICATED_ACTOR_REQUIRED", "Authenticated Entra actor is required"));
            return temporalResult(plantId, lineId, null, null, null, issues);
        }

        validateModuleEntitlement(actor, issues);
        validateSpecificKpiAssignment(actor, plantId, issues);
        if (issues.stream().anyMatch(i -> i.severity() == Severity.BLOCKER)) {
            return temporalResult(plantId, lineId, null, null, null, issues);
        }

        if (plantId > 0 && !accessScope.hasPlantAccess(actor.tenantId(), actor.objectId(), plantId)) {
            throw new AuthorizationException("Actor is not authorized for the requested KPI plant scope");
        }

        Optional<PlantContext> plant = plantId > 0 ? context.findPlant(plantId) : Optional.empty();
        if (plant.isEmpty()) {
            issues.add(ValidationIssue.blocker("PLANT_NOT_FOUND", "Plant was not found"));
            return temporalResult(plantId, lineId, null, null, null, issues);
        }
        if (!plant.get().active()) issues.add(ValidationIssue.blocker("PLANT_INACTIVE", "Plant is inactive"));
        if (lineId > 0 && !context.lineBelongsToPlant(lineId, plantId)) {
            issues.add(ValidationIssue.blocker("LINE_OUTSIDE_PLANT", "Line does not belong to the requested plant"));
        }

        ZoneId zone = resolvePlantZone(plant.get(), issues);
        if (zone == null) {
            return temporalResult(plantId, lineId, plant.get(), null, null, issues);
        }
        ZonedDateTime plantNow = ZonedDateTime.now(clock.withZone(zone));
        LocalDate eligibleDate = businessDatePolicy.editableBusinessDate(plant.get(), clock);
        addDatePolicyWarning(issues);
        return temporalResult(plantId, lineId, plant.get(), plantNow, eligibleDate, issues);
    }

    private TemporalContextResult temporalResult(long plantId, long lineId, PlantContext plant,
            ZonedDateTime plantNow, LocalDate eligibleDate, List<ValidationIssue> issues) {
        ZonedDateTime windowStart = null;
        ZonedDateTime windowEnd = null;
        if (plantNow != null) {
            ZoneId zone = plantNow.getZone();
            windowStart = plantNow.toLocalDate().atStartOfDay(zone);
            windowEnd = plantNow.toLocalDate().plusDays(1).atStartOfDay(zone);
        }
        return new TemporalContextResult(
                plantId,
                lineId,
                plant == null ? null : plant.timezoneName(),
                plantNow == null ? null : plantNow.toLocalDate(),
                plantNow == null ? null : plantNow.toOffsetDateTime().toString(),
                plantNow == null ? null : plantNow.toInstant().toString(),
                eligibleDate,
                windowStart == null ? null : windowStart.toOffsetDateTime().toString(),
                windowEnd == null ? null : windowEnd.toOffsetDateTime().toString(),
                "SINGLE_DATE_ONLY",
                "PREVIOUS_LOCAL_CALENDAR_DAY",
                "LOCAL_CALENDAR_OPERATION_DAY",
                false,
                false,
                true,
                "SERVER_RECALCULATES_D_MINUS_1_AND_REJECTS_STALE_DATE",
                "PENDING_FUNCTIONAL_DEFINITION",
                "PROVISIONAL_PLANT_TIMEZONE",
                List.copyOf(issues));
    }

    private ZoneId resolvePlantZone(PlantContext plant, List<ValidationIssue> issues) {
        String timezone = plant == null ? null : plant.timezoneName();
        if (timezone == null || timezone.isBlank()) {
            issues.add(ValidationIssue.blocker("PLANT_TIMEZONE_NOT_CONFIGURED",
                    "Plant timezone_name is required to calculate the Spec 03 D-1 window"));
            return null;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            issues.add(ValidationIssue.blocker("INVALID_PLANT_TIMEZONE_CONFIGURATION",
                    "Plant timezone_name is not a valid IANA timezone and D-1 cannot be calculated safely"));
            return null;
        }
    }

    private void addDatePolicyWarning(List<ValidationIssue> issues) {
        issues.add(ValidationIssue.warning(DATE_POLICY_WARNING_CODE,
                "Spec 03 confirms D-1 including weekends/holidays, but the official OEE cutoff, exact 24-hour interpretation and authoritative timezone remain pending; current policy uses core.plant.timezone_name and the previous local calendar date"));
    }

    private void validateModuleEntitlement(ActorContext actor, List<ValidationIssue> issues) {
        // Canonical v1.2 production authorization is resolved from app_security by the
        // ValidatorAssignmentPort. Local/external-config mode retains the token-role gate.
        if (validatorAssignments.enforcesRoleCapability()) {
            if (!validatorAssignments.configured()) {
                issues.add(ValidationIssue.blocker("VALIDATOR_ROLE_CAPABILITY_NOT_CONFIGURED",
                        "Canonical app_security must contain active role " + validatorAssignments.roleCode()
                                + " mapped to active capability " + validatorAssignments.capabilityCode()));
            }
            return;
        }
        if (requiredValidatorRole.isBlank()) {
            issues.add(ValidationIssue.blocker("VALIDATOR_ENTITLEMENT_NOT_CONFIGURED",
                    "GBC_KPI_VALIDATOR_ROLE must identify the local/external validator role"));
        } else if (!actor.hasRole(requiredValidatorRole)) {
            throw new AuthorizationException("Actor is not authorized for KPI OEE validation");
        }
    }

    private void validateSpecificKpiAssignment(ActorContext actor, long plantId, List<ValidationIssue> issues) {
        if (!validatorAssignments.configured()) {
            issues.add(ValidationIssue.blocker("KPI_ASSIGNMENT_SOURCE_NOT_CONFIGURED",
                    validatorAssignments.enforcesRoleCapability()
                            ? "Canonical KPI validator role/capability reference data is not configured"
                            : "GBC_KPI_VALIDATOR_ASSIGNMENTS must define the KPI-specific plant assignments"));
            return;
        }
        if (plantId > 0 && !validatorAssignments.isAssigned(actor.tenantId(), actor.objectId(), plantId)) {
            throw new AuthorizationException("Actor is not authorized for the requested KPI plant scope");
        }
        if (plantId > 0 && validatorAssignments.assignedValidatorCount(plantId) > 2) {
            issues.add(ValidationIssue.blocker("MAX_KPI_VALIDATORS_PER_PLANT_EXCEEDED",
                    "Plant has more than the Spec 02 maximum of two effective KPI OEE validators"));
        }
    }

    private EligibilityResult result(boolean eligible, long plantId, long lineId, LocalDate businessDate,
            LocalDate expected, String timezone, List<ValidationIssue> issues) {
        boolean entitlementConfigured = validatorAssignments.enforcesRoleCapability()
                ? validatorAssignments.configured()
                : !requiredValidatorRole.isBlank();
        return new EligibilityResult(eligible, plantId, lineId, businessDate, expected, timezone,
                entitlementConfigured, validatorAssignments.configured(), List.copyOf(issues));
    }

    @Override
    public ValidatorAssignmentSummary get(ActorContext actor) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (actor == null || actor.tenantId() == null || actor.objectId() == null) {
            issues.add(ValidationIssue.blocker("AUTHENTICATED_ACTOR_REQUIRED", "Authenticated Entra actor is required"));
            return new ValidatorAssignmentSummary(false, List.of(), List.of(), 2, validatorAssignments.source(),
                    validatorAssignments.enforcesRoleCapability() ? validatorAssignments.roleCode() : requiredValidatorRole,
                    validatorAssignments.enforcesRoleCapability() ? validatorAssignments.capabilityCode() : null,
                    List.copyOf(issues));
        }
        validateModuleEntitlement(actor, issues);
        if (!validatorAssignments.configured()) {
            issues.add(ValidationIssue.blocker("KPI_ASSIGNMENT_SOURCE_NOT_CONFIGURED",
                    "KPI validator assignments are not configured"));
        }
        List<Long> plants = validatorAssignments.assignedPlants(actor.tenantId(), actor.objectId()).stream()
                .filter(plantId -> accessScope.hasPlantAccess(actor.tenantId(), actor.objectId(), plantId))
                .sorted()
                .toList();
        List<ValidatorPlantAssignmentView> plantAssignments = new ArrayList<>();
        for (Long plantId : plants) {
            int effectiveCount = validatorAssignments.assignedValidatorCount(plantId);
            boolean overLimit = effectiveCount > 2;
            plantAssignments.add(new ValidatorPlantAssignmentView(
                    plantId, effectiveCount, 2, Math.max(0, 2 - effectiveCount), overLimit));
            if (overLimit) {
                issues.add(ValidationIssue.blocker("MAX_KPI_VALIDATORS_PER_PLANT_EXCEEDED",
                        "Plant " + plantId + " has more than the Spec 02 maximum of two effective KPI OEE validators"));
            }
        }
        if (plants.isEmpty() && validatorAssignments.configured()) {
            issues.add(ValidationIssue.blocker("NO_KPI_PLANT_ASSIGNMENT",
                    "Actor has no explicit KPI OEE plant assignment"));
        }
        boolean enabled = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new ValidatorAssignmentSummary(enabled, plants, List.copyOf(plantAssignments), 2,
                validatorAssignments.source(),
                validatorAssignments.enforcesRoleCapability() ? validatorAssignments.roleCode() : requiredValidatorRole,
                validatorAssignments.enforcesRoleCapability() ? validatorAssignments.capabilityCode() : null,
                List.copyOf(issues));
    }

    @Override
    public ValidatorAssignmentPolicyView getValidatorAssignmentPolicy() {
        boolean canonical = validatorAssignments.enforcesRoleCapability();
        return new ValidatorAssignmentPolicyView(
                canonical ? validatorAssignments.roleCode() : requiredValidatorRole,
                canonical ? validatorAssignments.capabilityCode() : null,
                2,
                "PLANT_SPECIFIC_KPI_OEE_PERMISSION",
                "CURRENT_GENERIC_USER_PLANT_ACCESS_REQUIRED",
                "EXTERNAL_GLOBAL_SUPPLY_CHAIN_APPROVAL",
                canonical ? "EXTERNAL_ADMIN_WRITES_CANONICAL_APP_SECURITY" : "LOCAL_FIXTURE_CONFIGURATION",
                canonical ? "OPTIONAL_VALID_TO_NULL_MEANS_INDEFINITE" : "CONFIGURATION_LIFETIME",
                "MULTIPLE_PLANTS_SUPPORTED_NO_GLOBAL_LIMIT",
                canonical
                        ? "DETECT_AND_BLOCK_INVALID_PLANT_AT_USE_ADMIN_WRITE_PREVENTION_EXTERNAL"
                        : "LOCAL_CONFIGURATION_REJECTS_MORE_THAN_TWO",
                canonical ? "CLOSE_USER_ROLE_ASSIGNMENT_VALID_TO" : "REMOVE_LOCAL_ASSIGNMENT_AND_RELOAD",
                canonical
                        ? "PARTIAL_CANONICAL_HISTORY_ASSIGNED_BY_REASON_AND_VALIDITY_NO_DEDICATED_REVOKED_BY_FIELD"
                        : "NO_PERSISTENT_ASSIGNMENT_AUDIT",
                "PENDING_DEFINITION_AND_VALIDATION_PERSISTENCE",
                canonical
                        ? "NOT_TRANSACTIONALLY_PREVENTED_BY_THIS_READ_ONLY_MICRO_POST_WRITE_OVERLIMIT_IS_BLOCKED"
                        : "STARTUP_CONFIGURATION_VALIDATION");
    }

    @Override
    public OeeMetricReferenceResult getOeeMetricReference(ActorContext actor, long plantId, long lineId,
            LocalDate businessDate, String metricCode) {
        EligibilityResult eligibility = check(actor, plantId, lineId, businessDate);
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());
        if (metricCode == null || metricCode.isBlank()) {
            issues.add(ValidationIssue.blocker("METRIC_CODE_REQUIRED",
                    "metricCode must match the canonical kpi.kpi_definition.kpi_code"));
            return new OeeMetricReferenceResult(plantId, lineId, businessDate, metricCode, null, null,
                    "CANONICAL_KPI_RESULT_NOT_RESOLVED", List.copyOf(issues));
        }
        if (!eligibility.eligible()) {
            return new OeeMetricReferenceResult(plantId, lineId, businessDate, metricCode.trim(), null, null,
                    "CANONICAL_KPI_RESULT_NOT_RESOLVED", List.copyOf(issues));
        }

        ZoneId zone = ZoneId.of(eligibility.plantTimezone());
        Optional<OeeMetricSnapshot> metric = oeeMetricSource.findMetric(plantId, lineId, businessDate, zone, metricCode);
        if (metric.isEmpty()) {
            issues.add(ValidationIssue.blocker("OEE_ORIGINAL_VALUE_NOT_FOUND",
                    "No canonical daily line-level KPI result matches metricCode and the provisional local D-1 window"));
            return new OeeMetricReferenceResult(plantId, lineId, businessDate, metricCode.trim(), null, null,
                    "CANONICAL_KPI_RESULT_NOT_FOUND", List.copyOf(issues));
        }
        return new OeeMetricReferenceResult(plantId, lineId, businessDate, metric.get().metricCode(),
                metric.get().value(), metric.get().sourceRecordKey(), oeeMetricSource.source(), List.copyOf(issues));
    }

    @Override
    public PrevalidationResult prevalidate(ActorContext actor, PrevalidateOeeCorrectionCommand command) {
        Objects.requireNonNull(command, "command");
        // Spec 03: date is recalculated on every operation, not trusted from an open/stale screen.
        EligibilityResult eligibility = check(actor, command.plantId(), command.lineId(), command.businessDate());
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());
        if (command.metricCode() == null || command.metricCode().isBlank()) {
            issues.add(ValidationIssue.blocker("METRIC_CODE_REQUIRED",
                    "metricCode must match the canonical kpi.kpi_definition.kpi_code"));
        }
        if (command.proposedValue() == null) {
            issues.add(ValidationIssue.blocker("PROPOSED_VALUE_REQUIRED", "proposedValue is required"));
        }
        CorrectionJustificationView justification = validateAndNormalizeJustification(
                command.reasonType(), command.incidentNumber(), command.operationalCause(), command.actionPlan(), issues);

        OeeMetricSnapshot metric = null;
        if (eligibility.eligible() && command.metricCode() != null && !command.metricCode().isBlank()) {
            ZoneId zone = ZoneId.of(eligibility.plantTimezone());
            metric = oeeMetricSource.findMetric(command.plantId(), command.lineId(), command.businessDate(), zone,
                    command.metricCode()).orElse(null);
            if (metric == null) {
                issues.add(ValidationIssue.blocker("OEE_ORIGINAL_VALUE_NOT_FOUND",
                        "No canonical daily line-level KPI result matches metricCode and the provisional local D-1 window"));
            }
        }
        issues.add(ValidationIssue.warning("VALIDATION_PERSISTENCE_NOT_CONNECTED",
                "Proposal persistence and PENDING_APPROVAL workflow are pending the KPI persistence contract"));
        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new PrevalidationResult(valid, false, command.plantId(), command.lineId(), command.businessDate(),
                command.metricCode(), metric == null ? null : metric.value(), command.proposedValue(), justification,
                auditEvidence(actor), metric == null ? null : metric.sourceRecordKey(),
                metric == null ? "CANONICAL_KPI_RESULT_NOT_FOUND" : oeeMetricSource.source(),
                ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "PENDING_EXTERNAL_INTEGRATION", List.copyOf(issues));
    }

    @Override
    public StopTimeReferenceResult get(ActorContext actor, long plantId, long lineId, LocalDate businessDate) {
        EligibilityResult eligibility = check(actor, plantId, lineId, businessDate);
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());
        if (!eligibility.eligible()) {
            return new StopTimeReferenceResult(plantId, lineId, businessDate, eligibility.plantTimezone(),
                    stopTimeReference.source(), stopTimeReference.programmingTypeState(), List.of(), List.copyOf(issues));
        }

        ZoneId zone = ZoneId.of(eligibility.plantTimezone());
        List<StopTimeReferenceEntry> entries = stopTimeReference.findObservedTimes(plantId, lineId, businessDate, zone);
        boolean hasUnmappedProgrammingType = entries.stream().anyMatch(entry -> entry.programmingType() == null);
        String programmingState = hasUnmappedProgrammingType
                ? "MAPPING_INCOMPLETE"
                : stopTimeReference.programmingTypeState();

        issues.add(ValidationIssue.warning("STOP_TIME_REFERENCE_DERIVED_FROM_CANONICAL_STOPS",
                "Times are reconstructed read-only from canonical v1.2 stops.stop_event/stop_fragment and the latest classification_history record for each stop event; the exact OEE calculation contract remains external"));
        if (hasUnmappedProgrammingType) {
            issues.add(ValidationIssue.warning("PROGRAMMING_TYPE_MAPPING_INCOMPLETE",
                    "At least one observed stop classification has no PROGRAMMED/UNPROGRAMMED mapping. Configure "
                            + stopTimeReference.programmingTypeConfigurationKey() + " using classification_code=PROGRAMMED|UNPROGRAMMED"));
        }
        issues.add(ValidationIssue.warning("LINE_CLASSIFICATION_APPLICABILITY_FALLBACK",
                "The canonical model has no line-to-stop-classification relation; active global/plant-organization classifications are exposed as the available catalog (including zero values), and currently inactive classifications are retained only when observed in D-1"));
        return new StopTimeReferenceResult(plantId, lineId, businessDate, eligibility.plantTimezone(),
                stopTimeReference.source(), programmingState, entries, List.copyOf(issues));
    }

    @Override
    public StopTimeCorrectionPrevalidationResult prevalidate(ActorContext actor, PrevalidateStopTimeCorrectionCommand command) {
        Objects.requireNonNull(command, "command");
        EligibilityResult eligibility = check(actor, command.plantId(), command.lineId(), command.businessDate());
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());
        StopTimeReferenceEntry observed = null;

        if (command.stopClassificationId() <= 0) {
            issues.add(ValidationIssue.blocker("INVALID_STOP_CLASSIFICATION_ID",
                    "stopClassificationId must be positive"));
        } else if (eligibility.eligible()) {
            if (!stopTimeReference.classificationExistsInPlantScope(command.plantId(), command.stopClassificationId())) {
                issues.add(ValidationIssue.blocker("STOP_CLASSIFICATION_OUTSIDE_SCOPE",
                        "stopClassificationId does not belong to the plant organization/global canonical stop catalog"));
            } else {
                ZoneId zone = ZoneId.of(eligibility.plantTimezone());
                observed = stopTimeReference.findObservedTime(command.plantId(), command.lineId(),
                        command.businessDate(), zone, command.stopClassificationId()).orElse(null);
                if (observed == null) {
                    issues.add(ValidationIssue.blocker("STOP_CLASSIFICATION_NOT_AVAILABLE_FOR_DATE",
                            "The classification is neither active in the plant organization/global catalog nor observed in the selected D-1 data"));
                }
            }
        }
        if (command.originalSeconds() == null) {
            issues.add(ValidationIssue.blocker("ORIGINAL_SECONDS_REQUIRED",
                    "originalSeconds is required so the proposal preserves the value presented to the validator"));
        } else if (command.originalSeconds() < 0) {
            issues.add(ValidationIssue.blocker("ORIGINAL_SECONDS_NEGATIVE", "originalSeconds cannot be negative"));
        }
        if (command.proposedSeconds() == null) {
            issues.add(ValidationIssue.blocker("PROPOSED_SECONDS_REQUIRED", "proposedSeconds is required"));
        } else if (command.proposedSeconds() < 0) {
            issues.add(ValidationIssue.blocker("PROPOSED_SECONDS_NEGATIVE", "proposedSeconds cannot be negative"));
        }

        Boolean originalMatchesReference = null;
        if (observed != null && command.originalSeconds() != null && command.originalSeconds() >= 0) {
            originalMatchesReference = command.originalSeconds().longValue() == observed.recordedLineDowntimeSeconds();
            if (!originalMatchesReference) {
                issues.add(ValidationIssue.blocker("ORIGINAL_STOP_TIME_CHANGED",
                        "originalSeconds no longer matches the server-observed canonical D-1 total; refresh the reference before proposing a correction"));
            }
            if (!observed.classificationActive()) {
                issues.add(ValidationIssue.warning("STOP_CLASSIFICATION_CURRENTLY_INACTIVE",
                        "The classification was observed in the selected D-1 data but is currently inactive; it remains visible/prevalidatable for historical reconciliation"));
            }
            if (observed.programmingType() == null) {
                issues.add(ValidationIssue.warning("PROGRAMMING_TYPE_MAPPING_MISSING_FOR_CLASSIFICATION",
                        "The selected classification has no PROGRAMMED/UNPROGRAMMED mapping in "
                                + stopTimeReference.programmingTypeConfigurationKey()));
            }
        }

        CorrectionJustificationView justification = validateAndNormalizeJustification(
                command.reasonType(), command.incidentNumber(), command.operationalCause(), command.actionPlan(), issues);

        issues.add(ValidationIssue.warning("TOTAL_TIME_RULE_PENDING_DEFINITION",
                "No 24-hour or cross-classification sum rule is applied because Spec 04 explicitly leaves the exact mathematical consistency rule undefined"));
        issues.add(ValidationIssue.warning("MULTI_CLASSIFICATION_VALIDATION_PENDING_DEFINITION",
                "One classification is prevalidated per request until the workflow owner defines whether multiple classification changes belong to one validation record"));
        issues.add(ValidationIssue.warning("VALIDATION_PERSISTENCE_NOT_CONNECTED",
                "The correction is not saved and cannot become durably PENDING_APPROVAL until the KPI validation persistence contract is available"));

        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new StopTimeCorrectionPrevalidationResult(valid, false, command.plantId(), command.lineId(),
                command.businessDate(), command.stopClassificationId(),
                observed == null ? null : observed.classificationCode(),
                observed == null ? null : observed.classificationActive(),
                observed == null ? null : observed.programmingType(),
                command.originalSeconds(), observed == null ? null : observed.recordedLineDowntimeSeconds(),
                originalMatchesReference, command.proposedSeconds(),
                observed == null ? null : observed.sourceAggregateKey(),
                justification, auditEvidence(actor), stopTimeReference.source(),
                observed != null && observed.programmingType() != null
                        ? stopTimeReference.programmingTypeState()
                        : "MAPPING_INCOMPLETE",
                ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "PENDING_EXTERNAL_INTEGRATION", List.copyOf(issues));
    }

    @Override
    public StopTimePolicyView getStopTimePolicy() {
        return new StopTimePolicyView(
                stopTimeReference.source(),
                "LINE_DATE_STOP_CLASSIFICATION_AGGREGATE",
                stopTimeReference.programmingTypeState(),
                stopTimeReference.programmingTypeConfigurationKey(),
                List.of("PROGRAMMED", "UNPROGRAMMED"),
                "ACTIVE_PLANT_ORGANIZATION_OR_GLOBAL_CATALOG_PLUS_OBSERVED_INACTIVE_D_MINUS_1",
                "SERVER_REVALIDATES_CANONICAL_AGGREGATE_AND_REJECTS_STALE_ORIGINAL",
                "PENDING_FUNCTIONAL_DEFINITION",
                "ONE_CLASSIFICATION_PER_PREVALIDATION_PENDING_WORKFLOW_GROUPING_DEFINITION",
                ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "PENDING_KPI_VALIDATION_PERSISTENCE");
    }

    @Override
    public MassPerformanceReferenceResult getMassPerformanceReference(ActorContext actor, long plantId, long lineId,
            LocalDate businessDate) {
        EligibilityResult eligibility = check(actor, plantId, lineId, businessDate);
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());
        if (!eligibility.eligible()) {
            return new MassPerformanceReferenceResult(plantId, lineId, businessDate, eligibility.plantTimezone(),
                    massPerformanceReference.source(), massPerformanceReference.sourceMapping(), List.of(),
                    List.copyOf(issues));
        }

        ZoneId zone = ZoneId.of(eligibility.plantTimezone());
        List<MassPerformanceReferenceEntry> entries = massPerformanceReference.findObservedMasses(
                plantId, lineId, businessDate, zone);

        boolean provisionalFallback = entries.stream().anyMatch(entry ->
                entry.sourceType() != null && entry.sourceType().endsWith("_PROVISIONAL"));
        if (provisionalFallback) {
            issues.add(ValidationIssue.warning("MASS_SCHEDULING_FALLBACK_PROVISIONAL",
                    "At least one mass variable is using the canonical v1.2 scheduling fallback because no exact line-level derived metric was found. The mapping is explicit but still requires OEE performance confirmation"));
        }
        if (entries.isEmpty()) {
            issues.add(ValidationIssue.warning("MASS_REFERENCE_NOT_FOUND",
                    "No canonical D-1 mass value was found for this line. Spec 05 distinguishes correction from manual capture, so absence is not silently converted to zero"));
        }
        issues.add(ValidationIssue.warning("MASS_RELATION_RULE_PENDING_DEFINITION",
                "No delivered-vs-elaborated relation is enforced because Spec 05 leaves special performance logic and allowed relationships undefined"));
        issues.add(ValidationIssue.warning("MASS_PRECISION_ROUNDING_PENDING_DEFINITION",
                "Canonical decimal values are preserved without application rounding; Spec 05 leaves final precision and rounding rules open"));
        return new MassPerformanceReferenceResult(plantId, lineId, businessDate, eligibility.plantTimezone(),
                massPerformanceReference.source(), massPerformanceReference.sourceMapping(), entries,
                List.copyOf(issues));
    }

    @Override
    public MassPerformancePolicyView getMassPerformancePolicy() {
        return new MassPerformancePolicyView(
                massPerformanceReference.source(),
                massPerformanceReference.sourceMode(),
                "LINE_DATE_VARIABLE_UOM_AGGREGATE",
                Arrays.stream(MassPerformanceVariable.values()).map(Enum::name).toList(),
                massPerformanceReference.elaboratedMetricCode(),
                massPerformanceReference.deliveredMetricCode(),
                massPerformanceReference.sourceMapping(),
                "SERVER_REVALIDATES_CANONICAL_REFERENCE_AND_REJECTS_STALE_ORIGINAL",
                "NO_SOURCE_ROW_IS_NOT_TREATED_AS_ZERO_OR_MANUAL_CAPTURE",
                "ONLY_CANONICAL_UOM_WITH_DIMENSION_CODE_MASS",
                "PRESERVE_CANONICAL_DECIMALS_NO_APPLICATION_ROUNDING_PENDING_DEFINITION",
                "PENDING_FUNCTIONAL_DEFINITION",
                "RECHECK_BEFORE_SUBMISSION; POST_PERSISTENCE_PRECEDENCE_PENDING_DEFINITION",
                ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "PENDING_KPI_VALIDATION_PERSISTENCE");
    }

    @Override
    public MassPerformanceCorrectionPrevalidationResult prevalidateMassPerformance(ActorContext actor,
            PrevalidateMassPerformanceCorrectionCommand command) {
        Objects.requireNonNull(command, "command");
        EligibilityResult eligibility = check(actor, command.plantId(), command.lineId(), command.businessDate());
        List<ValidationIssue> issues = new ArrayList<>(eligibility.issues());

        if (command.uomId() <= 0) {
            issues.add(ValidationIssue.blocker("INVALID_MASS_UOM_ID", "uomId must be positive"));
        } else if (eligibility.eligible() && !massPerformanceReference.uomExists(command.uomId())) {
            issues.add(ValidationIssue.blocker("MASS_UOM_NOT_FOUND", "uomId does not exist in the canonical UOM catalog"));
        } else if (eligibility.eligible() && !massPerformanceReference.uomIsMassDimension(command.uomId())) {
            issues.add(ValidationIssue.blocker("MASS_UOM_DIMENSION_REQUIRED",
                    "uomId must use canonical dimension_code=MASS for Spec 05"));
        }
        if (command.variable() == null) {
            issues.add(ValidationIssue.blocker("MASS_VARIABLE_REQUIRED",
                    "variable must identify ELABORATED_MASS or DELIVERED_MASS"));
        }
        if (command.originalValue() == null) {
            issues.add(ValidationIssue.blocker("ORIGINAL_MASS_VALUE_REQUIRED",
                    "originalValue is required so the proposal preserves the value presented to the validator"));
        } else if (command.originalValue().signum() < 0) {
            issues.add(ValidationIssue.blocker("ORIGINAL_MASS_VALUE_NEGATIVE", "originalValue cannot be negative"));
        }
        if (command.proposedValue() == null) {
            issues.add(ValidationIssue.blocker("PROPOSED_MASS_VALUE_REQUIRED", "proposedValue is required"));
        } else if (command.proposedValue().signum() < 0) {
            issues.add(ValidationIssue.blocker("PROPOSED_MASS_VALUE_NEGATIVE", "proposedValue cannot be negative"));
        }
        CorrectionJustificationView justification = validateAndNormalizeJustification(
                command.reasonType(), command.incidentNumber(), command.operationalCause(), command.actionPlan(), issues);

        java.math.BigDecimal observedReferenceValue = null;
        Boolean originalMatchesOperationalReference = null;
        String sourceAggregateKey = null;
        String sourceType = null;
        if (eligibility.eligible() && command.uomId() > 0 && command.variable() != null) {
            ZoneId zone = ZoneId.of(eligibility.plantTimezone());
            Optional<MassPerformanceReferenceEntry> observed = massPerformanceReference
                    .findObservedMasses(command.plantId(), command.lineId(), command.businessDate(), zone)
                    .stream()
                    .filter(entry -> entry.uomId() == command.uomId() && entry.variable() == command.variable())
                    .findFirst();
            if (observed.isEmpty()) {
                issues.add(ValidationIssue.blocker("MASS_ORIGINAL_VALUE_NOT_FOUND",
                        "No canonical D-1 value exists for the requested line/variable/UOM. Spec 05 is a correction flow and does not permit silently creating a missing mass value from zero"));
            } else {
                MassPerformanceReferenceEntry entry = observed.get();
                observedReferenceValue = entry.originalValue();
                sourceAggregateKey = entry.sourceAggregateKey();
                sourceType = entry.sourceType();
                if (command.originalValue() != null) {
                    originalMatchesOperationalReference = command.originalValue().compareTo(observedReferenceValue) == 0;
                    if (!originalMatchesOperationalReference) {
                        issues.add(ValidationIssue.blocker("ORIGINAL_MASS_VALUE_CHANGED",
                                "The client originalValue differs from the current server-side D-1 reference. Refresh the data before proposing a correction"));
                    }
                }
                if (sourceType != null && sourceType.endsWith("_PROVISIONAL")) {
                    issues.add(ValidationIssue.warning("MASS_SOURCE_MAPPING_PROVISIONAL",
                            "The selected value is using a canonical scheduling fallback whose elaborated/delivered interpretation still requires OEE performance confirmation"));
                }
            }
        }

        issues.add(ValidationIssue.warning("MASS_RELATION_RULE_PENDING_DEFINITION",
                "No relationship between elaborated and delivered mass is enforced because Spec 05 leaves this rule open, especially for special performance logic"));
        issues.add(ValidationIssue.warning("MASS_PRECISION_ROUNDING_PENDING_DEFINITION",
                "Fractional canonical values are accepted without additional application rounding; final precision and rounding remain undefined in Spec 05"));
        issues.add(ValidationIssue.warning("LATE_AUTOMATIC_DATA_POST_PERSISTENCE_PRECEDENCE_PENDING",
                "The server rechecks the source before submission, but Spec 05 does not define what wins if automatic data changes after a validation proposal has already been persisted"));
        issues.add(ValidationIssue.warning("VALIDATION_PERSISTENCE_NOT_CONNECTED",
                "The correction is not saved and cannot enter durable PENDING_APPROVAL until the KPI validation persistence contract is available"));

        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new MassPerformanceCorrectionPrevalidationResult(valid, false, command.plantId(), command.lineId(),
                command.businessDate(), command.uomId(), command.variable(),
                command.originalValue(), command.proposedValue(), observedReferenceValue,
                originalMatchesOperationalReference, sourceAggregateKey, sourceType, justification,
                auditEvidence(actor), massPerformanceReference.source(), ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "PENDING_EXTERNAL_INTEGRATION", List.copyOf(issues));
    }


    private CorrectionJustificationView validateAndNormalizeJustification(ValidationReasonType reasonType,
            String incidentNumber, String operationalCause, String actionPlan, List<ValidationIssue> issues) {
        boolean hadIncident = incidentNumber != null && !incidentNumber.isBlank();
        boolean hadCause = operationalCause != null && !operationalCause.isBlank();
        boolean hadPlan = actionPlan != null && !actionPlan.isBlank();
        try {
            CorrectionReason normalized = new CorrectionReason(reasonType, incidentNumber, operationalCause, actionPlan)
                    .normalized();

            if (reasonType == ValidationReasonType.SYSTEM && (hadCause || hadPlan)) {
                issues.add(ValidationIssue.warning("IRRELEVANT_OPERATIVE_DETAILS_DISCARDED",
                        "operationalCause/actionPlan were discarded because SYSTEM uses incidentNumber as its evidence"));
            } else if (reasonType == ValidationReasonType.OPERATIVE && hadIncident) {
                issues.add(ValidationIssue.warning("IRRELEVANT_INCIDENT_NUMBER_DISCARDED",
                        "incidentNumber was discarded because OPERATIVE uses operationalCause and actionPlan as its evidence"));
            }

            issues.add(ValidationIssue.warning("JUSTIFICATION_TEXT_LENGTH_PENDING_DEFINITION",
                    "Spec 06 requires UX length limits for free text, but the maximum lengths are still undefined; no arbitrary business limit is imposed"));
            issues.add(ValidationIssue.warning("SENSITIVE_DATA_POLICY_PENDING_DEFINITION",
                    "Spec 06 leaves the sensitive-data policy for free-text justification fields undefined; callers must avoid placing secrets or unnecessary personal data in these fields"));
            if (reasonType == ValidationReasonType.SYSTEM) {
                issues.add(ValidationIssue.warning("INCIDENT_FORMAT_AND_GB_SUPPORT_VALIDATION_PENDING",
                        "incidentNumber is captured as non-blank text only; its format and validation against GB Support are still undefined"));
            }

            return new CorrectionJustificationView(normalized.type(), normalized.incidentNumber(),
                    normalized.operationalCause(), normalized.actionPlan());
        } catch (DomainValidationException ex) {
            issues.add(ValidationIssue.blocker("INVALID_REASON_DETAILS", ex.getMessage()));
            return null;
        }
    }


    private ValidationAuditEvidenceView auditEvidence(ActorContext actor) {
        return new ValidationAuditEvidenceView(
                actor == null ? null : actor.tenantId(),
                actor == null ? null : actor.objectId(),
                actor == null ? null : actor.immutableActorId(),
                actor == null ? null : actor.displayName(),
                actor == null ? null : actor.principalName(),
                Instant.now(clock),
                "ABSOLUTE_INSTANT_UTC",
                "PREVALIDATION_EVIDENCE_ONLY_PENDING_PERSISTENCE");
    }

    @Override
    public AuditPolicyView getAuditPolicy() {
        return new AuditPolicyView(
                "ENTRA_TENANT_ID_PLUS_OBJECT_ID",
                List.of("displayNameSnapshot", "principalNameSnapshot"),
                "ABSOLUTE_INSTANT_UTC",
                "PENDING_PRESENTATION_TIMEZONE_DEFINITION",
                "PENDING_KPI_VALIDATION_PERSISTENCE",
                "PENDING_PERSISTED_SNAPSHOT",
                "PENDING_APPROVAL_AND_REPORTING_INTEGRATION");
    }

    @Override
    public WorkflowPolicyView getWorkflowPolicy() {
        return new WorkflowPolicyView(
                Arrays.stream(ValidationWorkflowStatus.values()).map(Enum::name).toList(),
                ValidationWorkflowStatus.PENDING_APPROVAL.name(),
                "KPI_RESULT_ID",
                "PENDING_KPI_VALIDATION_PERSISTENCE",
                "CONTRACT_DEFINED_REQUIRES_PERSISTENT_UNIQUENESS",
                "CONTRACT_DEFINED_REQUIRES_TRANSACTIONAL_PERSISTENCE",
                "PENDING_APPROVAL_MODULE_CONTRACT");
    }

    @Override
    public JustificationPolicyView getJustificationPolicy() {
        return new JustificationPolicyView(
                List.of("OPERATIVE", "SYSTEM"),
                List.of("operationalCause", "actionPlan"),
                List.of("incidentNumber"),
                "IMPLEMENTED_BASIC_TRIM_AND_CONTROL_CHARACTER_VALIDATION",
                "PENDING_UX_DEFINITION",
                "CAPTURE_ONLY_PATTERN_NOT_DEFINED",
                "PENDING_EXTERNAL_INTEGRATION",
                "PENDING_DEFINITION",
                "PENDING_KPI_VALIDATION_PERSISTENCE");
    }

    @Override
    public List<CapabilityView> capabilities() {
        String entitlement = validatorAssignments.enforcesRoleCapability()
                ? (validatorAssignments.configured() ? "IMPLEMENTED_CANONICAL_V1_2" : "PENDING_REFERENCE_DATA")
                : (requiredValidatorRole.isBlank() ? "PENDING_CONFIGURATION" : "IMPLEMENTED_LOCAL_OR_EXTERNAL");
        String assignments = validatorAssignments.configured()
                ? (validatorAssignments.enforcesRoleCapability() ? "IMPLEMENTED_CANONICAL_V1_2" : "IMPLEMENTED_EXTERNAL_CONFIG")
                : "PENDING_CONFIGURATION";
        return List.of(
                new CapabilityView("authentication", "IMPLEMENTED", "Entra identity is required when security is enabled"),
                new CapabilityView("plantLineScope", "IMPLEMENTED_V1_2", "Validated through app_security.user_identity + current user_plant_access and current core.line_plant_assignment"),
                new CapabilityView("validatorEntitlement", entitlement,
                        validatorAssignments.enforcesRoleCapability()
                                ? "Canonical v1.2 role=" + validatorAssignments.roleCode() + ", capability=" + validatorAssignments.capabilityCode()
                                : (requiredValidatorRole.isBlank() ? "Set GBC_KPI_VALIDATOR_ROLE" : "Local/external role: " + requiredValidatorRole)),
                new CapabilityView("kpiSpecificPlantAssignment", assignments,
                        validatorAssignments.configured()
                                ? "KPI validator plant assignments resolved from " + validatorAssignments.source()
                                : "Canonical role/capability reference data or local assignment configuration is required"),
                new CapabilityView("maxTwoValidatorsPerPlant", assignments,
                        validatorAssignments.configured()
                                ? "Every operation rejects a plant with more than two effective validators (current KPI role/capability plus current generic plant access); write-time prevention remains external"
                                : "Validator assignment source is not configured"),
                new CapabilityView("assignmentRevocation", assignments,
                        validatorAssignments.enforcesRoleCapability()
                                ? "Closing user_role_assignment.valid_to revokes the canonical plant assignment without redeploy"
                                : "Remove the external assignment and restart/redeploy"),
                new CapabilityView("assignmentChangeAudit", validatorAssignments.enforcesRoleCapability() ? "IMPLEMENTED_PARTIAL_CANONICAL_HISTORY" : "PENDING_EXTERNAL_INTEGRATION",
                        validatorAssignments.enforcesRoleCapability()
                                ? "app_security.user_role_assignment preserves assigned_by_user_id/reason and validity history, but the v1.2 table has no dedicated revoked_by field and this micro does not administer writes"
                                : "Assignment administration is external"),
                new CapabilityView("assignmentPolicyContract", "IMPLEMENTED_SPEC_02",
                        "GET /assignments/policy exposes semantic role/capability, max two, external approval, optional valid_to expiry, multi-plant support and the remaining administration constraints"),
                new CapabilityView("pendingValidationOnRevocation", "PENDING_DEFINITION",
                        "Spec 02 leaves behavior for pending validations undefined"),
                new CapabilityView("previousDayRule", "IMPLEMENTED_PROVISIONAL",
                        "Exactly the previous local calendar date; weekends and holidays are not skipped"),
                new CapabilityView("temporalContext", "IMPLEMENTED_SPEC_03",
                        "Server exposes plant-local current date/time, the single eligible KPI business date, the current D-1 window expiry at next local midnight, and mandatory revalidation-on-submit semantics"),
                new CapabilityView("weekendsAndHolidays", "IMPLEMENTED",
                        "No business-day calendar is applied; D-1 remains eligible on weekends and holidays"),
                new CapabilityView("serverSideDateRevalidation", "IMPLEMENTED",
                        "Eligibility is recalculated on each request/prevalidation so stale URLs/screens cannot bypass D-1; temporal-context exposes the next local-midnight expiry for frontend refresh"),
                new CapabilityView("oeeDayCutoffAnd24HourSemantics", "PENDING_DEFINITION",
                        "Spec 03 leaves exact 24-hour/OEE-cutoff interpretation open"),
                new CapabilityView("authoritativeTimezone", "IMPLEMENTED_PROVISIONAL",
                        "Uses core.plant.timezone_name until the authoritative timezone rule is confirmed; invalid/blank timezone configuration is blocked safely"),
                new CapabilityView("reasonValidation", "IMPLEMENTED_SPEC_06",
                        "Only SYSTEM/OPERATIVE are accepted; SYSTEM requires incidentNumber, OPERATIVE requires cause/action plan, text is normalized and stale fields from a changed reason are discarded"),
                new CapabilityView("justificationPersistence", "PENDING_EXTERNAL_INTEGRATION",
                        "Spec 06 requires atomic persistence with the validation, but no KPI validation persistence contract/table is available"),
                new CapabilityView("gbSupportIncidentValidation", "PENDING_DEFINITION",
                        "Incident format and validation against GB Support remain undefined; the service currently captures any non-blank incidentNumber"),
                new CapabilityView("justificationTextPolicy", "IMPLEMENTED_PARTIAL",
                        "Outer whitespace/control characters are handled; UX maximum lengths and sensitive-data policy remain undefined"),
                new CapabilityView("operationalMonitoring", "IMPLEMENTED",
                        "HTTP runtimes log critical consultations/actions and rejected requests with correlation and actor/scope when available"),
                new CapabilityView("stopTimeReferenceByClassification", "IMPLEMENTED_CANONICAL_V1_2",
                        "Read-only reference clips stops.stop_fragment to the D-1 window and groups by the latest stops.classification_history classification for each event"),
                new CapabilityView("stopTimeOriginalReconciliation", "IMPLEMENTED",
                        "Spec 04 prevalidation re-reads the canonical D-1 aggregate and blocks stale/manipulated originalSeconds"),
                new CapabilityView("stopTimeSignAndFormatValidation", "IMPLEMENTED",
                        "Spec 04 prevalidation rejects missing or negative original/proposed stop-time seconds; zero is valid when observed"),
                new CapabilityView("stopClassificationCatalogValidation", "IMPLEMENTED_PARTIAL",
                        "stopClassificationId is validated against stops.stop_classification and plant organization/global scope; active scoped classifications are exposed with zero when not observed because v1.2 has no line-specific relation"),
                new CapabilityView("programmedUnprogrammedClassification", "IMPLEMENTED_CONFIGURABLE",
                        "PROGRAMMED/UNPROGRAMMED is mapped explicitly by classification_code through GBC_STOP_PROGRAMMING_TYPE_BY_CODE; no code/name inference is performed"),
                new CapabilityView("inactiveStopClassificationHistory", "IMPLEMENTED_PROVISIONAL",
                        "A classification observed in D-1 remains visible/prevalidatable even when currently inactive so historical reconciliation does not disappear"),
                new CapabilityView("stopTimeTotalConsistency", "PENDING_DEFINITION",
                        "Spec 04 explicitly leaves the mathematical maximum/sum rule undefined; no 24-hour rule is inherited"),
                new CapabilityView("multiClassificationValidation", "PENDING_DEFINITION",
                        "Spec 04 leaves open whether several classification corrections belong to one persisted validation; prevalidation remains one classification per request"),
                new CapabilityView("massPerformanceReference", "IMPLEMENTED_CANONICAL_V1_2",
                        "Spec 05 prefers exact line-level telemetry.derived_metric values by semantic metric code and falls back per variable to canonical scheduling data when configured AUTO and no derived metric exists"),
                new CapabilityView("massPerformanceOriginalReconciliation", "IMPLEMENTED",
                        "Prevalidation re-reads the D-1 line/variable/UOM reference and blocks stale or manipulated originalValue"),
                new CapabilityView("massPerformanceNumericValidation", "IMPLEMENTED",
                        "Spec 05 accepts canonical decimal zero/positive values, rejects negatives, and requires a canonical UOM with dimension_code=MASS"),
                new CapabilityView("massPerformanceVariableEnablement", "IMPLEMENTED_SEMANTIC_CODES",
                        "ELABORATED_MASS and DELIVERED_MASS are explicit variables; derived metric codes are configurable through GBC_ELABORATED_MASS_METRIC_CODE and GBC_DELIVERED_MASS_METRIC_CODE"),
                new CapabilityView("massPerformanceMissingOriginal", "IMPLEMENTED_CONSERVATIVE",
                        "A missing canonical source row is not converted to zero because Spec 05 is a correction flow rather than KPI Manual capture-from-zero"),
                new CapabilityView("massPerformanceRelationRule", "PENDING_DEFINITION",
                        "Spec 05 leaves the allowed relation between elaborated and delivered mass undefined, especially for special performance logic"),
                new CapabilityView("massPerformancePrecisionRounding", "IMPLEMENTED_PROVISIONAL",
                        "Canonical decimals are preserved without application rounding until the official precision/rounding rule is defined"),
                new CapabilityView("lateAutomaticMassDataPrecedence", "IMPLEMENTED_PARTIAL",
                        "Source is rechecked before submission; precedence for automatic changes after a proposal is durably persisted remains undefined"),
                new CapabilityView("validationAuditEvidence", "IMPLEMENTED_PREVALIDATION_ONLY",
                        "Spec 07 prevalidation responses include immutable Entra tid+oid, optional display snapshots and the event instant; persistence is still pending"),
                new CapabilityView("auditTimestampSemantics", "IMPLEMENTED",
                        "Spec 07 represents the validation/prevalidation event time as an absolute UTC Instant so a later presentation timezone cannot change the recorded instant"),
                new CapabilityView("persistedAuditReconciliation", "PENDING_EXTERNAL_INTEGRATION",
                        "Persisted actor/timestamp reconciliation across Validation, Approval and reports requires the KPI validation persistence contract"),
                new CapabilityView("oeeOriginalValueSource", "IMPLEMENTED_READ_ONLY_V1_2",
                        "Reads the latest canonical kpi.kpi_result for the requested kpi_definition.kpi_code when its line-level period exactly matches the provisional local D-1 window"),
                new CapabilityView("validationWorkflowContract", "IMPLEMENTED_SEMANTIC_CONTRACT",
                        "Internal states are PENDING_APPROVAL, APPROVED and REJECTED; a valid new proposal targets PENDING_APPROVAL when persistence becomes available"),
                new CapabilityView("proposalPersistence", "PENDING_EXTERNAL_INTEGRATION",
                        "No KPI validation persistence contract/table is available"),
                new CapabilityView("singleModificationPerDatum", "PENDING_EXTERNAL_INTEGRATION",
                        "Requires persistent identity/uniqueness of the source OEE datum"),
                new CapabilityView("approvalWorkflow", "PENDING_DEPENDENCY",
                        "KPI Approval remains the second-stage dependency")
        );
    }
}


