package com.gbconnected.kpivalidation.application;

import java.time.Instant;
import java.util.UUID;

public record ValidationAuditEvidenceView(
        UUID actorTenantId,
        UUID actorObjectId,
        String immutableActorId,
        String displayNameSnapshot,
        String principalNameSnapshot,
        Instant eventTimestamp,
        String timestampSemantics,
        String evidencePersistenceState) {
}
