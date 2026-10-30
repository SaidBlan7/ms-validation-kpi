package com.gbconnected.kpivalidation.application.approval;

import java.time.*;
import java.util.List;

public record ApprovalValidationView(
        long validationId,
        String validationDomain,
        String validationKind,
        String status,
        long plantId,
        Long lineId,
        LocalDate businessDate,
        String reasonType,
        String justification,
        String cause,
        String actionPlan,
        String incidentReference,
        long proposedByUserId,
        String proposedByDisplayName,
        Instant proposedAt,
        Instant expiresAt,
        Long decidedByUserId,
        String decidedByDisplayName,
        Instant decidedAt,
        boolean decidable,
        List<ApprovalValueChangeView> changes) {
    public ApprovalValidationView { changes = changes == null ? List.of() : List.copyOf(changes); }
}

