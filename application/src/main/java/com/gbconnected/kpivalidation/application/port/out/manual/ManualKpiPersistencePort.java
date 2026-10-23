package com.gbconnected.kpivalidation.application.port.out.manual;
import com.gbconnected.kpivalidation.application.manual.*;
import java.time.*;
import java.util.Optional;
public interface ManualKpiPersistencePort {
    boolean available();
    String source();
    Optional<ManualKpiRecord> find(long plantId, long lineId, LocalDate businessDate);
    ManualKpiRecord create(SaveManualKpiCommand command, String timezone, long actorUserId, Instant createdAt, Instant editableUntil, String correlationId);
    ManualKpiRecord update(ManualKpiRecord existing, SaveManualKpiCommand command, long actorUserId, Instant updatedAt, String correlationId);
    static ManualKpiPersistencePort unavailable(String source) { return new ManualKpiPersistencePort() {
        public boolean available(){return false;} public String source(){return source;}
        public Optional<ManualKpiRecord> find(long p,long l,LocalDate d){return Optional.empty();}
        public ManualKpiRecord create(SaveManualKpiCommand c,String z,long u,Instant a,Instant e,String x){throw new ManualKpiStorageUnavailableException("Canonical Manual KPI persistence is not available");}
        public ManualKpiRecord update(ManualKpiRecord r,SaveManualKpiCommand c,long u,Instant a,String x){throw new ManualKpiStorageUnavailableException("Canonical Manual KPI persistence is not available");}
    }; }
}
