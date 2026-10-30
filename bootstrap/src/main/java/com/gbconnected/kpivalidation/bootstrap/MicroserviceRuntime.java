package com.gbconnected.kpivalidation.bootstrap;

import com.gbconnected.kpivalidation.adapter.memory.InMemoryOperationalContextAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryStopTimeReferenceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryMassPerformanceReferenceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryOeeMetricSourceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryActorIdentityAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryValidationPersistenceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.manual.*;
import com.gbconnected.kpivalidation.adapter.memory.approval.*;
import com.gbconnected.kpivalidation.adapter.postgres.*;
import com.gbconnected.kpivalidation.adapter.postgres.manual.*;
import com.gbconnected.kpivalidation.adapter.postgres.approval.*;
import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.application.manual.*;
import com.gbconnected.kpivalidation.application.manual.scope.*;
import com.gbconnected.kpivalidation.application.approval.*;
import com.gbconnected.kpivalidation.application.approval.scope.*;
import com.gbconnected.kpivalidation.application.port.out.manual.*;
import com.gbconnected.kpivalidation.application.port.out.approval.*;
import com.gbconnected.kpivalidation.domain.CorrectionReasonPolicy;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;

public final class MicroserviceRuntime implements AutoCloseable {
    private final OeeValidationService validation;
    private final DatabaseHealthPort health;
    private final ManualKpiUseCase manualKpis;
    private final ManualKpiScopeUseCase manualKpiScope;
    private final AdministerManualKpiScopeUseCase manualKpiScopeAdministration;
    private final KpiApprovalUseCase kpiApproval;
    private final AdministerKpiApprovalScopeUseCase kpiApprovalScopeAdministration;
    private final AutoCloseable closeable;

    private MicroserviceRuntime(OeeValidationService validation, DatabaseHealthPort health, ManualKpiUseCase manualKpis,
            ManualKpiScopeUseCase manualKpiScope, AdministerManualKpiScopeUseCase manualKpiScopeAdministration,
            KpiApprovalUseCase kpiApproval, AdministerKpiApprovalScopeUseCase kpiApprovalScopeAdministration, AutoCloseable closeable) {
        this.validation = validation;
        this.health = health;
        this.manualKpis = manualKpis;
        this.manualKpiScope = manualKpiScope;
        this.manualKpiScopeAdministration = manualKpiScopeAdministration;
        this.kpiApproval = kpiApproval;
        this.kpiApprovalScopeAdministration = kpiApprovalScopeAdministration;
        this.closeable = closeable;
    }

