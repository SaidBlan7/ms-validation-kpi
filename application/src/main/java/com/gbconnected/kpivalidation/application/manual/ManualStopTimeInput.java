package com.gbconnected.kpivalidation.application.manual;

import java.math.BigDecimal;

/** Manual stop time captured in decimal hours, as required by KPI Manuales Spec 04. */
public record ManualStopTimeInput(long stopClassificationId, BigDecimal hours) {}
