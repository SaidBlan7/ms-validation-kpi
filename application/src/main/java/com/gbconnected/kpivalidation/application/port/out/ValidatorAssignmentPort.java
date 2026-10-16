package com.gbconnected.kpivalidation.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the KPI-specific validator entitlement and plant assignment.
 *
 * Canonical v1.2 production adapters may resolve this from app_security role,
 * capability and user-role history. Local/test adapters may still use explicit
 * external configuration.
 */
public interface ValidatorAssignmentPort {
    boolean configured();
    boolean isAssigned(UUID tenantId, UUID objectId, long plantId);
    Set<Long> assignedPlants(UUID tenantId, UUID objectId);
    int assignedValidatorCount(long plantId);
    String source();

    /** True when the adapter itself validates canonical role + capability. */
    default boolean enforcesRoleCapability() { return false; }

    /** Semantic role code expected by the adapter, when applicable. */
    default String roleCode() { return null; }

    /** Semantic capability code expected by the adapter, when applicable. */
    default String capabilityCode() { return null; }
}
