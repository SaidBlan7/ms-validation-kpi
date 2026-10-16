package com.gbconnected.kpivalidation.appservice.config;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.DatabaseHealthPort;
import com.gbconnected.kpivalidation.bootstrap.MicroserviceRuntime;
import org.springframework.context.annotation.*;

@Configuration
public class ApplicationWiring {
    @Bean(destroyMethod = "close")
    MicroserviceRuntime runtime() { return MicroserviceRuntime.fromEnvironment(System.getenv()); }

    @Bean CheckEligibilityUseCase eligibility(MicroserviceRuntime runtime) { return runtime.eligibility(); }
    @Bean PrevalidateOeeCorrectionUseCase prevalidation(MicroserviceRuntime runtime) { return runtime.prevalidation(); }
    @Bean CapabilitiesUseCase capabilities(MicroserviceRuntime runtime) { return runtime.capabilities(); }
    @Bean GetMyValidatorAssignmentsUseCase validatorAssignments(MicroserviceRuntime runtime) { return runtime.validatorAssignments(); }
    @Bean GetValidatorAssignmentPolicyUseCase validatorAssignmentPolicy(MicroserviceRuntime runtime) { return runtime.validatorAssignmentPolicy(); }
    @Bean GetTemporalContextUseCase temporalContext(MicroserviceRuntime runtime) { return runtime.temporalContext(); }
    @Bean GetOeeMetricReferenceUseCase oeeMetricReference(MicroserviceRuntime runtime) { return runtime.oeeMetricReference(); }
    @Bean GetStopTimeReferenceUseCase stopTimeReference(MicroserviceRuntime runtime) { return runtime.stopTimeReference(); }
    @Bean GetStopTimePolicyUseCase stopTimePolicy(MicroserviceRuntime runtime) { return runtime.stopTimePolicy(); }
    @Bean PrevalidateStopTimeCorrectionUseCase stopTimePrevalidation(MicroserviceRuntime runtime) { return runtime.stopTimePrevalidation(); }
    @Bean GetMassPerformanceReferenceUseCase massPerformanceReference(MicroserviceRuntime runtime) { return runtime.massPerformanceReference(); }
    @Bean GetMassPerformancePolicyUseCase massPerformancePolicy(MicroserviceRuntime runtime) { return runtime.massPerformancePolicy(); }
    @Bean PrevalidateMassPerformanceCorrectionUseCase massPerformancePrevalidation(MicroserviceRuntime runtime) { return runtime.massPerformancePrevalidation(); }
    @Bean GetJustificationPolicyUseCase justificationPolicy(MicroserviceRuntime runtime) { return runtime.justificationPolicy(); }
    @Bean GetAuditPolicyUseCase auditPolicy(MicroserviceRuntime runtime) { return runtime.auditPolicy(); }
    @Bean GetWorkflowPolicyUseCase workflowPolicy(MicroserviceRuntime runtime) { return runtime.workflowPolicy(); }
    @Bean DatabaseHealthPort databaseHealth(MicroserviceRuntime runtime) { return runtime.health(); }
}
