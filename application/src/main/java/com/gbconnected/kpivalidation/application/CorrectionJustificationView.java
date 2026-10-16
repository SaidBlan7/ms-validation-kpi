package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;

public record CorrectionJustificationView(
        ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {
}
