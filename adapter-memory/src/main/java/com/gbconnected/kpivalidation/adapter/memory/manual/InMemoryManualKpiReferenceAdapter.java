package com.gbconnected.kpivalidation.adapter.memory.manual;

import com.gbconnected.kpivalidation.application.manual.ManualStopClassificationView;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiReferencePort;
import java.util.List;

/** Deterministic reference catalogs for local/manual-adapter execution. */
public final class InMemoryManualKpiReferenceAdapter implements ManualKpiReferencePort {
    private static final List<ManualStopClassificationView> STOPS = List.of(
            stop(1,"NO_ORDER","Sin Pedido",1,"PROGRAMMED"),
            stop(2,"PROGRAMMED_MAINTENANCE","Mantenimiento Programado",2,"PROGRAMMED"),
            stop(3,"THEORETICAL_CHANGEOVER_CLEANING","Cambio/Limpieza teórico",3,"PROGRAMMED"),
            stop(4,"EXCEEDED_CHANGEOVER_CLEANING","Cambio/Limpieza excedido",4,"UNPROGRAMMED"),
            stop(5,"OPERATIVE","Operativos",5,"UNPROGRAMMED"),
            stop(6,"MECHANICAL","Mecánicos",6,"UNPROGRAMMED"),
            stop(7,"OTHER","Otros",7,"UNPROGRAMMED"),
            stop(8,"UNCLASSIFIED","Sin clasificar",8,"UNPROGRAMMED"),
            stop(9,"REDUCED_RATE","Ritmo reducido",9,"UNPROGRAMMED"),
            stop(10,"MICROSTOP","Microparos",10,"UNPROGRAMMED"));

    public List<ManualStopClassificationView> activeStopClassifications(long plantId){return STOPS;}
    public boolean activeMetricDefinition(long id){return id>0;}
    public boolean activeUom(long id){return id>0;}
    public boolean validWasteClassification(long c,Long s){return c>0 && (s==null||s>0);}
    public String source(){return "MEMORY_REFERENCE_CATALOGS";}

    private static ManualStopClassificationView stop(long id,String code,String name,int order,String type){
        return new ManualStopClassificationView(id,code,name,order,type,"PROGRAMMED".equals(type),"UNPROGRAMMED".equals(type));
    }
}
