package com.gbconnected.kpivalidation.application;

import java.util.List;

public record JustificationPolicyView(
        List<String> allowedReasonTypes,
        List<String> operativeRequiredFields,
        List<String> systemRequiredFields,
        String textNormalizationState,
        String textLengthPolicyState,
        Integer incidentNumberMaxLength,
        Integer operationalCauseMaxLength,
        Integer actionPlanMaxLength,
        String lengthUnit,
        String incidentFormatValidationState,
        String incidentNumberRegex,
        String gbSupportValidationState,
        String sensitiveDataPolicyState,
        String persistenceState) {
}
