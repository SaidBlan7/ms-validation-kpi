package com.gbconnected.kpivalidation.application.approval;
import java.math.BigDecimal;
public record ApprovalValueChangeView(Long kpiResultId, Long stopClassificationId, Long metricDefinitionId,
        BigDecimal originalValue, BigDecimal proposedValue, Long uomId) {}
