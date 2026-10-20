package com.gbconnected.kpivalidation.functions;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import com.google.gson.*;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

public class KpiValidationFunctions {
    private static final String CORRELATION = "X-Correlation-Id";

    @FunctionName("kpi-validation-capabilities")
    public HttpResponseMessage capabilities(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/capabilities", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_validation_capabilities correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.capabilities().capabilities(), correlationId);
    }

    @FunctionName("kpi-validation-justification-policy")
    public HttpResponseMessage justificationPolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/justification/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_justification_policy correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.justificationPolicy().getJustificationPolicy(), correlationId);
    }

    @FunctionName("kpi-validation-audit-policy")
    public HttpResponseMessage auditPolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/audit/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_audit_policy correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.auditPolicy().getAuditPolicy(), correlationId);
    }

    @FunctionName("kpi-validation-workflow-policy")
    public HttpResponseMessage workflowPolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/workflow/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_workflow_policy correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.workflowPolicy().getWorkflowPolicy(), correlationId);
    }

    @FunctionName("kpi-validation-assignment-policy")
    public HttpResponseMessage assignmentPolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/assignments/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_validator_assignment_policy correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.validatorAssignmentPolicy().getValidatorAssignmentPolicy(), correlationId);
    }

    @FunctionName("kpi-validation-my-assignments")
    public HttpResponseMessage assignments(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/assignments/me", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            ValidatorAssignmentSummary result = FunctionRuntime.RUNTIME.validatorAssignments().get(actor);
            ctx.getLogger().info("event=kpi_validator_assignments actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " correlationId=" + correlationId
                    + " enabled=" + result.enabled() + " plantCount=" + result.plantIds().size());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-admin-list-assignments")
    public HttpResponseMessage adminListAssignments(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/assignments", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req, ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plantId = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            var result = FunctionRuntime.RUNTIME.validatorAssignmentAdministration().list(actor, plantId);
            ctx.getLogger().info("event=kpi_validator_assignment_admin_list actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plantId + " correlationId=" + correlationId
                    + " resultCount=" + result.size());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) { return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) { return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) { return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId); }
    }

    @FunctionName("kpi-validation-admin-assign-validator")
    public HttpResponseMessage adminAssignValidator(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/assignments", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req, ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required"))).getAsJsonObject();
            Instant validTo = body.has("validTo") && !body.get("validTo").isJsonNull() ? Instant.parse(body.get("validTo").getAsString()) : null;
            AssignValidatorCommand command = new AssignValidatorCommand(longVal(body,"targetUserId"), longVal(body,"plantId"), validTo, str(body,"externalApprovalReference"), nullable(body,"reason"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            var result = FunctionRuntime.RUNTIME.validatorAssignmentAdministration().assign(actor, command, correlationId);
            ctx.getLogger().info("event=kpi_validator_assignment_created actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " targetUserId=" + command.targetUserId()
                    + " plantId=" + command.plantId() + " correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.CREATED, result, correlationId);
        } catch (SecurityException ex) { return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) { return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (ValidatorAssignmentConflictException ex) { return reject(req, ctx, HttpStatus.CONFLICT, ex.getMessage(), correlationId);
        } catch (Exception ex) { return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId); }
    }

    @FunctionName("kpi-validation-admin-modify-validator")
    public HttpResponseMessage adminModifyValidator(
            @HttpTrigger(name = "req", methods = {HttpMethod.PATCH}, route = "v1/oee-validation/assignments/{targetUserId}/plants/{plantId}", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            @BindingName("targetUserId") String targetUserId, @BindingName("plantId") String plantId, ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required"))).getAsJsonObject();
            Instant validTo = body.has("validTo") && !body.get("validTo").isJsonNull() ? Instant.parse(body.get("validTo").getAsString()) : null;
            ModifyValidatorAssignmentCommand command = new ModifyValidatorAssignmentCommand(requiredLong(targetUserId,"targetUserId"), requiredLong(plantId,"plantId"), validTo, str(body,"externalApprovalReference"), nullable(body,"reason"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            var result = FunctionRuntime.RUNTIME.validatorAssignmentAdministration().modify(actor, command, correlationId);
            ctx.getLogger().info("event=kpi_validator_assignment_modified actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " targetUserId=" + command.targetUserId()
                    + " plantId=" + command.plantId() + " correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) { return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) { return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (ValidatorAssignmentConflictException ex) { return reject(req, ctx, HttpStatus.CONFLICT, ex.getMessage(), correlationId);
        } catch (Exception ex) { return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId); }
    }

    @FunctionName("kpi-validation-admin-revoke-validator")
    public HttpResponseMessage adminRevokeValidator(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/assignments/{targetUserId}/plants/{plantId}/revoke", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            @BindingName("targetUserId") String targetUserId, @BindingName("plantId") String plantId, ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required"))).getAsJsonObject();
            RevokeValidatorAssignmentCommand command = new RevokeValidatorAssignmentCommand(requiredLong(targetUserId,"targetUserId"), requiredLong(plantId,"plantId"), str(body,"reason"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            var result = FunctionRuntime.RUNTIME.validatorAssignmentAdministration().revoke(actor, command, correlationId);
            ctx.getLogger().info("event=kpi_validator_assignment_revoked actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " targetUserId=" + command.targetUserId()
                    + " plantId=" + command.plantId() + " correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) { return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) { return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (ValidatorAssignmentConflictException ex) { return reject(req, ctx, HttpStatus.CONFLICT, ex.getMessage(), correlationId);
        } catch (Exception ex) { return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId); }
    }

    @FunctionName("kpi-validation-temporal-context")
    public HttpResponseMessage temporalContext(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/temporal-context", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plant = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            long line = requiredLong(req.getQueryParameters().get("lineId"), "lineId");
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            TemporalContextResult result = FunctionRuntime.RUNTIME.temporalContext().get(actor, plant, line);
            ctx.getLogger().info("event=kpi_validation_temporal_context actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plant + " lineId=" + line
                    + " correlationId=" + correlationId + " eligibleBusinessDate=" + result.eligibleBusinessDate()
                    + " currentOperationDate=" + result.currentOperationDate());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-eligibility")
    public HttpResponseMessage eligibility(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/eligibility", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plant = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            long line = requiredLong(req.getQueryParameters().get("lineId"), "lineId");
            LocalDate date = LocalDate.parse(required(req.getQueryParameters().get("businessDate"), "businessDate"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            EligibilityResult result = FunctionRuntime.RUNTIME.eligibility().check(actor, plant, line, date);
            ctx.getLogger().info("event=kpi_validation_eligibility actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plant + " lineId=" + line
                    + " businessDate=" + date + " correlationId=" + correlationId + " eligible=" + result.eligible());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-oee-reference")
    public HttpResponseMessage oeeReference(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/oee/reference", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plant = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            long line = requiredLong(req.getQueryParameters().get("lineId"), "lineId");
            LocalDate date = LocalDate.parse(required(req.getQueryParameters().get("businessDate"), "businessDate"));
            String metricCode = required(req.getQueryParameters().get("metricCode"), "metricCode");
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            OeeMetricReferenceResult result = FunctionRuntime.RUNTIME.oeeMetricReference()
                    .getOeeMetricReference(actor, plant, line, date, metricCode);
            ctx.getLogger().info("event=kpi_oee_reference actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plant + " lineId=" + line
                    + " businessDate=" + date + " metricCode=" + metricCode + " correlationId=" + correlationId
                    + " sourceState=" + result.sourceState() + " found=" + (result.originalValue() != null));
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-stop-time-policy")
    public HttpResponseMessage stopTimePolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/stop-times/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        ctx.getLogger().info("event=kpi_stop_time_policy correlationId=" + correlationId + " result=OK");
        return json(req, HttpStatus.OK, FunctionRuntime.RUNTIME.stopTimePolicy().getStopTimePolicy(), correlationId);
    }

    @FunctionName("kpi-validation-stop-time-reference")
    public HttpResponseMessage stopTimeReference(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/stop-times/reference", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plant = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            long line = requiredLong(req.getQueryParameters().get("lineId"), "lineId");
            LocalDate date = LocalDate.parse(required(req.getQueryParameters().get("businessDate"), "businessDate"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            StopTimeReferenceResult result = FunctionRuntime.RUNTIME.stopTimeReference().get(actor, plant, line, date);
            ctx.getLogger().info("event=kpi_stop_time_reference actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plant + " lineId=" + line
                    + " businessDate=" + date + " correlationId=" + correlationId
                    + " entryCount=" + result.entries().size());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-stop-time-prevalidate")
    public HttpResponseMessage stopTimePrevalidate(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/stop-times/prevalidate", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(
                    req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required")))
                    .getAsJsonObject();
            var command = new PrevalidateStopTimeCorrectionCommand(
                    longVal(body, "plantId"),
                    longVal(body, "lineId"),
                    LocalDate.parse(str(body, "businessDate")),
                    longVal(body, "stopClassificationId"),
                    longVal(body, "originalSeconds"),
                    longVal(body, "proposedSeconds"),
                    ValidationReasonType.valueOf(str(body, "reasonType").toUpperCase(Locale.ROOT)),
                    nullable(body, "incidentNumber"),
                    nullable(body, "operationalCause"),
                    nullable(body, "actionPlan"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            StopTimeCorrectionPrevalidationResult result = FunctionRuntime.RUNTIME.stopTimePrevalidation().prevalidate(actor, command);
            ctx.getLogger().info("event=kpi_stop_time_prevalidate actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + command.plantId() + " lineId=" + command.lineId()
                    + " businessDate=" + command.businessDate() + " stopClassificationId=" + command.stopClassificationId()
                    + " reasonType=" + command.reasonType() + " correlationId=" + correlationId + " rulesValid=" + result.businessRulesValid()
                    + " readyForSubmission=" + result.readyForSubmission());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-mass-policy")
    public HttpResponseMessage massPolicy(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/masses/policy", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            MassPerformancePolicyView result = FunctionRuntime.RUNTIME.massPerformancePolicy().getMassPerformancePolicy();
            ctx.getLogger().info("event=kpi_mass_policy correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-mass-reference")
    public HttpResponseMessage massReference(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/masses/reference", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long plant = requiredLong(req.getQueryParameters().get("plantId"), "plantId");
            long line = requiredLong(req.getQueryParameters().get("lineId"), "lineId");
            LocalDate date = LocalDate.parse(required(req.getQueryParameters().get("businessDate"), "businessDate"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            MassPerformanceReferenceResult result = FunctionRuntime.RUNTIME.massPerformanceReference().getMassPerformanceReference(actor, plant, line, date);
            ctx.getLogger().info("event=kpi_mass_reference actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + plant + " lineId=" + line
                    + " businessDate=" + date + " correlationId=" + correlationId
                    + " entryCount=" + result.entries().size());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-mass-prevalidate")
    public HttpResponseMessage massPrevalidate(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/masses/prevalidate", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(
                    req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required")))
                    .getAsJsonObject();
            var command = new PrevalidateMassPerformanceCorrectionCommand(
                    longVal(body, "plantId"),
                    longVal(body, "lineId"),
                    LocalDate.parse(str(body, "businessDate")),
                    longVal(body, "uomId"),
                    MassPerformanceVariable.valueOf(str(body, "variable").toUpperCase(Locale.ROOT)),
                    new BigDecimal(str(body, "originalValue")),
                    new BigDecimal(str(body, "proposedValue")),
                    ValidationReasonType.valueOf(str(body, "reasonType").toUpperCase(Locale.ROOT)),
                    nullable(body, "incidentNumber"),
                    nullable(body, "operationalCause"),
                    nullable(body, "actionPlan"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            MassPerformanceCorrectionPrevalidationResult result = FunctionRuntime.RUNTIME.massPerformancePrevalidation()
                    .prevalidateMassPerformance(actor, command);
            ctx.getLogger().info("event=kpi_mass_prevalidate actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + command.plantId() + " lineId=" + command.lineId()
                    + " businessDate=" + command.businessDate()
                    + " uomId=" + command.uomId() + " variable=" + command.variable()
                    + " reasonType=" + command.reasonType() + " correlationId=" + correlationId + " rulesValid=" + result.businessRulesValid()
                    + " readyForSubmission=" + result.readyForSubmission());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-prevalidate")
    public HttpResponseMessage prevalidate(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/prevalidate", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(
                    req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required")))
                    .getAsJsonObject();
            var command = new PrevalidateOeeCorrectionCommand(
                    longVal(body, "plantId"),
                    longVal(body, "lineId"),
                    LocalDate.parse(str(body, "businessDate")),
                    str(body, "metricCode"),
                    new BigDecimal(str(body, "proposedValue")),
                    ValidationReasonType.valueOf(str(body, "reasonType").toUpperCase(Locale.ROOT)),
                    nullable(body, "incidentNumber"),
                    nullable(body, "operationalCause"),
                    nullable(body, "actionPlan"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            PrevalidationResult result = FunctionRuntime.RUNTIME.prevalidation().prevalidate(actor, command);
            ctx.getLogger().info("event=kpi_validation_prevalidate actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " plantId=" + command.plantId() + " lineId=" + command.lineId()
                    + " businessDate=" + command.businessDate() + " metricCode=" + command.metricCode()
                    + " reasonType=" + command.reasonType() + " correlationId=" + correlationId + " rulesValid=" + result.businessRulesValid()
                    + " readyForSubmission=" + result.readyForSubmission());
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-submit")
    public HttpResponseMessage submit(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "v1/oee-validation/validations", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            JsonObject body = JsonParser.parseString(
                    req.getBody().orElseThrow(() -> new IllegalArgumentException("JSON body is required")))
                    .getAsJsonObject();
            var command = new PrevalidateOeeCorrectionCommand(
                    longVal(body, "plantId"),
                    longVal(body, "lineId"),
                    LocalDate.parse(str(body, "businessDate")),
                    str(body, "metricCode"),
                    new BigDecimal(str(body, "proposedValue")),
                    ValidationReasonType.valueOf(str(body, "reasonType").toUpperCase(Locale.ROOT)),
                    nullable(body, "incidentNumber"),
                    nullable(body, "operationalCause"),
                    nullable(body, "actionPlan"));
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            OeeValidationResult result = FunctionRuntime.RUNTIME.submission().submit(actor, command, correlationId);
            ctx.getLogger().info("event=kpi_validation_created actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " validationId=" + result.validationId()
                    + " plantId=" + result.plantId() + " lineId=" + result.lineId()
                    + " businessDate=" + result.businessDate() + " metricCode=" + result.metricCode()
                    + " status=" + result.status() + " correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.CREATED, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (ValidationAlreadyExistsException ex) {
            return reject(req, ctx, HttpStatus.CONFLICT, ex.getMessage(), correlationId);
        } catch (DomainValidationException ex) {
            return reject(req, ctx, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-detail")
    public HttpResponseMessage validationDetail(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/oee-validation/validations/{validationId}", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> req,
            @BindingName("validationId") String validationId,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        try {
            long id = requiredLong(validationId, "validationId");
            ActorContext actor = FunctionActorContextResolver.resolve(req);
            OeeValidationResult result = FunctionRuntime.RUNTIME.validationDetail().get(actor, id);
            ctx.getLogger().info("event=kpi_validation_detail actorTenant=" + actor.tenantId()
                    + " actorObject=" + actor.objectId() + " validationId=" + id
                    + " plantId=" + result.plantId() + " lineId=" + result.lineId()
                    + " status=" + result.status() + " correlationId=" + correlationId + " result=OK");
            return json(req, HttpStatus.OK, result, correlationId);
        } catch (SecurityException ex) {
            return reject(req, ctx, HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId);
        } catch (AuthorizationException ex) {
            return reject(req, ctx, HttpStatus.FORBIDDEN, ex.getMessage(), correlationId);
        } catch (ValidationNotFoundException ex) {
            return reject(req, ctx, HttpStatus.NOT_FOUND, ex.getMessage(), correlationId);
        } catch (Exception ex) {
            return reject(req, ctx, HttpStatus.BAD_REQUEST, ex.getMessage(), correlationId);
        }
    }

    @FunctionName("kpi-validation-health")
    public HttpResponseMessage health(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "v1/health", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> req,
            ExecutionContext ctx) {
        String correlationId = correlation(req);
        boolean ok = FunctionRuntime.RUNTIME.health().healthy();
        ctx.getLogger().info("event=kpi_validation_health correlationId=" + correlationId + " status=" + (ok ? "UP" : "DOWN"));
        return json(req, ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE,
                Map.of("status", ok ? "UP" : "DOWN", "database", ok), correlationId);
    }

    private static HttpResponseMessage reject(HttpRequestMessage<?> request, ExecutionContext ctx,
            HttpStatus status, String message, String correlationId) {
        String safe = message == null || message.isBlank() ? status.name() : message;
        ctx.getLogger().warning("event=kpi_validation_request_rejected correlationId=" + correlationId
                + " status=" + status.value() + " reason=" + safe);
        return json(request, status, Map.of("error", safe), correlationId);
    }

    private static HttpResponseMessage json(HttpRequestMessage<?> request, HttpStatus status, Object body,
            String correlationId) {
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .header(CORRELATION, correlationId)
                .body(JsonCodec.GSON.toJson(body))
                .build();
    }

    private static String correlation(HttpRequestMessage<?> request) {
        return request.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(CORRELATION))
                .map(Map.Entry::getValue)
                .findFirst()
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static long requiredLong(String value, String name) {
        return Long.parseLong(required(value, name));
    }

    private static long longVal(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException(name + " is required");
        return object.get(name).getAsLong();
    }

    private static String str(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException(name + " is required");
        return object.get(name).getAsString();
    }

    private static String nullable(JsonObject object, String name) {
        return !object.has(name) || object.get(name).isJsonNull() ? null : object.get(name).getAsString();
    }
}