    public static MicroserviceRuntime fromEnvironment(Map<String, String> env) {
        DatabaseSettings settings = DatabaseSettings.from(env);
        DatabaseHealthPort health;
        AccessScopePort access;
        OperationalContextPort context;
        StopTimeReferencePort stopTimes;
        MassPerformanceReferencePort massPerformance;
        OeeMetricSourcePort oeeMetrics;
        ActorIdentityPort actorIdentity;
        ValidationPersistencePort validationPersistence;
        FeatureActivationPort featureActivation;
        ValidatorAssignmentAdministrationPort assignmentAdministration = ValidatorAssignmentAdministrationPort.unavailable();
        ManualKpiAuthorizationPort manualAuthorization;
        ManualKpiReferencePort manualReferences;
        ManualKpiPersistencePort manualPersistence;
        FeatureActivationPort manualFeatureActivation;
        ManualKpiScopePort manualScope;
        ManualKpiScopeAdministrationPort manualScopeAdministration = ManualKpiScopeAdministrationPort.unavailable();
        KpiApprovalPersistencePort approvalPersistence;
        KpiApprovalAuthorizationPort approvalAuthorization;
        FeatureActivationPort approvalFeatureActivation;
        KpiApprovalScopeAdministrationPort approvalScopeAdministration = KpiApprovalScopeAdministrationPort.unavailable();
        AutoCloseable close = () -> { };
        DataSource sqlDataSource = null;
        String massSourceMode = env.getOrDefault("GBC_MASS_PERFORMANCE_SOURCE_MODE", "AUTO");
        String elaboratedMetricCode = env.getOrDefault("GBC_ELABORATED_MASS_METRIC_CODE", "ELABORATED_MASS");
        String deliveredMetricCode = env.getOrDefault("GBC_DELIVERED_MASS_METRIC_CODE", "DELIVERED_MASS");

        switch (settings.mode()) {
            case "memory" -> {
                UUID tenant = UUID.fromString(env.getOrDefault("DEV_TENANT_ID", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
                UUID user = UUID.fromString(env.getOrDefault("DEV_USER_OID", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
                long plant = Long.parseLong(env.getOrDefault("DEV_PLANT_ID", "1"));
                long line = Long.parseLong(env.getOrDefault("DEV_LINE_ID", "1"));
                String timezone = env.getOrDefault("DEV_PLANT_TIMEZONE", "America/Mexico_City");
                var memory = new InMemoryOperationalContextAdapter(tenant, user, plant, line, timezone);
                access = memory;
                context = memory;
                health = memory;
                stopTimes = new InMemoryStopTimeReferenceAdapter();
                massPerformance = new InMemoryMassPerformanceReferenceAdapter();
                oeeMetrics = new InMemoryOeeMetricSourceAdapter();
                actorIdentity = new InMemoryActorIdentityAdapter(tenant, user, Long.parseLong(env.getOrDefault("DEV_CANONICAL_USER_ID", "1")));
                validationPersistence = new InMemoryValidationPersistenceAdapter();
                approvalPersistence = (KpiApprovalPersistencePort) validationPersistence;
                var memoryApprovalAuthorization = new InMemoryKpiApprovalAuthorizationAdapter(tenant, user,
                        Long.parseLong(env.getOrDefault("DEV_CANONICAL_USER_ID", "1")), plant);
                approvalAuthorization = memoryApprovalAuthorization;
                approvalFeatureActivation = FeatureActivationPort.enabledByDefault(
                        env.getOrDefault("GBC_KPI_APPROVAL_FEATURE_CODE", "KPI_APPROVAL_ENABLED"));
                approvalScopeAdministration = new InMemoryKpiApprovalScopeAdministrationAdapter(tenant, user,
                        Long.parseLong(env.getOrDefault("DEV_CANONICAL_USER_ID", "1")), plant,
                        env.getOrDefault("GBC_KPI_APPROVAL_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_APPROVAL_SCOPE_ADMIN"),
                        memoryApprovalAuthorization);
                featureActivation = FeatureActivationPort.enabledByDefault(env.getOrDefault("GBC_KPI_VALIDATION_FEATURE_CODE", "KPI_OEE_VALIDATION_ENABLED"));
                String manualCapability = env.getOrDefault("GBC_MANUAL_KPI_CAPABILITY_CODE", "KPI_MANUAL_ENTRY");
                var memoryManualAuthorization = new InMemoryManualKpiAuthorizationAdapter(tenant, user,
                        Long.parseLong(env.getOrDefault("DEV_CANONICAL_USER_ID", "1")), plant, manualCapability);
                manualAuthorization = memoryManualAuthorization;
                manualReferences = new InMemoryManualKpiReferenceAdapter();
                manualPersistence = new InMemoryManualKpiPersistenceAdapter();
                manualFeatureActivation = FeatureActivationPort.enabledByDefault(env.getOrDefault("GBC_MANUAL_KPI_FEATURE_CODE", "KPI_MANUAL_ENTRY_ENABLED"));
                manualScope = new InMemoryManualKpiScopeAdapter(tenant, user, plant, line, timezone);
                manualScopeAdministration = new InMemoryManualKpiScopeAdministrationAdapter(tenant, user,
                        Long.parseLong(env.getOrDefault("DEV_CANONICAL_USER_ID", "1")), plant,
                        env.getOrDefault("GBC_MANUAL_KPI_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_MANUAL_SCOPE_ADMIN"),
                        memoryManualAuthorization);
            }
            case "postgres-password" -> {
                HikariDataSource ds = PooledDataSourceFactory.createPasswordDataSource(settings);
                var jdbc = new JdbcOperationalContextAdapter(ds);
                access = jdbc;
                context = jdbc;
                health = jdbc;
                stopTimes = new JdbcStopTimeReferenceAdapter(ds);
                massPerformance = new JdbcMassPerformanceReferenceAdapter(ds, massSourceMode, elaboratedMetricCode, deliveredMetricCode);
                oeeMetrics = new JdbcOeeMetricSourceAdapter(ds);
                actorIdentity = new JdbcActorIdentityAdapter(ds);
                validationPersistence = new JdbcValidationPersistenceAdapter(ds, elaboratedMetricCode, deliveredMetricCode);
                approvalPersistence = new JdbcKpiApprovalPersistenceAdapter(ds);
                approvalAuthorization = new JdbcKpiApprovalAuthorizationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVER_CAPABILITY_CODE", "KPI_APPROVAL"));
                approvalFeatureActivation = new JdbcFeatureActivationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVAL_FEATURE_CODE", "KPI_APPROVAL_ENABLED"));
                approvalScopeAdministration = new JdbcKpiApprovalScopeAdministrationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVER_ROLE_CODE", "KPI_APPROVER"),
                        env.getOrDefault("GBC_KPI_APPROVER_CAPABILITY_CODE", "KPI_APPROVAL"),
                        env.getOrDefault("GBC_KPI_APPROVAL_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_APPROVAL_SCOPE_ADMIN"));
                featureActivation = new JdbcFeatureActivationAdapter(ds, env.getOrDefault("GBC_KPI_VALIDATION_FEATURE_CODE", "KPI_OEE_VALIDATION_ENABLED"));
                manualAuthorization = new JdbcManualKpiAuthorizationAdapter(ds, env.getOrDefault("GBC_MANUAL_KPI_CAPABILITY_CODE", "KPI_MANUAL_ENTRY"));
                manualReferences = new JdbcManualKpiReferenceAdapter(ds);
                manualPersistence = new JdbcManualKpiPersistenceAdapter(ds, requiredEnv(env, "GBC_MANUAL_KPI_CAPTURE_KPI_CODE"));
                manualFeatureActivation = new JdbcFeatureActivationAdapter(ds, env.getOrDefault("GBC_MANUAL_KPI_FEATURE_CODE", "KPI_MANUAL_ENTRY_ENABLED"));
                manualScope = new JdbcManualKpiScopeAdapter(ds);
                manualScopeAdministration = new JdbcManualKpiScopeAdministrationAdapter(ds,
                        env.getOrDefault("GBC_MANUAL_KPI_ROLE_CODE", "KPI_MANUAL_SUPERUSER"),
                        env.getOrDefault("GBC_MANUAL_KPI_CAPABILITY_CODE", "KPI_MANUAL_ENTRY"),
                        env.getOrDefault("GBC_MANUAL_KPI_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_MANUAL_SCOPE_ADMIN"));
                close = ds;
                sqlDataSource = ds;
            }
            case "lakebase-oauth" -> {
                HikariDataSource ds = PooledDataSourceFactory.createLakebaseOAuthDataSource(settings, env);
                var jdbc = new JdbcOperationalContextAdapter(ds);
                access = jdbc;
                context = jdbc;
                health = jdbc;
                stopTimes = new JdbcStopTimeReferenceAdapter(ds);
                massPerformance = new JdbcMassPerformanceReferenceAdapter(ds, massSourceMode, elaboratedMetricCode, deliveredMetricCode);
                oeeMetrics = new JdbcOeeMetricSourceAdapter(ds);
                actorIdentity = new JdbcActorIdentityAdapter(ds);
                validationPersistence = new JdbcValidationPersistenceAdapter(ds, elaboratedMetricCode, deliveredMetricCode);
                approvalPersistence = new JdbcKpiApprovalPersistenceAdapter(ds);
                approvalAuthorization = new JdbcKpiApprovalAuthorizationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVER_CAPABILITY_CODE", "KPI_APPROVAL"));
                approvalFeatureActivation = new JdbcFeatureActivationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVAL_FEATURE_CODE", "KPI_APPROVAL_ENABLED"));
                approvalScopeAdministration = new JdbcKpiApprovalScopeAdministrationAdapter(ds,
                        env.getOrDefault("GBC_KPI_APPROVER_ROLE_CODE", "KPI_APPROVER"),
                        env.getOrDefault("GBC_KPI_APPROVER_CAPABILITY_CODE", "KPI_APPROVAL"),
                        env.getOrDefault("GBC_KPI_APPROVAL_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_APPROVAL_SCOPE_ADMIN"));
                featureActivation = new JdbcFeatureActivationAdapter(ds, env.getOrDefault("GBC_KPI_VALIDATION_FEATURE_CODE", "KPI_OEE_VALIDATION_ENABLED"));
                manualAuthorization = new JdbcManualKpiAuthorizationAdapter(ds, env.getOrDefault("GBC_MANUAL_KPI_CAPABILITY_CODE", "KPI_MANUAL_ENTRY"));
                manualReferences = new JdbcManualKpiReferenceAdapter(ds);
                manualPersistence = new JdbcManualKpiPersistenceAdapter(ds, requiredEnv(env, "GBC_MANUAL_KPI_CAPTURE_KPI_CODE"));
                manualFeatureActivation = new JdbcFeatureActivationAdapter(ds, env.getOrDefault("GBC_MANUAL_KPI_FEATURE_CODE", "KPI_MANUAL_ENTRY_ENABLED"));
                manualScope = new JdbcManualKpiScopeAdapter(ds);
                manualScopeAdministration = new JdbcManualKpiScopeAdministrationAdapter(ds,
                        env.getOrDefault("GBC_MANUAL_KPI_ROLE_CODE", "KPI_MANUAL_SUPERUSER"),
                        env.getOrDefault("GBC_MANUAL_KPI_CAPABILITY_CODE", "KPI_MANUAL_ENTRY"),
                        env.getOrDefault("GBC_MANUAL_KPI_SCOPE_ADMIN_CAPABILITY_CODE", "KPI_MANUAL_SCOPE_ADMIN"));
                close = ds;
                sqlDataSource = ds;
            }
            default -> throw new IllegalArgumentException("Unsupported GBC_PERSISTENCE_MODE: " + settings.mode());
        }

        String role = env.get("GBC_KPI_VALIDATOR_ROLE");
        if ("memory".equalsIgnoreCase(settings.mode()) && (role == null || role.isBlank())) {
            role = "KPI_OEE_VALIDATOR";
        }

        String configuredAssignments = env.get("GBC_KPI_VALIDATOR_ASSIGNMENTS");
        if ("memory".equalsIgnoreCase(settings.mode()) && (configuredAssignments == null || configuredAssignments.isBlank())) {
            String tenant = env.getOrDefault("DEV_TENANT_ID", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            String user = env.getOrDefault("DEV_USER_OID", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            String plant = env.getOrDefault("DEV_PLANT_ID", "1");
            configuredAssignments = plant + "=" + tenant + "/" + user;
        }
        String validatorRoleCode = env.getOrDefault("GBC_KPI_VALIDATOR_ROLE_CODE", "KPI_OEE_VALIDATOR");
        String validatorCapabilityCode = env.getOrDefault("GBC_KPI_VALIDATOR_CAPABILITY_CODE", "KPI_OEE_VALIDATION");
        ValidatorAssignmentPort assignments;
        String assignmentAdminCapabilityCode = env.getOrDefault("GBC_KPI_VALIDATOR_ADMIN_CAPABILITY_CODE", "KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN");
        if ("memory".equalsIgnoreCase(settings.mode())) {
            assignments = new ConfiguredValidatorAssignmentAdapter(configuredAssignments);
        } else {
            DataSource assignmentDataSource = Objects.requireNonNull(sqlDataSource);
            assignments = new JdbcValidatorAssignmentAdapter(assignmentDataSource, validatorRoleCode, validatorCapabilityCode);
            assignmentAdministration = new JdbcValidatorAssignmentAdministrationAdapter(assignmentDataSource, validatorRoleCode, validatorCapabilityCode, assignmentAdminCapabilityCode);
        }

        CorrectionReasonPolicy correctionReasonPolicy = CorrectionReasonPolicy.closedDefaults();

        OeeValidationService service = new OeeValidationService(
                access,
                context,
                assignments,
                stopTimes,
                massPerformance,
                oeeMetrics,
                actorIdentity,
                validationPersistence,
                featureActivation,
                assignmentAdministration,
                correctionReasonPolicy,
                new PreviousLocalCalendarDayPolicy(),
                Clock.systemUTC(),
                role);
        ManualKpiService manualService = new ManualKpiService(
                access, context, actorIdentity, manualFeatureActivation, manualAuthorization, manualReferences,
                manualPersistence, Clock.systemUTC());
        ManualKpiScopeService manualScopeService = new ManualKpiScopeService(
                actorIdentity, manualFeatureActivation, manualAuthorization, manualScope, manualScopeAdministration);
        KpiApprovalService approvalService = new KpiApprovalService(
                actorIdentity, access, context, approvalFeatureActivation, approvalAuthorization, approvalPersistence, Clock.systemUTC());
        KpiApprovalScopeService approvalScopeService = new KpiApprovalScopeService(actorIdentity, approvalScopeAdministration);
        return new MicroserviceRuntime(service, health, manualService, manualScopeService, manualScopeService, approvalService, approvalScopeService, close);
    }

    public CheckEligibilityUseCase eligibility() { return validation; }
    public PrevalidateOeeCorrectionUseCase prevalidation() { return validation; }
    public SubmitOeeValidationUseCase submission() { return validation; }
    public GetOeeValidationUseCase validationDetail() { return validation; }
    public CapabilitiesUseCase capabilities() { return validation; }
    public GetMyValidatorAssignmentsUseCase validatorAssignments() { return validation; }
    public GetValidatorAssignmentPolicyUseCase validatorAssignmentPolicy() { return validation; }
    public AdministerValidatorAssignmentsUseCase validatorAssignmentAdministration() { return validation; }
    public GetTemporalContextUseCase temporalContext() { return validation; }
    public GetOeeMetricReferenceUseCase oeeMetricReference() { return validation; }
    public GetStopTimeReferenceUseCase stopTimeReference() { return validation; }
    public GetStopTimePolicyUseCase stopTimePolicy() { return validation; }
    public PrevalidateStopTimeCorrectionUseCase stopTimePrevalidation() { return validation; }
    public SubmitStopTimeValidationUseCase stopTimeSubmission() { return validation; }
    public GetStopTimeValidationUseCase stopTimeValidationDetail() { return validation; }
    public GetMassPerformanceReferenceUseCase massPerformanceReference() { return validation; }
    public GetMassPerformancePolicyUseCase massPerformancePolicy() { return validation; }
    public PrevalidateMassPerformanceCorrectionUseCase massPerformancePrevalidation() { return validation; }
    public SubmitMassPerformanceValidationUseCase massPerformanceSubmission() { return validation; }
    public GetMassPerformanceValidationUseCase massPerformanceValidationDetail() { return validation; }
    public GetJustificationPolicyUseCase justificationPolicy() { return validation; }
    public GetAuditPolicyUseCase auditPolicy() { return validation; }
    public GetWorkflowPolicyUseCase workflowPolicy() { return validation; }
    public DatabaseHealthPort health() { return health; }
    public ManualKpiUseCase manualKpis() { return manualKpis; }
    public ManualKpiScopeUseCase manualKpiScope() { return manualKpiScope; }
    public AdministerManualKpiScopeUseCase manualKpiScopeAdministration() { return manualKpiScopeAdministration; }
    public KpiApprovalUseCase kpiApproval() { return kpiApproval; }
    public AdministerKpiApprovalScopeUseCase kpiApprovalScopeAdministration() { return kpiApprovalScopeAdministration; }

    private static String requiredEnv(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.trim();
    }

    private static int optionalNonNegativeInt(Map<String, String> env, String key) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank()) return 0;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) throw new IllegalArgumentException(key + " cannot be negative");
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a non-negative integer", ex);
        }
    }

    @Override
    public void close() {
        try {
            closeable.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to close runtime", ex);
        }
    }
}
