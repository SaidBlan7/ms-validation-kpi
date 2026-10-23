package com.gbconnected.kpivalidation.application.manual;
import java.time.Instant;
public record ManualKpiAuditEvent(long version, String action, long actorUserId, Instant occurredAt, String correlationId, String result) {}
