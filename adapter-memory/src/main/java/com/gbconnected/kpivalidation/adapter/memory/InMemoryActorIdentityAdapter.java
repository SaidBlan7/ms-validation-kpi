package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.ActorIdentitySnapshot;
import com.gbconnected.kpivalidation.application.port.out.ActorIdentityPort;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryActorIdentityAdapter implements ActorIdentityPort {
    private final UUID tenantId;
    private final UUID objectId;
    private final long userId;

    public InMemoryActorIdentityAdapter(UUID tenantId, UUID objectId, long userId) {
        this.tenantId = tenantId;
        this.objectId = objectId;
        this.userId = userId;
    }

    @Override
    public Optional<ActorIdentitySnapshot> findByEntraIdentity(UUID tenantId, UUID objectId) {
        if (!this.tenantId.equals(tenantId) || !this.objectId.equals(objectId)) return Optional.empty();
        return Optional.of(new ActorIdentitySnapshot(userId, "Development User", "dev@local", true, source()));
    }

    @Override public String source() { return "MEMORY_USER_IDENTITY"; }
    @Override public boolean canonicalUserIdAvailable() { return true; }
}
