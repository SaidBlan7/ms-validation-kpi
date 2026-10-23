package com.gbconnected.kpivalidation.application.manual;

/** Canonical stop-classification option exposed to KPI Manuales. */
public record ManualStopClassificationView(
        long stopClassificationId,
        String classificationCode,
        String classificationName,
        Integer displayOrder,
        String oeeStopTypeCode,
        boolean programmed,
        boolean unprogrammed) {}
