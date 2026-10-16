package com.gbconnected.kpivalidation.bootstrap;

import com.gbconnected.kpivalidation.adapter.memory.InMemoryOperationalContextAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryStopTimeReferenceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryMassPerformanceReferenceAdapter;
import com.gbconnected.kpivalidation.adapter.memory.InMemoryOeeMetricSourceAdapter;
import com.gbconnected.kpivalidation.adapter.postgres.*;
import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;

public final class MicroserviceRuntime implements AutoCloseable {
    private final OeeValidationService validation;
    private final DatabaseHealthPort health;
    private final AutoCloseable closeable;

    private MicroserviceRuntime(OeeValidationService validation, DatabaseHealthPort health, AutoCloseable closeable) {
        this.validation = validation;
        this.health = health;
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
        AutoCloseable close = () -> { };
        DataSource sqlDataSource = null;
        Map<String, String> stopProgrammingTypeByCode = parseStopProgrammingTypeMapping(
                env.get("GBC_STOP_PROGRAMMING_TYPE_BY_CODE"));
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
                stopTimes = stopProgrammingTypeByCode.isEmpty()
                        ? new InMemoryStopTimeReferenceAdapter()
                        : new InMemoryStopTimeReferenceAdapter(stopProgrammingTypeByCode);
                massPerformance = new InMemoryMassPerformanceReferenceAdapter();
                oeeMetrics = new InMemoryOeeMetricSourceAdapter();
            }
            case "postgres-password" -> {
                HikariDataSource ds = PooledDataSourceFactory.createPasswordDataSource(settings);
                var jdbc = new JdbcOperationalContextAdapter(ds);
                access = jdbc;
                context = jdbc;
                health = jdbc;
                stopTimes = new JdbcStopTimeReferenceAdapter(ds, stopProgrammingTypeByCode);
                massPerformance = new JdbcMassPerformanceReferenceAdapter(ds, massSourceMode, elaboratedMetricCode, deliveredMetricCode);
                oeeMetrics = new JdbcOeeMetricSourceAdapter(ds);
                close = ds;
                sqlDataSource = ds;
            }
            case "lakebase-oauth" -> {
                HikariDataSource ds = PooledDataSourceFactory.createLakebaseOAuthDataSource(settings, env);
                var jdbc = new JdbcOperationalContextAdapter(ds);
                access = jdbc;
                context = jdbc;
                health = jdbc;
                stopTimes = new JdbcStopTimeReferenceAdapter(ds, stopProgrammingTypeByCode);
                massPerformance = new JdbcMassPerformanceReferenceAdapter(ds, massSourceMode, elaboratedMetricCode, deliveredMetricCode);
                oeeMetrics = new JdbcOeeMetricSourceAdapter(ds);
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
        if ("memory".equalsIgnoreCase(settings.mode())) {
            assignments = new ConfiguredValidatorAssignmentAdapter(configuredAssignments);
        } else {
            assignments = new JdbcValidatorAssignmentAdapter(Objects.requireNonNull(sqlDataSource), validatorRoleCode, validatorCapabilityCode);
        }

        OeeValidationService service = new OeeValidationService(
                access,
                context,
                assignments,
                stopTimes,
                massPerformance,
                oeeMetrics,
                new PreviousLocalCalendarDayPolicy(),
                Clock.systemUTC(),
                role);
        return new MicroserviceRuntime(service, health, close);
    }

    public CheckEligibilityUseCase eligibility() { return validation; }
    public PrevalidateOeeCorrectionUseCase prevalidation() { return validation; }
    public CapabilitiesUseCase capabilities() { return validation; }
    public GetMyValidatorAssignmentsUseCase validatorAssignments() { return validation; }
    public GetValidatorAssignmentPolicyUseCase validatorAssignmentPolicy() { return validation; }
    public GetTemporalContextUseCase temporalContext() { return validation; }
    public GetOeeMetricReferenceUseCase oeeMetricReference() { return validation; }
    public GetStopTimeReferenceUseCase stopTimeReference() { return validation; }
    public GetStopTimePolicyUseCase stopTimePolicy() { return validation; }
    public PrevalidateStopTimeCorrectionUseCase stopTimePrevalidation() { return validation; }
    public GetMassPerformanceReferenceUseCase massPerformanceReference() { return validation; }
    public GetMassPerformancePolicyUseCase massPerformancePolicy() { return validation; }
    public PrevalidateMassPerformanceCorrectionUseCase massPerformancePrevalidation() { return validation; }
    public GetJustificationPolicyUseCase justificationPolicy() { return validation; }
    public GetAuditPolicyUseCase auditPolicy() { return validation; }
    public GetWorkflowPolicyUseCase workflowPolicy() { return validation; }
    public DatabaseHealthPort health() { return health; }

    private static Map<String, String> parseStopProgrammingTypeMapping(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String token : raw.split("[,;]")) {
            if (token == null || token.isBlank()) continue;
            int separator = token.indexOf('=');
            if (separator <= 0 || separator == token.length() - 1) {
                throw new IllegalArgumentException(
                        "GBC_STOP_PROGRAMMING_TYPE_BY_CODE entries must use classificationCode=PROGRAMMED|UNPROGRAMMED");
            }
            String code = token.substring(0, separator).trim();
            String type = token.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
            if (!Set.of("PROGRAMMED", "UNPROGRAMMED").contains(type)) {
                throw new IllegalArgumentException(
                        "Unsupported programming type for stop classification " + code + ": " + type);
            }
            out.put(code, type);
        }
        return Map.copyOf(out);
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
