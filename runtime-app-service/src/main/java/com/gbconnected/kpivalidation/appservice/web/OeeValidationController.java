package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.application.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/oee-validation", "/api/oee-validation"})
public class OeeValidationController {
    private static final Logger LOG = LoggerFactory.getLogger(OeeValidationController.class);

    private final CheckEligibilityUseCase eligibility;
    private final PrevalidateOeeCorrectionUseCase prevalidation;
    private final SubmitOeeValidationUseCase submission;
    private final GetOeeValidationUseCase validationDetail;
    private final CapabilitiesUseCase capabilities;
    private final GetMyValidatorAssignmentsUseCase validatorAssignments;
    private final GetValidatorAssignmentPolicyUseCase validatorAssignmentPolicy;
    private final AdministerValidatorAssignmentsUseCase validatorAssignmentAdministration;
    private final GetTemporalContextUseCase temporalContext;
    private final GetOeeMetricReferenceUseCase oeeMetricReference;
    private final GetStopTimeReferenceUseCase stopTimeReference;
    private final GetStopTimePolicyUseCase stopTimePolicy;
    private final PrevalidateStopTimeCorrectionUseCase stopTimePrevalidation;
    private final GetMassPerformanceReferenceUseCase massPerformanceReference;
    private final GetMassPerformancePolicyUseCase massPerformancePolicy;
    private final PrevalidateMassPerformanceCorrectionUseCase massPerformancePrevalidation;
    private final GetJustificationPolicyUseCase justificationPolicy;
    private final GetAuditPolicyUseCase auditPolicy;
    private final GetWorkflowPolicyUseCase workflowPolicy;

    public OeeValidationController(CheckEligibilityUseCase eligibility,
            PrevalidateOeeCorrectionUseCase prevalidation,
            SubmitOeeValidationUseCase submission,
            GetOeeValidationUseCase validationDetail,
            CapabilitiesUseCase capabilities,
            GetMyValidatorAssignmentsUseCase validatorAssignments,
            GetValidatorAssignmentPolicyUseCase validatorAssignmentPolicy,
            AdministerValidatorAssignmentsUseCase validatorAssignmentAdministration,
            GetTemporalContextUseCase temporalContext,
            GetOeeMetricReferenceUseCase oeeMetricReference,
            GetStopTimeReferenceUseCase stopTimeReference,
            GetStopTimePolicyUseCase stopTimePolicy,
            PrevalidateStopTimeCorrectionUseCase stopTimePrevalidation,
            GetMassPerformanceReferenceUseCase massPerformanceReference,
            GetMassPerformancePolicyUseCase massPerformancePolicy,
            PrevalidateMassPerformanceCorrectionUseCase massPerformancePrevalidation,
            GetJustificationPolicyUseCase justificationPolicy,
            GetAuditPolicyUseCase auditPolicy,
            GetWorkflowPolicyUseCase workflowPolicy) {
        this.eligibility = eligibility;
        this.prevalidation = prevalidation;
        this.submission = submission;
        this.validationDetail = validationDetail;
        this.capabilities = capabilities;
        this.validatorAssignments = validatorAssignments;
        this.validatorAssignmentPolicy = validatorAssignmentPolicy;
        this.validatorAssignmentAdministration = validatorAssignmentAdministration;
        this.temporalContext = temporalContext;
        this.oeeMetricReference = oeeMetricReference;
        this.stopTimeReference = stopTimeReference;
        this.stopTimePolicy = stopTimePolicy;
        this.stopTimePrevalidation = stopTimePrevalidation;
        this.massPerformanceReference = massPerformanceReference;
        this.massPerformancePolicy = massPerformancePolicy;
        this.massPerformancePrevalidation = massPerformancePrevalidation;
        this.justificationPolicy = justificationPolicy;
        this.auditPolicy = auditPolicy;
        this.workflowPolicy = workflowPolicy;
    }

    @GetMapping("/capabilities")
    public List<CapabilityView> capabilities() {
        LOG.info("event=kpi_validation_capabilities correlationId={} result=OK", correlationId());
        return capabilities.capabilities();
    }

