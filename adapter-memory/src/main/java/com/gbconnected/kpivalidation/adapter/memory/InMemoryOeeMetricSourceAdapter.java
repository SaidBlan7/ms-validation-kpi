package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.OeeMetricSnapshot;
import com.gbconnected.kpivalidation.application.port.out.OeeMetricSourcePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

public final class InMemoryOeeMetricSourceAdapter implements OeeMetricSourcePort {
    @Override
    public Optional<OeeMetricSnapshot> findMetric(long plantId, long lineId, LocalDate businessDate,
            ZoneId zoneId, String metricCode) {
        if (plantId != 1L || lineId != 1L || metricCode == null || metricCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new OeeMetricSnapshot(
                plantId, lineId, businessDate, metricCode.trim(), new BigDecimal("87.50000000"),
                "kpi.kpi_result:memory-1"));
    }

    @Override
    public String source() {
        return "MEMORY_KPI_RESULT";
    }
}
