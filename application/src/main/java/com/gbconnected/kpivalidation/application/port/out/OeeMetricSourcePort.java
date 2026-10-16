package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.OeeMetricSnapshot;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** Read-only source of canonical KPI results used as the original value for OEE validation. */
public interface OeeMetricSourcePort {
    Optional<OeeMetricSnapshot> findMetric(
            long plantId,
            long lineId,
            LocalDate businessDate,
            ZoneId zoneId,
            String metricCode);

    String source();
}
