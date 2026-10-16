package com.gbconnected.kpivalidation.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OeeMetricReferenceResult(
        long plantId,
        long lineId,
        LocalDate businessDate,
        String metricCode,
        BigDecimal originalValue,
        String sourceRecordKey,
        String sourceState,
        List<ValidationIssue> issues) {}
