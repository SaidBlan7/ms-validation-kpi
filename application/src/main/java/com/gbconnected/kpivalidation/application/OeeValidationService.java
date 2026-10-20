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
        GetValidatorAssignmentPolicyUseCase, GetStopTimePolicyUseCase, GetMassPerformancePolicyUseCase,
        SubmitOeeValidationUseCase, GetOeeValidationUseCase, SubmitStopTimeValidationUseCase, GetStopTimeValidationUseCase, AdministerValidatorAssignmentsUseCase {

    private static final String DATE_POLICY_WARNING_CODE = "OEE_TEMPORAL_AUTHORITY_PROVISIONAL";

    private final AccessScopePort accessScope;
    private final OperationalContextPort context;
    private final ValidatorAssignmentPort validatorAssignments;
    private final StopTimeReferencePort stopTimeReference;
    private final MassPerformanceReferencePort massPerformanceReference;
    private final OeeMetricSourcePort oeeMetricSource;
    private final ActorIdentityPort actorIdentity;
    private final ValidationPersistencePort validationPersistence;
    private final FeatureActivationPort featureActivation;
    private final ValidatorAssignmentAdministrationPort validatorAssignmentAdministration;
    private final CorrectionReasonPolicy correctionReasonPolicy;
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
        this(accessScope, context, validatorAssignments, stopTimeReference, massPerformanceReference, oeeMetricSource,
                ActorIdentityPort.unavailable(), ValidationPersistencePort.unavailable(), FeatureActivationPort.enabledByDefault("KPI_OEE_VALIDATION_ENABLED"), ValidatorAssignmentAdministrationPort.unavailable(), CorrectionReasonPolicy.unbounded(), businessDatePolicy, clock,
                requiredValidatorRole);
    }

    public OeeValidationService(AccessScopePort accessScope,
            OperationalContextPort context,
            ValidatorAssignmentPort validatorAssignments,
            StopTimeReferencePort stopTimeReference,
            MassPerformanceReferencePort massPerformanceReference,
            OeeMetricSourcePort oeeMetricSource,
            ActorIdentityPort actorIdentity,
            CorrectionReasonPolicy correctionReasonPolicy,
            BusinessDatePolicy businessDatePolicy,
            Clock clock,
            String requiredValidatorRole) {
        this(accessScope, context, validatorAssignments, stopTimeReference, massPerformanceReference, oeeMetricSource,
                actorIdentity, ValidationPersistencePort.unavailable(), FeatureActivationPort.enabledByDefault("KPI_OEE_VALIDATION_ENABLED"), ValidatorAssignmentAdministrationPort.unavailable(), correctionReasonPolicy, businessDatePolicy, clock,
                requiredValidatorRole);
    }

    public OeeValidationService(AccessScopePort accessScope,
            OperationalContextPort context,
            ValidatorAssignmentPort validatorAssignments,
            StopTimeReferencePort stopTimeReference,
            MassPerformanceReferencePort massPerformanceReference,
            OeeMetricSourcePort oeeMetricSource,
            ActorIdentityPort actorIdentity,
            ValidationPersistencePort validationPersistence,
            FeatureActivationPort featureActivation,
            ValidatorAssignmentAdministrationPort validatorAssignmentAdministration,
            CorrectionReasonPolicy correctionReasonPolicy,
            BusinessDatePolicy businessDatePolicy,
            Clock clock,
            String requiredValidatorRole) {
        this.accessScope = Objects.requireNonNull(accessScope);
        this.context = Objects.requireNonNull(context);
        this.validatorAssignments = Objects.requireNonNull(validatorAssignments);
        this.stopTimeReference = Objects.requireNonNull(stopTimeReference);
        this.massPerformanceReference = Objects.requireNonNull(massPerformanceReference);
        this.oeeMetricSource = Objects.requireNonNull(oeeMetricSource);
        this.actorIdentity = Objects.requireNonNull(actorIdentity);
        this.validationPersistence = Objects.requireNonNull(validationPersistence);
        this.featureActivation = Objects.requireNonNull(featureActivation);
        this.validatorAssignmentAdministration = Objects.requireNonNull(validatorAssignmentAdministration);
        this.correctionReasonPolicy = Objects.requireNonNull(correctionReasonPolicy);
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
        validateFeatureActivation(plantId, issues);

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
                    "Only D-1 in the plant-local calendar is eligible. Weekends and holidays are not skipped; refresh temporal-context when the plant crosses midnight"));
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
        validateFeatureActivation(plantId, issues);
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
                "Spec 03 confirms calendar D-1 and explicitly includes weekends/holidays. The official OEE cutoff, elapsed-24-hour interpretation and final timezone authority remain open; until defined, the server uses core.plant.timezone_name and never consults a business-day/holiday calendar"));
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

    private void validateFeatureActivation(long plantId, List<ValidationIssue> issues) {
        if (!featureActivation.configured()) {
            issues.add(ValidationIssue.blocker("KPI_OEE_VALIDATION_FEATURE_NOT_CONFIGURED",
                    "Canonical configuration definition " + featureActivation.configurationCode() + " is not configured"));
            return;
        }
        if (plantId > 0 && !featureActivation.enabledForPlant(plantId)) {
            issues.add(ValidationIssue.blocker("KPI_OEE_VALIDATION_DISABLED_FOR_PLANT",
                    "KPI OEE validation is not enabled for the requested plant"));
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
                .filter(featureActivation::enabledForPlant)
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
                canonical ? validatorAssignmentAdministration.adminCapabilityCode() : null,
                2,
                "PLANT_SPECIFIC_KPI_OEE_PERMISSION",
                "CURRENT_GENERIC_USER_PLANT_ACCESS_REQUIRED",
                "EXTERNAL_GLOBAL_SUPPLY_CHAIN_APPROVAL",
                canonical ? (validatorAssignmentAdministration.available() ? "IN_MICROSERVICE_CANONICAL_ADMIN_API" : "PENDING_ADMIN_CAPABILITY_REFERENCE_DATA") : "LOCAL_FIXTURE_CONFIGURATION",
                canonical ? "OPTIONAL_VALID_TO_NULL_MEANS_INDEFINITE" : "CONFIGURATION_LIFETIME",
                "MULTIPLE_PLANTS_SUPPORTED_NO_GLOBAL_LIMIT",
                canonical
                        ? "TRANSACTIONAL_WRITE_TIME_LIMIT_WITH_PLANT_ADVISORY_LOCK"
                        : "LOCAL_CONFIGURATION_REJECTS_MORE_THAN_TWO",
                canonical ? "ADMIN_API_CLOSES_USER_ROLE_ASSIGNMENT_VALID_TO" : "REMOVE_LOCAL_ASSIGNMENT_AND_RELOAD",
                canonical
                        ? "CANONICAL_HISTORY_PLUS_AUDIT_CHANGE_LOG_ACTOR_CORRELATION_AND_REASON"
                        : "NO_PERSISTENT_ASSIGNMENT_AUDIT",
                "PENDING_BUSINESS_DEFINITION_VALIDATIONS_REMAIN_PERSISTED",
                canonical
                        ? "TRANSACTIONALLY_SERIALIZED_BY_PLANT_ADVISORY_LOCK"
                        : "STARTUP_CONFIGURATION_VALIDATION");
    }


    @Override
    public List<ValidatorAssignmentAdminView> list(ActorContext actor, long plantId) {
        requireAssignmentAdministrator(actor, plantId);
        return validatorAssignmentAdministration.list(plantId);
    }

    @Override
    public ValidatorAssignmentChangeResult assign(ActorContext actor, AssignValidatorCommand command, String correlationId) {
        Objects.requireNonNull(command, "command");
        long actorUserId = requireAssignmentAdministrator(actor, command.plantId());
        if (command.targetUserId() <= 0) throw new IllegalArgumentException("targetUserId must be positive");
        if (command.externalApprovalReference() == null || command.externalApprovalReference().isBlank())
            throw new IllegalArgumentException("externalApprovalReference is required because Direction Global approval remains external");
        return validatorAssignmentAdministration.assign(actorUserId, command.targetUserId(), command.plantId(), command.validTo(),
                command.externalApprovalReference(), command.reason(), correlationId);
    }

    @Override
    public ValidatorAssignmentChangeResult modify(ActorContext actor, ModifyValidatorAssignmentCommand command, String correlationId) {
        Objects.requireNonNull(command, "command");
        long actorUserId = requireAssignmentAdministrator(actor, command.plantId());
        if (command.targetUserId() <= 0) throw new IllegalArgumentException("targetUserId must be positive");
        if (command.externalApprovalReference() == null || command.externalApprovalReference().isBlank())
            throw new IllegalArgumentException("externalApprovalReference is required for assignment modification");
        return validatorAssignmentAdministration.modify(actorUserId, command.targetUserId(), command.plantId(), command.validTo(),
                command.externalApprovalReference(), command.reason(), correlationId);
    }

    @Override
    public ValidatorAssignmentChangeResult revoke(ActorContext actor, RevokeValidatorAssignmentCommand command, String correlationId) {
        Objects.requireNonNull(command, "command");
        long actorUserId = requireAssignmentAdministrator(actor, command.plantId());
        if (command.targetUserId() <= 0) throw new IllegalArgumentException("targetUserId must be positive");
        if (command.reason() == null || command.reason().isBlank()) throw new IllegalArgumentException("reason is required for revocation");
        return validatorAssignmentAdministration.revoke(actorUserId, command.targetUserId(), command.plantId(), command.reason(), correlationId);
    }

    private long requireAssignmentAdministrator(ActorContext actor, long plantId) {
        if (!validatorAssignmentAdministration.available())
            throw new IllegalStateException("KPI OEE validator assignment administration reference data is not configured");
        if (actor == null || actor.tenantId() == null || actor.objectId() == null)
            throw new AuthorizationException("Authenticated Entra actor is required");
        if (plantId <= 0) throw new IllegalArgumentException("plantId must be positive");
        if (!validatorAssignmentAdministration.canAdminister(actor.tenantId(), actor.objectId(), plantId))
            throw new AuthorizationException("Actor is not authorized to administer KPI OEE validator assignments for the plant");
        ActorIdentitySnapshot identity = actorIdentity.findByEntraIdentity(actor.tenantId(), actor.objectId())
                .orElseThrow(() -> new AuthorizationException("Actor does not have a canonical app_security identity"));
        if (!identity.active() || identity.canonicalUserId() == null)
            throw new AuthorizationException("Actor canonical identity is inactive or unresolved");
        return identity.canonicalUserId();
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
        if (metric != null && command.proposedValue() != null && metric.value().compareTo(command.proposedValue()) == 0) {
            issues.add(ValidationIssue.blocker("PROPOSED_VALUE_MUST_DIFFER",
                    "proposedValue must differ from the canonical original value"));
        }
        if (!validationPersistence.available()) {
            issues.add(ValidationIssue.warning("VALIDATION_PERSISTENCE_UNAVAILABLE",
                    "Prevalidation is available, but this runtime cannot persist kpi.kpi_validation proposals"));
        } else if (metric != null) {
            long kpiResultId = parseKpiResultId(metric.sourceRecordKey());
            if (kpiResultId > 0 && validationPersistence.existsForKpiResult(kpiResultId)) {
                issues.add(ValidationIssue.blocker("OEE_VALUE_ALREADY_VALIDATED",
                        "This KPI result already has a validation proposal and cannot be modified again"));
            }
        }
        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new PrevalidationResult(valid, valid && validationPersistence.available(), command.plantId(), command.lineId(), command.businessDate(),
                command.metricCode(), metric == null ? null : metric.value(), command.proposedValue(), justification,
                auditEvidence(actor), metric == null ? null : metric.sourceRecordKey(),
                metric == null ? "CANONICAL_KPI_RESULT_NOT_FOUND" : oeeMetricSource.source(),
                ValidationWorkflowStatus.PENDING.name(),
                validationPersistence.available() ? "CANONICAL_KPI_VALIDATION_READY" : "UNAVAILABLE", List.copyOf(issues));
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

        if (observed != null && command.originalSeconds() != null && command.proposedSeconds() != null
                && command.originalSeconds().longValue() == command.proposedSeconds().longValue()) {
            issues.add(ValidationIssue.blocker("PROPOSED_STOP_TIME_MUST_DIFFER",
                    "proposedSeconds must differ from originalSeconds"));
        }
        if (!validationPersistence.available()) {
            issues.add(ValidationIssue.warning("VALIDATION_PERSISTENCE_UNAVAILABLE",
                    "Prevalidation is available, but this runtime cannot persist kpi.kpi_validation proposals"));
        } else if (command.stopClassificationId() > 0
                && validationPersistence.existsForStopTime(command.lineId(), command.businessDate(), command.stopClassificationId())) {
            issues.add(ValidationIssue.blocker("STOP_TIME_ALREADY_VALIDATED",
                    "This line/date/classification already has a validation proposal and cannot be edited again"));
        }

        issues.add(ValidationIssue.warning("TOTAL_TIME_RULE_PENDING_DEFINITION",
                "No 24-hour or cross-classification sum rule is applied because Spec 04 explicitly leaves the exact mathematical consistency rule undefined"));
        issues.add(ValidationIssue.warning("MULTI_CLASSIFICATION_VALIDATION_PENDING_DEFINITION",
                "One classification is persisted per validation until the workflow owner defines whether multiple classification changes belong to one validation header"));

        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new StopTimeCorrectionPrevalidationResult(valid, valid && validationPersistence.available(), command.plantId(), command.lineId(),
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
                ValidationWorkflowStatus.PENDING.name(),
                validationPersistence.available() ? "CANONICAL_KPI_VALIDATION_READY" : "UNAVAILABLE", List.copyOf(issues));
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
                ValidationWorkflowStatus.PENDING.name(),
                validationPersistence.available() ? "IMPLEMENTED_CANONICAL_KPI_VALIDATION_DETAIL_STOP_CLASSIFICATION" : "UNAVAILABLE");
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
                ValidationWorkflowStatus.PENDING.name(),
                validationPersistence.available() ? "IMPLEMENTED_CANONICAL_KPI_VALIDATION_DETAIL_STOP_CLASSIFICATION" : "UNAVAILABLE");
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
                "The correction is not saved and cannot enter durable PENDING until the KPI validation persistence contract is available"));

        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKER);
        return new MassPerformanceCorrectionPrevalidationResult(valid, false, command.plantId(), command.lineId(),
                command.businessDate(), command.uomId(), command.variable(),
                command.originalValue(), command.proposedValue(), observedReferenceValue,
                originalMatchesOperationalReference, sourceAggregateKey, sourceType, justification,
                auditEvidence(actor), massPerformanceReference.source(), ValidationWorkflowStatus.PENDING.name(),
                validationPersistence.available() ? "CANONICAL_KPI_VALIDATION_READY" : "UNAVAILABLE", List.copyOf(issues));
    }


    private CorrectionJustificationView validateAndNormalizeJustification(ValidationReasonType reasonType,
            String incidentNumber, String operationalCause, String actionPlan, List<ValidationIssue> issues) {
        boolean hadIncident = incidentNumber != null && !incidentNumber.isBlank();
        boolean hadCause = operationalCause != null && !operationalCause.isBlank();
        boolean hadPlan = actionPlan != null && !actionPlan.isBlank();
        try {
            CorrectionReason normalized = new CorrectionReason(reasonType, incidentNumber, operationalCause, actionPlan)
                    .normalized(correctionReasonPolicy);

            if (reasonType == ValidationReasonType.SYSTEM && (hadCause || hadPlan)) {
                issues.add(ValidationIssue.warning("IRRELEVANT_OPERATIVE_DETAILS_DISCARDED",
                        "operationalCause/actionPlan were discarded because SYSTEM uses incidentNumber as its evidence"));
            } else if (reasonType == ValidationReasonType.OPERATIVE && hadIncident) {
                issues.add(ValidationIssue.warning("IRRELEVANT_INCIDENT_NUMBER_DISCARDED",
                        "incidentNumber was discarded because OPERATIVE uses operationalCause and actionPlan as its evidence"));
            }

            boolean relevantLengthUndefined = reasonType == ValidationReasonType.SYSTEM
                    ? !correctionReasonPolicy.incidentLengthConfigured()
                    : (!correctionReasonPolicy.operationalCauseLengthConfigured() || !correctionReasonPolicy.actionPlanLengthConfigured());
            if (relevantLengthUndefined) {
                issues.add(ValidationIssue.warning("JUSTIFICATION_TEXT_LENGTH_PENDING_DEFINITION",
                        "At least one relevant Spec 06 UX maximum length is not configured; no arbitrary business maximum is imposed for that field"));
            }
            issues.add(ValidationIssue.warning("SENSITIVE_DATA_POLICY_PENDING_DEFINITION",
                    "Spec 06 leaves the sensitive-data policy for free-text justification fields undefined; callers must avoid placing secrets or unnecessary personal data in these fields"));
            if (reasonType == ValidationReasonType.SYSTEM && !correctionReasonPolicy.incidentRegexConfigured()) {
                issues.add(ValidationIssue.warning("INCIDENT_FORMAT_AND_GB_SUPPORT_VALIDATION_PENDING",
                        "incidentNumber is captured as non-blank text only because no incident regex/GB Support validation contract is configured"));
            } else if (reasonType == ValidationReasonType.SYSTEM) {
                issues.add(ValidationIssue.warning("GB_SUPPORT_VALIDATION_PENDING",
                        "incidentNumber matches the configured local format, but online validation against GB Support remains unavailable"));
            }

            return new CorrectionJustificationView(normalized.type(), normalized.incidentNumber(),
                    normalized.operationalCause(), normalized.actionPlan());
        } catch (DomainValidationException ex) {
            issues.add(ValidationIssue.blocker("INVALID_REASON_DETAILS", ex.getMessage()));
            return null;
        }
    }


    private ValidationAuditEvidenceView auditEvidence(ActorContext actor) {
        ActorIdentitySnapshot canonical = null;
        if (actor != null && actor.tenantId() != null && actor.objectId() != null) {
            canonical = actorIdentity.findByEntraIdentity(actor.tenantId(), actor.objectId()).orElse(null);
        }
        String displayName = firstNonBlank(canonical == null ? null : canonical.displayName(), actor == null ? null : actor.displayName());
        String principalName = firstNonBlank(canonical == null ? null : canonical.principalName(), actor == null ? null : actor.principalName());
        return new ValidationAuditEvidenceView(
                canonical == null ? null : canonical.canonicalUserId(),
                actor == null ? null : actor.tenantId(),
                actor == null ? null : actor.objectId(),
                actor == null ? null : actor.immutableActorId(),
                displayName,
                principalName,
                canonical == null ? null : canonical.active(),
                canonical == null ? actorIdentity.source() : canonical.source(),
                Instant.now(clock),
                "ABSOLUTE_INSTANT_UTC",
                validationPersistence.available() ? "CANONICAL_VALIDATION_PERSISTENCE_AVAILABLE" : "PREVALIDATION_ONLY");
    }

    @Override
    public StopTimeValidationResult submitStopTime(ActorContext actor, PrevalidateStopTimeCorrectionCommand command,
            String correlationId) {
        StopTimeCorrectionPrevalidationResult pre = prevalidate(actor, command);
        if (!pre.businessRulesValid() || !pre.readyForSubmission()) {
            throw new DomainValidationException("Stop-time validation proposal does not satisfy Spec 04 business rules");
        }
        ValidationAuditEvidenceView evidence = auditEvidence(actor);
        if (evidence.canonicalUserId() == null || !Boolean.TRUE.equals(evidence.accountActiveSnapshot())) {
            throw new AuthorizationException("Canonical active app_security.user_identity is required to persist a validation");
        }
        PlantContext plant = context.findPlant(command.plantId())
                .orElseThrow(() -> new IllegalStateException("Plant not found"));
        ZoneId zone = ZoneId.of(plant.timezoneName());
        Instant now = clock.instant();
        Instant expiresAt = ZonedDateTime.now(clock.withZone(zone)).toLocalDate().plusDays(1)
                .atStartOfDay(zone).minusSeconds(1).toInstant();
        if (expiresAt.isBefore(now)) expiresAt = now;
        CorrectionJustificationView j = pre.justification();
        StopTimeValidationPersistenceRequest request = new StopTimeValidationPersistenceRequest(
                command.plantId(), command.lineId(), command.businessDate(), command.stopClassificationId(),
                command.originalSeconds(), command.proposedSeconds(), command.reasonType(), null,
                j == null ? null : j.operationalCause(), j == null ? null : j.actionPlan(),
                j == null ? null : j.incidentNumber(), evidence.canonicalUserId(), now, expiresAt, correlationId);
        return validationPersistence.createStopTime(request);
    }

    @Override
    public StopTimeValidationResult getStopTimeValidation(ActorContext actor, long validationId) {
        if (validationId <= 0) throw new IllegalArgumentException("validationId must be positive");
        StopTimeValidationResult result = validationPersistence.findStopTimeById(validationId)
                .orElseThrow(() -> new ValidationNotFoundException("Stop-time validation not found"));
        List<ValidationIssue> issues = new ArrayList<>();
        validateModuleEntitlement(actor, issues);
        validateSpecificKpiAssignment(actor, result.plantId(), issues);
        validateFeatureActivation(result.plantId(), issues);
        if (issues.stream().anyMatch(i -> i.severity() == Severity.BLOCKER)) {
            throw new AuthorizationException("Actor is not authorized for KPI OEE validation");
        }
        if (!accessScope.hasPlantAccess(actor.tenantId(), actor.objectId(), result.plantId())) {
            throw new AuthorizationException("Actor is not authorized for the validation plant scope");
        }
        if (result.lineId() != null && !context.lineBelongsToPlant(result.lineId(), result.plantId())) {
            throw new AuthorizationException("Validation line is outside the authorized plant");
        }
        return result;
    }

    @Override
    public AuditPolicyView getAuditPolicy() {
        return new AuditPolicyView(
                "ENTRA_TENANT_ID_PLUS_OBJECT_ID",
                actorIdentity.canonicalUserIdAvailable() ? "IMPLEMENTED_APP_SECURITY_USER_ID" : "NOT_AVAILABLE_IN_CURRENT_ADAPTER",
                actorIdentity.source(),
                List.of("displayNameSnapshot", "principalNameSnapshot"),
                "APP_SECURITY_SNAPSHOT_THEN_AUTHENTICATION_CLAIM_FALLBACK",
                "ABSOLUTE_INSTANT_UTC",
                "PENDING_PRESENTATION_TIMEZONE_DEFINITION",
                validationPersistence.available() ? "IMPLEMENTED_KPI_VALIDATION_AUDIT_TRIGGERS" : "UNAVAILABLE",
                validationPersistence.available() ? "CANONICAL_USER_AND_TIMESTAMP_PERSISTED" : "PREVALIDATION_ONLY",
                "PENDING_DEFINITION",
                "APPROVAL_AND_REPORTING_RECONCILIATION_OWNED_BY_DEPENDENT_MODULES");
    }

    @Override
    public OeeValidationResult submit(ActorContext actor, PrevalidateOeeCorrectionCommand command, String correlationId) {
        PrevalidationResult pre = prevalidate(actor, command);
        if (!pre.businessRulesValid() || !pre.readyForSubmission()) {
            throw new DomainValidationException("OEE validation proposal does not satisfy Spec 01 business rules");
        }
        ValidationAuditEvidenceView evidence = auditEvidence(actor);
        if (evidence.canonicalUserId() == null || !Boolean.TRUE.equals(evidence.accountActiveSnapshot())) {
            throw new AuthorizationException("Canonical active app_security.user_identity is required to persist a validation");
        }
        long kpiResultId = parseKpiResultId(pre.sourceRecordKey());
        if (kpiResultId <= 0) throw new IllegalStateException("Canonical kpi_result_id could not be resolved");
        PlantContext plant = context.findPlant(command.plantId()).orElseThrow(() -> new IllegalStateException("Plant not found"));
        ZoneId zone = ZoneId.of(plant.timezoneName());
        Instant now = clock.instant();
        Instant expiresAt = ZonedDateTime.now(clock.withZone(zone)).toLocalDate().plusDays(1).atStartOfDay(zone).minusSeconds(1).toInstant();
        if (expiresAt.isBefore(now)) expiresAt = now;
        CorrectionJustificationView j = pre.justification();
        ValidationPersistenceRequest request = new ValidationPersistenceRequest(
                command.plantId(), command.lineId(), command.businessDate(), command.metricCode().trim(), kpiResultId,
                pre.originalValue(), command.proposedValue(), null, command.reasonType(), null,
                j == null ? null : j.operationalCause(), j == null ? null : j.actionPlan(),
                j == null ? null : j.incidentNumber(), evidence.canonicalUserId(), now, expiresAt, correlationId);
        return validationPersistence.create(request);
    }

    @Override
    public OeeValidationResult get(ActorContext actor, long validationId) {
        if (validationId <= 0) throw new IllegalArgumentException("validationId must be positive");
        OeeValidationResult result = validationPersistence.findById(validationId)
                .orElseThrow(() -> new ValidationNotFoundException("Validation not found"));
        List<ValidationIssue> issues = new ArrayList<>();
        validateModuleEntitlement(actor, issues);
        validateSpecificKpiAssignment(actor, result.plantId(), issues);
        validateFeatureActivation(result.plantId(), issues);
        if (issues.stream().anyMatch(i -> i.severity() == Severity.BLOCKER)) {
            throw new AuthorizationException("Actor is not authorized for KPI OEE validation");
        }
        if (!accessScope.hasPlantAccess(actor.tenantId(), actor.objectId(), result.plantId())) {
            throw new AuthorizationException("Actor is not authorized for the validation plant scope");
        }
        if (result.lineId() != null && !context.lineBelongsToPlant(result.lineId(), result.plantId())) {
            throw new AuthorizationException("Validation line is outside the authorized plant");
        }
        return result;
    }

    private static long parseKpiResultId(String sourceRecordKey) {
        if (sourceRecordKey == null) return -1;
        int idx = sourceRecordKey.lastIndexOf(':');
        if (idx < 0 || idx == sourceRecordKey.length() - 1) return -1;
        try { return Long.parseLong(sourceRecordKey.substring(idx + 1)); }
        catch (NumberFormatException ex) { return -1; }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    @Override
    public WorkflowPolicyView getWorkflowPolicy() {
        return new WorkflowPolicyView(
                Arrays.stream(ValidationWorkflowStatus.values()).map(Enum::name).toList(),
                ValidationWorkflowStatus.PENDING.name(),
                "KPI_RESULT_ID",
                validationPersistence.available() ? "IMPLEMENTED_CANONICAL_KPI_VALIDATION" : "UNAVAILABLE",
                "IMPLEMENTED_SERVICE_TRANSACTION_GUARD_DB_UNIQUE_RECOMMENDED",
                "IMPLEMENTED_POSTGRES_ADVISORY_LOCK",
                "APPROVAL_MODULE_OWNS_APPROVE_REJECT_AND_EFFECTIVE_APPLICATION");
    }

    @Override
    public JustificationPolicyView getJustificationPolicy() {
        boolean anyLength = correctionReasonPolicy.incidentLengthConfigured()
                || correctionReasonPolicy.operationalCauseLengthConfigured()
                || correctionReasonPolicy.actionPlanLengthConfigured();
        boolean allLengths = correctionReasonPolicy.incidentLengthConfigured()
                && correctionReasonPolicy.operationalCauseLengthConfigured()
                && correctionReasonPolicy.actionPlanLengthConfigured();
        return new JustificationPolicyView(
                List.of("OPERATIVE", "SYSTEM"),
                List.of("operationalCause", "actionPlan"),
                List.of("incidentNumber"),
                "IMPLEMENTED_TRIM_LINE_ENDING_AND_CONTROL_CHARACTER_VALIDATION",
                allLengths ? "CONFIGURED_AND_ENFORCED" : (anyLength ? "PARTIALLY_CONFIGURED_AND_ENFORCED" : "PENDING_UX_DEFINITION"),
                correctionReasonPolicy.incidentNumberMaxLengthOrNull(),
                correctionReasonPolicy.operationalCauseMaxLengthOrNull(),
                correctionReasonPolicy.actionPlanMaxLengthOrNull(),
                "UNICODE_CODE_POINTS",
                correctionReasonPolicy.incidentRegexConfigured() ? "CONFIGURED_REGEX_ENFORCED" : "CAPTURE_ONLY_PATTERN_NOT_DEFINED",
                correctionReasonPolicy.incidentNumberRegex(),
                "PENDING_EXTERNAL_INTEGRATION",
                "PENDING_DEFINITION",
                validationPersistence.available() ? "IMPLEMENTED_CANONICAL_KPI_VALIDATION" : "UNAVAILABLE");
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
                new CapabilityView("plantFeatureActivation", featureActivation.configured() ? "IMPLEMENTED_CANONICAL" : "PENDING_REFERENCE_DATA",
                        "Plant feature flag resolved from " + featureActivation.source() + " using code " + featureActivation.configurationCode()),
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
                                ? "Admin write path serializes by plant and rejects a third effective validator; operational requests also fail closed if canonical data is already over limit"
                                : "Validator assignment source is not configured"),
                new CapabilityView("assignmentRevocation", assignments,
                        validatorAssignments.enforcesRoleCapability()
                                ? "Closing user_role_assignment.valid_to revokes the canonical plant assignment without redeploy"
                                : "Remove the external assignment and restart/redeploy"),
                new CapabilityView("assignmentChangeAudit", validatorAssignments.enforcesRoleCapability() && validatorAssignmentAdministration.available() ? "IMPLEMENTED_CANONICAL_AUDIT" : "PENDING_EXTERNAL_INTEGRATION",
                        validatorAssignments.enforcesRoleCapability()
                                ? "Admin writes set audit.change_log session actor/source/correlation; user_role_assignment preserves assigned_by_user_id, reason and validity history"
                                : "Assignment administration is external"),
                new CapabilityView("assignmentPolicyContract", "IMPLEMENTED_SPEC_02",
                        "Spec 02 supports policy, self-scope, admin list, assign, modify and revoke; external approval remains outside GBC but its reference is mandatory on assign/modify"),
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
                new CapabilityView("justificationPersistence", validationPersistence.available() ? "IMPLEMENTED_CANONICAL" : "UNAVAILABLE",
                        "Reason type and applicable incident/cause/action-plan evidence are persisted atomically in kpi.kpi_validation"),
                new CapabilityView("gbSupportIncidentValidation", correctionReasonPolicy.incidentRegexConfigured() ? "IMPLEMENTED_LOCAL_FORMAT_ONLY" : "PENDING_DEFINITION",
                        correctionReasonPolicy.incidentRegexConfigured()
                                ? "Configured incident regex is enforced locally; online validation against GB Support remains pending"
                                : "Incident format and validation against GB Support remain undefined; the service captures any non-blank incidentNumber"),
                new CapabilityView("justificationTextPolicy",
                        (correctionReasonPolicy.incidentLengthConfigured() && correctionReasonPolicy.operationalCauseLengthConfigured() && correctionReasonPolicy.actionPlanLengthConfigured())
                                ? "IMPLEMENTED_CONFIGURABLE_LENGTHS" : "IMPLEMENTED_PARTIAL",
                        "Whitespace/line endings/control characters are normalized and optional per-field Unicode length limits are enforced when configured; sensitive-data policy remains undefined"),
                new CapabilityView("operationalMonitoring", "IMPLEMENTED",
                        "HTTP runtimes log critical consultations/actions and rejected requests with correlation and actor/scope when available"),
                new CapabilityView("stopTimeReferenceByClassification", "IMPLEMENTED_CANONICAL",
                        "Read-only reference clips stops.stop_fragment to the D-1 window and groups by the latest stops.classification_history classification for each event"),
                new CapabilityView("stopTimeOriginalReconciliation", "IMPLEMENTED",
                        "Spec 04 prevalidation re-reads the canonical D-1 aggregate and blocks stale/manipulated originalSeconds"),
                new CapabilityView("stopTimeSignAndFormatValidation", "IMPLEMENTED",
                        "Spec 04 prevalidation rejects missing or negative original/proposed stop-time seconds; zero is valid when observed"),
                new CapabilityView("stopClassificationCatalogValidation", "IMPLEMENTED_CANONICAL_SCOPE_FALLBACK",
                        "stopClassificationId is validated against stops.stop_classification and plant organization/global scope; active scoped classifications are exposed with zero when not observed because v1.2 has no line-specific relation"),
                new CapabilityView("programmedUnprogrammedClassification", "IMPLEMENTED_CANONICAL",
                        "PROGRAMMED/UNPROGRAMMED is read from stops.stop_classification.oee_stop_type_code; no external code/name mapping is required"),
                new CapabilityView("inactiveStopClassificationHistory", "IMPLEMENTED_PROVISIONAL",
                        "A classification observed in D-1 remains visible/prevalidatable even when currently inactive so historical reconciliation does not disappear"),
                new CapabilityView("stopTimeTotalConsistency", "PENDING_FUNCTIONAL_DEFINITION",
                        "Spec 04 explicitly leaves the mathematical maximum/sum rule undefined; no 24-hour rule is inherited"),
                new CapabilityView("stopTimeValidationPersistence", validationPersistence.available() ? "IMPLEMENTED_CANONICAL" : "UNAVAILABLE",
                        "Spec 04 proposals persist as PENDING using kpi.kpi_validation_detail.stop_classification_id with original/proposed values and transactional one-opportunity protection"),
                new CapabilityView("multiClassificationValidation", "IMPLEMENTED_CONSERVATIVE_PENDING_GROUPING_DEFINITION",
                        "Spec 04 leaves grouping open; the service persists one classification per validation header until business defines multi-detail grouping"),
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
                new CapabilityView("validationAuditEvidence", "IMPLEMENTED_CANONICAL",
                        "Canonical app_security.user_id and proposed_at are persisted on kpi.kpi_validation; audit triggers capture actor/source/correlation context"),
                new CapabilityView("auditTimestampSemantics", "IMPLEMENTED",
                        "Spec 07 represents the validation/prevalidation event time as an absolute UTC Instant so a later presentation timezone cannot change the recorded instant"),
                new CapabilityView("persistedAuditReconciliation", "IMPLEMENTED_FOR_VALIDATION_STEP",
                        "Validation proposal actor/timestamp are persisted; Approval/report reconciliation remains owned by their dependent modules"),
                new CapabilityView("oeeOriginalValueSource", "IMPLEMENTED_READ_ONLY_V1_2",
                        "Reads the latest canonical kpi.kpi_result for the requested kpi_definition.kpi_code when its line-level period exactly matches the provisional local D-1 window"),
                new CapabilityView("validationWorkflowContract", "IMPLEMENTED_CANONICAL",
                        "Canonical statuses are PENDING, APPROVED, REJECTED and EXPIRED; new proposals are persisted as PENDING"),
                new CapabilityView("proposalPersistence", validationPersistence.available() ? "IMPLEMENTED_CANONICAL" : "UNAVAILABLE",
                        "Persists kpi.kpi_validation and kpi.kpi_validation_detail atomically"),
                new CapabilityView("singleModificationPerDatum", "IMPLEMENTED_SERVICE_GUARD",
                        "Uses kpi_result_id identity, transactional advisory lock and existing-detail check; a DB UNIQUE constraint is still recommended"),
                new CapabilityView("approvalWorkflow", "PENDING_DEPENDENCY",
                        "KPI Approval remains the second-stage dependency")
        );
    }
}

