package com.gbconnected.kpivalidation.application.port.out.manual;

import com.gbconnected.kpivalidation.application.manual.ManualStopClassificationView;
import java.util.List;

public interface ManualKpiReferencePort {
    List<ManualStopClassificationView> activeStopClassifications(long plantId);
    boolean activeMetricDefinition(long id);
    boolean activeUom(long id);
    boolean validWasteClassification(long classificationId, Long subclassificationId);
    String source();
}
