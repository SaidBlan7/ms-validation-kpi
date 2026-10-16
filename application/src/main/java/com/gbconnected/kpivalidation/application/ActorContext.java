package com.gbconnected.kpivalidation.application;

import java.util.Set;
import java.util.UUID;

public record ActorContext(
        UUID tenantId,
        UUID objectId,
        Set<String> roles,
        String displayName,
        String principalName) {

    public ActorContext(UUID tenantId, UUID objectId, Set<String> roles) {
        this(tenantId, objectId, roles, null, null);
    }

    public ActorContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        displayName = normalize(displayName);
        principalName = normalize(principalName);
    }

    public boolean hasRole(String role) {
        return role != null && !role.isBlank() && roles.contains(role);
    }

    public String immutableActorId() {
        if (tenantId == null || objectId == null) return null;
        return tenantId + "/" + objectId;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
