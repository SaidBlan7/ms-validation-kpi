package com.gbconnected.kpivalidation.application.manual;
import java.time.*; import java.util.List;
public record ManualKpiRecord(long recordId, long version, long plantId, long lineId, LocalDate businessDate,
        String plantTimezone, ManualOeeSection oee, ManualWasteSection waste, long createdByUserId, Instant createdAt,
        long updatedByUserId, Instant updatedAt, Instant editableUntil, String sourceMode, String correlationId,
        List<ManualKpiAuditEvent> auditEvents) {
    public ManualKpiRecord { auditEvents = auditEvents == null ? List.of() : List.copyOf(auditEvents); }
    public boolean editableAt(Instant now) { return now != null && now.isBefore(editableUntil); }
}