    @GetMapping("/justification/policy")
    public JustificationPolicyView justificationPolicy() {
        LOG.info("event=kpi_justification_policy correlationId={} result=OK", correlationId());
        return justificationPolicy.getJustificationPolicy();
    }

    @GetMapping("/audit/policy")
    public AuditPolicyView auditPolicy() {
        LOG.info("event=kpi_audit_policy correlationId={} result=OK", correlationId());
        return auditPolicy.getAuditPolicy();
    }

    @GetMapping("/workflow/policy")
    public WorkflowPolicyView workflowPolicy() {
        LOG.info("event=kpi_workflow_policy correlationId={} result=OK", correlationId());
        return workflowPolicy.getWorkflowPolicy();
    }

    @GetMapping("/assignments/policy")
    public ValidatorAssignmentPolicyView assignmentPolicy() {
        LOG.info("event=kpi_validator_assignment_policy correlationId={} result=OK", correlationId());
        return validatorAssignmentPolicy.getValidatorAssignmentPolicy();
    }

    @GetMapping("/assignments")
    public List<ValidatorAssignmentAdminView> plantAssignments(@AuthenticationPrincipal Jwt jwt, @RequestParam long plantId) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        List<ValidatorAssignmentAdminView> result = validatorAssignmentAdministration.list(actor, plantId);
        LOG.info("event=kpi_validator_assignment_admin_list actorTenant={} actorObject={} plantId={} correlationId={} resultCount={}",
                actor.tenantId(), actor.objectId(), plantId, correlationId(), result.size());
        return result;
    }

    @PostMapping("/assignments")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ValidatorAssignmentChangeResult assignValidator(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ValidatorAssignmentHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        ValidatorAssignmentChangeResult result = validatorAssignmentAdministration.assign(actor,
                new AssignValidatorCommand(request.targetUserId(), request.plantId(), request.validTo(),
                        request.externalApprovalReference(), request.reason()), correlationId());
        LOG.info("event=kpi_validator_assignment_created actorTenant={} actorObject={} targetUserId={} plantId={} correlationId={} result=OK",
                actor.tenantId(), actor.objectId(), request.targetUserId(), request.plantId(), correlationId());
        return result;
    }

    @PatchMapping("/assignments/{targetUserId}/plants/{plantId}")
    public ValidatorAssignmentChangeResult modifyValidator(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long targetUserId, @PathVariable long plantId,
            @Valid @RequestBody ValidatorAssignmentModifyHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        ValidatorAssignmentChangeResult result = validatorAssignmentAdministration.modify(actor, new ModifyValidatorAssignmentCommand(targetUserId, plantId,
                request.validTo(), request.externalApprovalReference(), request.reason()), correlationId());
        LOG.info("event=kpi_validator_assignment_modified actorTenant={} actorObject={} targetUserId={} plantId={} correlationId={} result=OK",
                actor.tenantId(), actor.objectId(), targetUserId, plantId, correlationId());
        return result;
    }

    @PostMapping("/assignments/{targetUserId}/plants/{plantId}/revoke")
    public ValidatorAssignmentChangeResult revokeValidator(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long targetUserId, @PathVariable long plantId,
            @Valid @RequestBody ValidatorAssignmentRevokeHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        ValidatorAssignmentChangeResult result = validatorAssignmentAdministration.revoke(actor, new RevokeValidatorAssignmentCommand(targetUserId, plantId, request.reason()), correlationId());
        LOG.info("event=kpi_validator_assignment_revoked actorTenant={} actorObject={} targetUserId={} plantId={} correlationId={} result=OK",
                actor.tenantId(), actor.objectId(), targetUserId, plantId, correlationId());
        return result;
    }

    @GetMapping("/assignments/me")
    public ValidatorAssignmentSummary assignments(@AuthenticationPrincipal Jwt jwt) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        ValidatorAssignmentSummary result = validatorAssignments.get(actor);
        LOG.info("event=kpi_validator_assignments actorTenant={} actorObject={} correlationId={} enabled={} plantCount={}",
                actor.tenantId(), actor.objectId(), correlationId(), result.enabled(), result.plantIds().size());
        return result;
    }

    @GetMapping("/temporal-context")
    public TemporalContextResult temporalContext(@AuthenticationPrincipal Jwt jwt,
            @RequestParam long plantId,
            @RequestParam long lineId) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        TemporalContextResult result = temporalContext.get(actor, plantId, lineId);
        LOG.info("event=kpi_validation_temporal_context actorTenant={} actorObject={} plantId={} lineId={} correlationId={} eligibleBusinessDate={} currentOperationDate={}",
                actor.tenantId(), actor.objectId(), plantId, lineId, correlationId(),
                result.eligibleBusinessDate(), result.currentOperationDate());
        return result;
    }

    @GetMapping("/eligibility")
    public EligibilityResult eligibility(@AuthenticationPrincipal Jwt jwt,
            @RequestParam long plantId,
            @RequestParam long lineId,
            @RequestParam LocalDate businessDate) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        EligibilityResult result = eligibility.check(actor, plantId, lineId, businessDate);
        LOG.info("event=kpi_validation_eligibility actorTenant={} actorObject={} plantId={} lineId={} businessDate={} correlationId={} eligible={}",
                actor.tenantId(), actor.objectId(), plantId, lineId, businessDate, correlationId(), result.eligible());
        return result;
    }

    @GetMapping("/oee/reference")
    public OeeMetricReferenceResult oeeReference(@AuthenticationPrincipal Jwt jwt,
            @RequestParam long plantId,
            @RequestParam long lineId,
            @RequestParam LocalDate businessDate,
            @RequestParam String metricCode) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        OeeMetricReferenceResult result = oeeMetricReference.getOeeMetricReference(
                actor, plantId, lineId, businessDate, metricCode);
        LOG.info("event=kpi_oee_reference actorTenant={} actorObject={} plantId={} lineId={} businessDate={} metricCode={} correlationId={} sourceState={} found={}",
                actor.tenantId(), actor.objectId(), plantId, lineId, businessDate, metricCode, correlationId(),
                result.sourceState(), result.originalValue() != null);
        return result;
    }

    @GetMapping("/stop-times/policy")
    public StopTimePolicyView stopTimePolicy() {
        LOG.info("event=kpi_stop_time_policy correlationId={} result=OK", correlationId());
        return stopTimePolicy.getStopTimePolicy();
    }

    @GetMapping("/stop-times/reference")
    public StopTimeReferenceResult stopTimeReference(@AuthenticationPrincipal Jwt jwt,
            @RequestParam long plantId,
            @RequestParam long lineId,
            @RequestParam LocalDate businessDate) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        StopTimeReferenceResult result = stopTimeReference.get(actor, plantId, lineId, businessDate);
        LOG.info("event=kpi_stop_time_reference actorTenant={} actorObject={} plantId={} lineId={} businessDate={} correlationId={} entryCount={}",
                actor.tenantId(), actor.objectId(), plantId, lineId, businessDate, correlationId(), result.entries().size());
        return result;
    }

    @PostMapping("/stop-times/prevalidate")
    public StopTimeCorrectionPrevalidationResult prevalidateStopTime(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StopTimePrevalidationHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        StopTimeCorrectionPrevalidationResult result = stopTimePrevalidation.prevalidate(actor,
                new PrevalidateStopTimeCorrectionCommand(
                        request.plantId(), request.lineId(), request.businessDate(), request.stopClassificationId(),
                        request.originalSeconds(), request.proposedSeconds(), request.reasonType(),
                        request.incidentNumber(), request.operationalCause(), request.actionPlan()));
        LOG.info("event=kpi_stop_time_prevalidate actorTenant={} actorObject={} plantId={} lineId={} businessDate={} stopClassificationId={} reasonType={} correlationId={} rulesValid={} readyForSubmission={}",
                actor.tenantId(), actor.objectId(), request.plantId(), request.lineId(), request.businessDate(),
                request.stopClassificationId(), request.reasonType(), correlationId(), result.businessRulesValid(), result.readyForSubmission());
        return result;
    }

    @GetMapping("/masses/policy")
    public MassPerformancePolicyView massPolicy() {
        LOG.info("event=kpi_mass_policy correlationId={} result=OK", correlationId());
        return massPerformancePolicy.getMassPerformancePolicy();
    }

    @GetMapping("/masses/reference")
    public MassPerformanceReferenceResult massReference(@AuthenticationPrincipal Jwt jwt,
            @RequestParam long plantId,
            @RequestParam long lineId,
            @RequestParam LocalDate businessDate) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        MassPerformanceReferenceResult result = massPerformanceReference.getMassPerformanceReference(actor, plantId, lineId, businessDate);
        LOG.info("event=kpi_mass_reference actorTenant={} actorObject={} plantId={} lineId={} businessDate={} correlationId={} entryCount={}",
                actor.tenantId(), actor.objectId(), plantId, lineId, businessDate, correlationId(), result.entries().size());
        return result;
    }

    @PostMapping("/masses/prevalidate")
    public MassPerformanceCorrectionPrevalidationResult prevalidateMass(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MassPerformancePrevalidationHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        MassPerformanceCorrectionPrevalidationResult result = massPerformancePrevalidation.prevalidateMassPerformance(
                actor, new PrevalidateMassPerformanceCorrectionCommand(
                        request.plantId(), request.lineId(), request.businessDate(), request.uomId(),
                        request.variable(), request.originalValue(), request.proposedValue(), request.reasonType(),
                        request.incidentNumber(), request.operationalCause(), request.actionPlan()));
        LOG.info("event=kpi_mass_prevalidate actorTenant={} actorObject={} plantId={} lineId={} businessDate={} uomId={} variable={} reasonType={} correlationId={} rulesValid={} readyForSubmission={}",
                actor.tenantId(), actor.objectId(), request.plantId(), request.lineId(), request.businessDate(),
                request.uomId(), request.variable(), request.reasonType(), correlationId(), result.businessRulesValid(),
                result.readyForSubmission());
        return result;
    }

    @PostMapping("/prevalidate")
    public PrevalidationResult prevalidate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PrevalidationHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        PrevalidationResult result = prevalidation.prevalidate(
                actor,
                new PrevalidateOeeCorrectionCommand(
                        request.plantId(), request.lineId(), request.businessDate(), request.metricCode(),
                        request.proposedValue(), request.reasonType(), request.incidentNumber(),
                        request.operationalCause(), request.actionPlan()));
        LOG.info("event=kpi_validation_prevalidate actorTenant={} actorObject={} plantId={} lineId={} businessDate={} metricCode={} reasonType={} correlationId={} rulesValid={} readyForSubmission={}",
                actor.tenantId(), actor.objectId(), request.plantId(), request.lineId(), request.businessDate(),
                request.metricCode(), request.reasonType(), correlationId(), result.businessRulesValid(), result.readyForSubmission());
        return result;
    }


    @PostMapping("/validations")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public OeeValidationResult submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PrevalidationHttpRequest request) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        OeeValidationResult result = submission.submit(actor,
                new PrevalidateOeeCorrectionCommand(
                        request.plantId(), request.lineId(), request.businessDate(), request.metricCode(),
                        request.proposedValue(), request.reasonType(), request.incidentNumber(),
                        request.operationalCause(), request.actionPlan()), correlationId());
        LOG.info("event=kpi_validation_created actorTenant={} actorObject={} validationId={} plantId={} lineId={} businessDate={} metricCode={} status={} correlationId={} result=OK",
                actor.tenantId(), actor.objectId(), result.validationId(), result.plantId(), result.lineId(),
                result.businessDate(), result.metricCode(), result.status(), correlationId());
        return result;
    }

    @GetMapping("/validations/{validationId}")
    public OeeValidationResult validationDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable long validationId) {
        ActorContext actor = RequestActorContextResolver.resolve(jwt);
        OeeValidationResult result = validationDetail.get(actor, validationId);
        LOG.info("event=kpi_validation_detail actorTenant={} actorObject={} validationId={} plantId={} lineId={} status={} correlationId={} result=OK",
                actor.tenantId(), actor.objectId(), validationId, result.plantId(), result.lineId(), result.status(), correlationId());
        return result;
    }

    private static String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? "" : value;
    }
}

