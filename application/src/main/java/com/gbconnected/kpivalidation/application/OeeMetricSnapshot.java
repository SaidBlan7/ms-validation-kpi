package com.gbconnected.kpivalidation.application;
import java.math.BigDecimal;
import java.time.LocalDate;
public record OeeMetricSnapshot(long plantId, long lineId, LocalDate businessDate, String metricCode, BigDecimal value, String sourceRecordKey) {}
