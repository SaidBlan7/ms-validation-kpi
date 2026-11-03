package com.gbconnected.kpivalidation.application.port.out.approval;
import com.gbconnected.kpivalidation.application.approval.*;
import java.time.Instant;
import java.util.*;
public interface KpiApprovalPersistencePort {
    List<ApprovalValidationView> pending();
    List<ApprovalValidationView> all();
    Optional<ApprovalValidationView> find(long validationId);
    /** Storage-level prefilter for authorized records; in-memory adapters can defer to Application checks. */
    default List<ApprovalValidationView> scopedAll(long userId) { return all(); }
    default List<ApprovalValidationView> scopedPending(long userId) { return pending(); }
    default Optional<ApprovalValidationView> scopedFind(long validationId, long userId) { return find(validationId); }

    ApprovalValidationView decide(long validationId,long userId,ApprovalDecision decision,Instant decidedAt,String correlationId);
    void expirePending(Instant now,String correlationId);
    boolean supportsWasteValidations();
}


