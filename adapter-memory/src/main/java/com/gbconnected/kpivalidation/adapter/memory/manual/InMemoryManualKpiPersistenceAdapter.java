package com.gbconnected.kpivalidation.adapter.memory.manual;

import com.gbconnected.kpivalidation.application.manual.*;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiPersistencePort;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class InMemoryManualKpiPersistenceAdapter implements ManualKpiPersistencePort {
    private final AtomicLong ids=new AtomicLong();
    private final ConcurrentMap<String,ManualKpiRecord> records=new ConcurrentHashMap<>();
    public boolean available(){return true;}
    public String source(){return "MEMORY_MANUAL_KPI_STORE";}
    public Optional<ManualKpiRecord> find(long p,long l,LocalDate d){return Optional.ofNullable(records.get(key(p,l,d)));}

    public synchronized ManualKpiRecord create(SaveManualKpiCommand c,String z,long u,Instant at,Instant until,String correlation){
        String k=key(c.plantId(),c.lineId(),c.businessDate());
        if(records.containsKey(k)) throw new ManualKpiConflictException("Manual KPI record already exists");
        ManualKpiRecord r=new ManualKpiRecord(ids.incrementAndGet(),1,c.plantId(),c.lineId(),c.businessDate(),z,c.oee(),c.waste(),u,at,u,at,until,"MANUAL",correlation,List.of(new ManualKpiAuditEvent(1,"CREATE",u,at,correlation,"SUCCESS")));
        records.put(k,r);
        return r;
    }

    public synchronized ManualKpiRecord update(ManualKpiRecord old,SaveManualKpiCommand c,long u,Instant at,String correlation){
        String k=key(old.plantId(),old.lineId(),old.businessDate());
        ManualKpiRecord current=records.get(k);
        if(current==null) throw new ManualKpiNotFoundException("Manual KPI record was not found");
        if(c.expectedVersion()==null || c.expectedVersion().longValue()!=current.version() || old.version()!=current.version()) {
            throw new ManualKpiConflictException("Manual KPI record changed since it was read; reload it before saving");
        }
        ManualKpiRecord r=new ManualKpiRecord(current.recordId(),current.version()+1,current.plantId(),current.lineId(),current.businessDate(),current.plantTimezone(),c.oee(),c.waste(),current.createdByUserId(),current.createdAt(),u,at,current.editableUntil(),"MANUAL",correlation,append(current.auditEvents(),new ManualKpiAuditEvent(current.version()+1,"UPDATE",u,at,correlation,"SUCCESS")));
        records.put(k,r);
        return r;
    }
    private static List<ManualKpiAuditEvent> append(List<ManualKpiAuditEvent> current,ManualKpiAuditEvent event){var copy=new ArrayList<>(current);copy.add(event);return List.copyOf(copy);}
    private static String key(long p,long l,LocalDate d){return p+":"+l+":"+d;}
}
