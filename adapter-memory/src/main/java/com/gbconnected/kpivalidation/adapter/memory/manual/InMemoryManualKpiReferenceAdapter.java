package com.gbconnected.kpivalidation.adapter.memory.manual;

import com.gbconnected.kpivalidation.application.manual.ManualStopClassificationView;
import com.gbconnected.kpivalidation.application.manual.ManualWasteCatalogEntryView;
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
    private static final List<ManualWasteCatalogEntryView> WASTE = List.of(
            waste(1,"PROCESS","Proceso",101,"DOUGH","Masa"),
            waste(1,"PROCESS","Proceso",102,"PRODUCT","Producto"),
            waste(2,"PACKAGING","Empaque",201,"PRIMARY_PACKAGING","Empaque primario"),
            waste(2,"PACKAGING","Empaque",202,"SECONDARY_PACKAGING","Empaque secundario"));

    public List<ManualStopClassificationView> activeStopClassifications(long plantId){return STOPS;}
    public List<ManualWasteCatalogEntryView> activeWasteSubclassifications(long plantId){return WASTE;}
    public boolean wasteSubclassificationExists(long plantId,long c,long s){return WASTE.stream().anyMatch(x->x.wasteClassificationId()==c&&x.wasteSubclassificationId()==s);}
    public boolean activeMetricDefinition(long id){return id>0;}
    public boolean activeUom(long id){return id>0;}
    public String source(){return "MEMORY_REFERENCE_CATALOGS";}

    private static ManualStopClassificationView stop(long id,String code,String name,int order,String type){
        return new ManualStopClassificationView(id,code,name,order,type,"PROGRAMMED".equals(type),"UNPROGRAMMED".equals(type));
    }
    private static ManualWasteCatalogEntryView waste(long cid,String cc,String cn,long sid,String sc,String sn){
        return new ManualWasteCatalogEntryView(cid,cc,cn,sid,sc,sn,true);
    }
}
