package com.gbconnected.kpivalidation.application;

public record ActorIdentitySnapshot(
        Long canonicalUserId,
        String displayName,
        String principalName,
        boolean active,
        String source) {
}
