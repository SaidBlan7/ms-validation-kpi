package com.gbconnected.kpivalidation.application.approval;

import java.util.List;

public record ApprovalValidationPageView(
        List<ApprovalValidationView> items,
        int offset,
        int limit,
        long total) {
    public ApprovalValidationPageView { items = items == null ? List.of() : List.copyOf(items); }
}
