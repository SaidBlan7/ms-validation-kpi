package com.gbconnected.kpivalidation.adapter.memory.manual;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiReferencePort;
public final class InMemoryManualKpiReferenceAdapter implements ManualKpiReferencePort {
    public boolean activeStopClassification(long id){return id>0;} public boolean activeMetricDefinition(long id){return id>0;}
    public boolean activeUom(long id){return id>0;} public boolean validWasteClassification(long c,Long s){return c>0 && (s==null||s>0);}
    public String source(){return "MEMORY_REFERENCE_CATALOGS";}
}
