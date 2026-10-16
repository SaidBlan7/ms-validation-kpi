package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.StopTimeReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.StopTimeReferencePort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class InMemoryStopTimeReferenceAdapter implements StopTimeReferencePort {
    private final Map<String, String> programmingTypeByCode;

    public InMemoryStopTimeReferenceAdapter() {
        this(Map.of("MECHANICAL", "UNPROGRAMMED"));
    }

    public InMemoryStopTimeReferenceAdapter(Map<String, String> programmingTypeByCode) {
        this.programmingTypeByCode = normalize(programmingTypeByCode);
    }

    @Override
    public List<StopTimeReferenceEntry> findObservedTimes(long plantId, long lineId, LocalDate businessDate, ZoneId zoneId) {
        String code = "MECHANICAL";
        return List.of(new StopTimeReferenceEntry(100L, code, "Mechanical stop", true,
                900L, 2L, 2L, programmingTypeByCode.get(code), aggregateKey(lineId, businessDate, 100L)));
    }

    @Override
    public boolean classificationExistsInPlantScope(long plantId, long stopClassificationId) {
        return stopClassificationId == 100L;
    }

    @Override public String source() { return "MEMORY_CANONICAL_STOPS_V1_2"; }
    @Override public String programmingTypeState() {
        return programmingTypeByCode.isEmpty() ? "CONFIGURATION_REQUIRED" : "CONFIGURED_BY_CLASSIFICATION_CODE";
    }

    private static String aggregateKey(long lineId, LocalDate businessDate, long classificationId) {
        return "stops:line:" + lineId + ":date:" + businessDate + ":classification:" + classificationId;
    }

    private static Map<String, String> normalize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>();
        raw.forEach((code, type) -> {
            if (code == null || code.isBlank() || type == null || type.isBlank()) return;
            String normalizedType = type.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("PROGRAMMED", "UNPROGRAMMED").contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported stop programming type: " + type);
            }
            out.put(code.trim().toUpperCase(Locale.ROOT), normalizedType);
        });
        return Map.copyOf(out);
    }
}
