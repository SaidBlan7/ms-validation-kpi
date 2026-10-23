package com.gbconnected.kpivalidation.application.port.out.manual;
public interface ManualKpiReferencePort {
    boolean activeStopClassification(long id);
    boolean activeMetricDefinition(long id);
    boolean activeUom(long id);
    boolean validWasteClassification(long classificationId, Long subclassificationId);
    String source();
}
