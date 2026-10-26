package com.gbconnected.kpivalidation.application.manual;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

public record ManualKpiTraceView(
        long recordId,
        long version,
        long plantId,
        long lineId,
        String businessDate,
        String plantTimezoneSnapshot,
        long createdByUserId,
        String createdByDisplayName,
        Instant createdAt,
        ZonedDateTime createdAtPlantLocal,
        long updatedByUserId,
        String updatedByDisplayName,
        Instant updatedAt,
        ZonedDateTime updatedAtPlantLocal,
        Instant editableUntil,
        ZonedDateTime editableUntilPlantLocal,
        boolean editable,
        String editWindowSemantics,
        List<ManualKpiAuditEventView> auditEvents) {
    public ManualKpiTraceView {
        auditEvents = auditEvents == null ? List.of() : List.copyOf(auditEvents);
    }
}
