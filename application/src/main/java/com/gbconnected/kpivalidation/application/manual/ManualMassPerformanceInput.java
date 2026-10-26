package com.gbconnected.kpivalidation.application.manual;

import java.math.BigDecimal;

/** Spec 05 explicit manual performance inputs. Values intentionally preserve caller decimal precision. */
public record ManualMassPerformanceInput(BigDecimal elaboratedMass, BigDecimal deliveredMass) {
    public boolean empty() { return elaboratedMass == null && deliveredMass == null; }
}
