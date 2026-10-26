package com.gbconnected.kpivalidation.application.port.out.manual;

import com.gbconnected.kpivalidation.application.manual.ManualStopClassificationView;
import com.gbconnected.kpivalidation.application.manual.ManualWasteCatalogEntryView;
import java.util.List;

public interface ManualKpiReferencePort {
    List<ManualStopClassificationView> activeStopClassifications(long plantId);
    List<ManualWasteCatalogEntryView> activeWasteSubclassifications(long plantId);
    boolean wasteSubclassificationExists(long plantId, long classificationId, long subclassificationId);
    boolean activeMetricDefinition(long id);
    boolean activeUom(long id);
    String source();
}
