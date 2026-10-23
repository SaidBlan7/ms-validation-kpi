package com.gbconnected.kpivalidation.application.manual;
import java.math.BigDecimal;
public record ManualPerformanceInput(long metricDefinitionId, BigDecimal value, long uomId) {}
