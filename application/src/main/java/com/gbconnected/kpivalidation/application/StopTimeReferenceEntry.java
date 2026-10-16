package com.gbconnected.kpivalidation.application;

public record StopTimeReferenceEntry(
        long stopClassificationId,
        String classificationCode,
        String classificationName,
        boolean classificationActive,
        long recordedLineDowntimeSeconds,
        long fragmentCount,
        long stopEventCount,
        String programmingType,
        String sourceAggregateKey) {
}

