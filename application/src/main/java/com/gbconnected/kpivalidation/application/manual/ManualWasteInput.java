package com.gbconnected.kpivalidation.application.manual;
import java.math.BigDecimal;

/** Spec 06: kilograms are always tied to a canonical waste subclassification. */
public record ManualWasteInput(long wasteClassificationId, long wasteSubclassificationId, BigDecimal kilograms) {}
