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
        Long uomId,
        String uomCode,
        String uomName) { }
