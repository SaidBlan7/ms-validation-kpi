package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.*;
import java.util.List;
import java.util.UUID;

public interface ValidatorAssignmentAdministrationPort {
    boolean available();
    String adminCapabilityCode();
    boolean canAdminister(UUID tenantId, UUID objectId, long plantId);
    List<ValidatorAssignmentAdminView> list(long plantId);
    ValidatorAssignmentChangeResult assign(long actorUserId, long targetUserId, long plantId,
            String externalApprovalReference, String reason, String correlationId);
    ValidatorAssignmentChangeResult modify(long actorUserId, long targetUserId, long plantId,
            String externalApprovalReference, String reason, String correlationId);
    ValidatorAssignmentChangeResult revoke(long actorUserId, long targetUserId, long plantId, String reason,
            String correlationId);

    static ValidatorAssignmentAdministrationPort unavailable() {
        return new ValidatorAssignmentAdministrationPort() {
            public boolean available() { return false; }
            public String adminCapabilityCode() { return null; }
            public boolean canAdminister(UUID t, UUID o, long p) { return false; }
            public List<ValidatorAssignmentAdminView> list(long p) { return List.of(); }
            public ValidatorAssignmentChangeResult assign(long a,long u,long p,String e,String r,String c) { throw new UnsupportedOperationException("Assignment administration unavailable"); }
            public ValidatorAssignmentChangeResult modify(long a,long u,long p,String e,String r,String c) { throw new UnsupportedOperationException("Assignment administration unavailable"); }
            public ValidatorAssignmentChangeResult revoke(long a,long u,long p,String r,String c) { throw new UnsupportedOperationException("Assignment administration unavailable"); }
        };
    }
}
