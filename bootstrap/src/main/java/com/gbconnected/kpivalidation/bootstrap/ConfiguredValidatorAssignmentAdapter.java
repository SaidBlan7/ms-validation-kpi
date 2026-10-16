package com.gbconnected.kpivalidation.bootstrap;

import com.gbconnected.kpivalidation.application.port.out.ValidatorAssignmentPort;
import java.util.*;

/**
 * External-configuration adapter for Spec 02.
 *
 * Format:
 *   GBC_KPI_VALIDATOR_ASSIGNMENTS=
 *     1=tenantUuid/objectUuid,tenantUuid/objectUuid;2=tenantUuid/objectUuid
 *
 * Assignment administration remains external, matching the current functional
 * specification. The service refuses configurations with more than two
 * distinct validators for one plant.
 */
public final class ConfiguredValidatorAssignmentAdapter implements ValidatorAssignmentPort {
    public static final int MAX_VALIDATORS_PER_PLANT = 2;
    public static final String ENV_NAME = "GBC_KPI_VALIDATOR_ASSIGNMENTS";

    private final Map<Long, Set<Principal>> byPlant;
    private final boolean configured;

    public ConfiguredValidatorAssignmentAdapter(String rawConfiguration) {
        String raw = rawConfiguration == null ? "" : rawConfiguration.trim();
        this.configured = !raw.isBlank();
        this.byPlant = parse(raw);
    }

    @Override public boolean configured() { return configured; }

    @Override
    public boolean isAssigned(UUID tenantId, UUID objectId, long plantId) {
        if (tenantId == null || objectId == null || plantId <= 0) return false;
        return byPlant.getOrDefault(plantId, Set.of()).contains(new Principal(tenantId, objectId));
    }

    @Override
    public Set<Long> assignedPlants(UUID tenantId, UUID objectId) {
        if (tenantId == null || objectId == null) return Set.of();
        Principal principal = new Principal(tenantId, objectId);
        TreeSet<Long> result = new TreeSet<>();
        byPlant.forEach((plantId, principals) -> {
            if (principals.contains(principal)) result.add(plantId);
        });
        return Collections.unmodifiableSet(result);
    }

    @Override
    public int assignedValidatorCount(long plantId) {
        return byPlant.getOrDefault(plantId, Set.of()).size();
    }

    @Override public String source() { return ENV_NAME; }

    private static Map<Long, Set<Principal>> parse(String raw) {
        if (raw.isBlank()) return Map.of();
        Map<Long, LinkedHashSet<Principal>> mutable = new LinkedHashMap<>();
        for (String plantEntry : raw.split(";")) {
            if (plantEntry == null || plantEntry.isBlank()) continue;
            String[] sides = plantEntry.trim().split("=", 2);
            if (sides.length != 2 || sides[0].isBlank() || sides[1].isBlank()) {
                throw new IllegalArgumentException("Invalid " + ENV_NAME + " entry: " + plantEntry);
            }
            long plantId;
            try {
                plantId = Long.parseLong(sides[0].trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid plantId in " + ENV_NAME + ": " + sides[0], ex);
            }
            if (plantId <= 0) throw new IllegalArgumentException("plantId must be positive in " + ENV_NAME);

            LinkedHashSet<Principal> validators = mutable.computeIfAbsent(plantId, ignored -> new LinkedHashSet<>());
            for (String principalText : sides[1].split(",")) {
                if (principalText == null || principalText.isBlank()) continue;
                validators.add(parsePrincipal(principalText.trim()));
                if (validators.size() > MAX_VALIDATORS_PER_PLANT) {
                    throw new IllegalArgumentException(
                            "Plant " + plantId + " has more than " + MAX_VALIDATORS_PER_PLANT
                                    + " KPI validators in " + ENV_NAME);
                }
            }
            if (validators.isEmpty()) {
                throw new IllegalArgumentException("Plant " + plantId + " has no validator identity in " + ENV_NAME);
            }
        }
        Map<Long, Set<Principal>> immutable = new LinkedHashMap<>();
        mutable.forEach((plantId, principals) -> immutable.put(plantId, Set.copyOf(principals)));
        return Collections.unmodifiableMap(immutable);
    }

    private static Principal parsePrincipal(String value) {
        String[] ids = value.split("/", 2);
        if (ids.length != 2 || ids[0].isBlank() || ids[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid validator identity in " + ENV_NAME + ": expected tenantUuid/objectUuid");
        }
        try {
            return new Principal(UUID.fromString(ids[0].trim()), UUID.fromString(ids[1].trim()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID in " + ENV_NAME + ": " + value, ex);
        }
    }

    private record Principal(UUID tenantId, UUID objectId) { }
}
