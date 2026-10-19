package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.ActorIdentitySnapshot;
import java.util.Optional;
import java.util.UUID;

/** Resolves the canonical app_security identity used by future validation persistence/audit. */
public interface ActorIdentityPort {
    Optional<ActorIdentitySnapshot> findByEntraIdentity(UUID tenantId, UUID objectId);
    String source();

    default boolean canonicalUserIdAvailable() { return false; }

    static ActorIdentityPort unavailable() {
        return new ActorIdentityPort() {
            @Override public Optional<ActorIdentitySnapshot> findByEntraIdentity(UUID tenantId, UUID objectId) {
                return Optional.empty();
            }
            @Override public String source() { return "ENTRA_CLAIMS_ONLY"; }
        };
    }
}
