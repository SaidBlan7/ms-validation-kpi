package com.gbconnected.kpivalidation.application;

public record ValidatorAssignmentPolicyView(
        String roleCode,
        String capabilityCode,
        int maxValidatorsPerPlant,
        String assignmentScope,
        String genericPlantAccessRequirement,
        String externalApprovalMode,
        String administrationMode,
        String expiryPolicy,
        String multiPlantPolicy,
        String thirdAssignmentControl,
        String revocationMechanism,
        String assignmentAuditState,
        String pendingValidationOnRevocationState,
        String concurrentAdministrationState) {
}
