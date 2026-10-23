package com.gbconnected.kpivalidation.application.manual;
import java.math.BigDecimal;
public record ManualWasteInput(long wasteClassificationId, Long wasteSubclassificationId, BigDecimal kilograms) {}
