package com.gbconnected.kpivalidation.domain.manual;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Spec 08 edit-window policy. The record is editable in the half-open interval
 * [createdAt, createdAt + 8h). At the exact expiration instant it is locked.
 */
public final class ManualKpiEditWindowPolicy {
    public static final int EDIT_HOURS = 8;

    public Instant expiresAt(Instant createdAt) {
        return Objects.requireNonNull(createdAt, "createdAt is required")
                .plus(Duration.ofHours(EDIT_HOURS));
    }

    public boolean editableAt(Instant createdAt, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return now.isBefore(expiresAt(createdAt));
    }
}
