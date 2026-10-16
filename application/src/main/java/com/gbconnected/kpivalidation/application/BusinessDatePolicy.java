package com.gbconnected.kpivalidation.application;
import java.time.Clock;
import java.time.LocalDate;
public interface BusinessDatePolicy { LocalDate editableBusinessDate(PlantContext plant, Clock clock); }
