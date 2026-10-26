package com.gbconnected.kpivalidation.application.manual;

/** Canonical waste compendium entry exposed to KPI Manuales. */
public record ManualWasteCatalogEntryView(
        long wasteClassificationId,
        String classificationCode,
        String classificationName,
        long wasteSubclassificationId,
        String subclassificationCode,
        String subclassificationName,
        boolean active) {}
