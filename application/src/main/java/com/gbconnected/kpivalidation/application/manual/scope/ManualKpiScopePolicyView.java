package com.gbconnected.kpivalidation.application.manual.scope;

public record ManualKpiScopePolicyView(String plantCardinality, String lineFilter,
                                       String revocationEffect, String timezoneSource,
                                       String assignmentSource, boolean administrationAvailable,
                                       String administrationCapabilityCode) { }
