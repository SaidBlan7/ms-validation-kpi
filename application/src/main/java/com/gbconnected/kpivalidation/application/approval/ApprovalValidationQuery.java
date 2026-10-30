package com.gbconnected.kpivalidation.application.approval;

import java.time.LocalDate;

/** Read-only filters for the approval validation list. */
public record ApprovalValidationQuery(
        String status,
        String validationDomain,
        Long plantId,
        LocalDate fromDate,
        LocalDate toDate,
        int offset,
        int limit) {
    public ApprovalValidationQuery {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit <= 0 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (plantId != null && plantId <= 0) throw new IllegalArgumentException("plantId must be positive");
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) throw new IllegalArgumentException("toDate must be >= fromDate");
        status = normalize(status);
        validationDomain = normalize(validationDomain);
        if (status != null && !java.util.Set.of("PENDING","APPROVED","REJECTED","EXPIRED").contains(status))
            throw new IllegalArgumentException("status must be PENDING, APPROVED, REJECTED or EXPIRED");
        if (validationDomain != null && !java.util.Set.of("OEE","WASTE").contains(validationDomain))
            throw new IllegalArgumentException("validationDomain must be OEE or WASTE");
    }
    public static ApprovalValidationQuery defaults(){return new ApprovalValidationQuery(null,null,null,null,null,0,50);}
    private static String normalize(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(java.util.Locale.ROOT);}
}
