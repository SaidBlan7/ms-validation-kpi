package com.gbconnected.kpivalidation.application.approval;

import java.math.BigDecimal;

public record ApprovalValueChangeView(
        Long kpiResultId,
        String kpiCode,
        String kpiName,
        Long stopClassificationId,
        String stopClassificationCode,
        String stopClassificationName,
        String oeeStopTypeCode,
        Long metricDefinitionId,
        String metricCode,
        String metricName,
        String metricSourceDomain,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        BigDecimal effectiveValue,
        String effectiveValueSource,
        Long uomId,
        String uomCode,
        String uomName) {

    public ApprovalValueChangeView withEffectiveForStatus(String status) {
        boolean approved = "APPROVED".equals(status);
        return new ApprovalValueChangeView(
                kpiResultId, kpiCode, kpiName,
                stopClassificationId, stopClassificationCode, stopClassificationName, oeeStopTypeCode,
                metricDefinitionId, metricCode, metricName, metricSourceDomain,
                originalValue, proposedValue,
                approved ? proposedValue : originalValue,
                approved ? "PROPOSED_APPROVED" : "ORIGINAL_NOT_APPROVED",
                uomId, uomCode, uomName);
    }
}
