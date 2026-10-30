package com.gbconnected.kpivalidation.application.port.out.approval;
import com.gbconnected.kpivalidation.application.approval.*;
import java.time.Instant;
import java.util.*;
public interface KpiApprovalPersistencePort {
    List<ApprovalValidationView> pending();
    List<ApprovalValidationView> all();
    Optional<ApprovalValidationView> find(long validationId);
    ApprovalValidationView decide(long validationId,long userId,ApprovalDecision decision,Instant decidedAt,String correlationId);
    void expirePending(Instant now,String correlationId);
    boolean supportsWasteValidations();
}

