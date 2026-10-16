package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.application.ActorContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

final class RequestActorContextResolver {
    private static final UUID LOCAL_TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCAL_USER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private RequestActorContextResolver() {
    }

    static ActorContext resolve(Jwt jwt) {
        if (jwt != null) {
            String tenantId = jwt.getClaimAsString("tid");
            String objectId = jwt.getClaimAsString("oid");
            if (tenantId == null || objectId == null) {
                throw new SecurityException("Authenticated Entra identity must contain tid and oid");
            }

            List<String> roles = jwt.getClaimAsStringList("roles");
            try {
                return new ActorContext(
                        UUID.fromString(tenantId),
                        UUID.fromString(objectId),
                        roles == null ? Set.of() : Set.copyOf(roles),
                        jwt.getClaimAsString("name"),
                        firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"), jwt.getClaimAsString("upn")));
            } catch (IllegalArgumentException ex) {
                throw new SecurityException("Authenticated Entra identity contains invalid tid/oid", ex);
            }
        }

        if (mockAuthEnabled()) {
            String role = System.getenv().getOrDefault("GBC_KPI_VALIDATOR_ROLE", "KPI_OEE_VALIDATOR");
            return new ActorContext(
                    UUID.fromString(System.getenv().getOrDefault("DEV_TENANT_ID", LOCAL_TENANT.toString())),
                    UUID.fromString(System.getenv().getOrDefault("DEV_USER_OID", LOCAL_USER.toString())),
                    Set.of(role),
                    System.getenv().getOrDefault("DEV_USER_DISPLAY_NAME", "Local KPI Validator"),
                    System.getenv().get("DEV_USER_EMAIL"));
        }

        throw new SecurityException("Authenticated Entra identity is required");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static boolean mockAuthEnabled() {
        return "mock".equalsIgnoreCase(System.getenv().getOrDefault("AUTH_MODE", "entra"));
    }
}
