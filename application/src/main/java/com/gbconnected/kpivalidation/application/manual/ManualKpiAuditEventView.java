package com.gbconnected.kpivalidation.application.manual;

import java.time.Instant;
import java.time.ZonedDateTime;

public record ManualKpiAuditEventView(
        long version,
        String action,
        long actorUserId,
        String actorDisplayName,
        Instant occurredAt,
        ZonedDateTime occurredAtPlantLocal,
        String correlationId,
        String result) {}
