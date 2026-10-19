package com.gbconnected.kpivalidation.application;

import java.time.Instant;
import java.util.UUID;

public record ValidationAuditEvidenceView(
        Long canonicalUserId,
        UUID actorTenantId,
        UUID actorObjectId,
        String immutableActorId,
        String displayNameSnapshot,
        String principalNameSnapshot,
        Boolean accountActiveSnapshot,
        String identitySource,
        Instant eventTimestamp,
        String timestampSemantics,
        String evidencePersistenceState) {
}
