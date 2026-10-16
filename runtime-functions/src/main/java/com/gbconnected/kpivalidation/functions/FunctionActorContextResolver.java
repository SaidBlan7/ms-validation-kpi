package com.gbconnected.kpivalidation.functions;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.google.gson.*;
import com.microsoft.azure.functions.HttpRequestMessage;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class FunctionActorContextResolver {
    private static final String LT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String LU = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private FunctionActorContextResolver() {
    }

    static ActorContext resolve(HttpRequestMessage<?> request) {
        if (localMode()) {
            String role = System.getenv().getOrDefault("GBC_KPI_VALIDATOR_ROLE", "KPI_OEE_VALIDATOR");
            return new ActorContext(
                    UUID.fromString(System.getenv().getOrDefault("DEV_TENANT_ID", LT)),
                    UUID.fromString(System.getenv().getOrDefault("DEV_USER_OID", LU)),
                    Set.of(role),
                    System.getenv().getOrDefault("DEV_USER_DISPLAY_NAME", "Local KPI Validator"),
                    System.getenv().get("DEV_USER_EMAIL"));
        }

        JsonObject root = principal(request);
        String tid = claim(root, "tid", "http://schemas.microsoft.com/identity/claims/tenantid");
        String oid = claim(root, "oid", "http://schemas.microsoft.com/identity/claims/objectidentifier");
        if (tid == null || oid == null) throw new SecurityException("X-MS-CLIENT-PRINCIPAL must contain tid and oid");
        String displayName = firstNonBlank(
                claim(root, "name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name"),
                property(root, "userDetails"));
        String principalName = firstNonBlank(
                claim(root, "preferred_username", "preferred_username"),
                claim(root, "email", "emails", "upn"),
                property(root, "userDetails"));
        try {
            return new ActorContext(UUID.fromString(tid), UUID.fromString(oid), roles(root), displayName, principalName);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("X-MS-CLIENT-PRINCIPAL contains invalid tid/oid", ex);
        }
    }

    private static boolean localMode() {
        return "mock".equalsIgnoreCase(System.getenv().getOrDefault("AUTH_MODE", "entra"));
    }

    private static JsonObject principal(HttpRequestMessage<?> request) {
        String encoded = request.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("x-ms-client-principal"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (encoded == null || encoded.isBlank()) throw new SecurityException("Missing X-MS-CLIENT-PRINCIPAL");
        try {
            return JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new SecurityException("Invalid X-MS-CLIENT-PRINCIPAL", ex);
        }
    }

    private static String property(JsonObject root, String name) {
        if (!root.has(name) || root.get(name).isJsonNull()) return null;
        String value = root.get(name).getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String claim(JsonObject root, String... names) {
        JsonArray claims = root.getAsJsonArray("claims");
        if (claims == null) return null;
        for (JsonElement element : claims) {
            JsonObject current = element.getAsJsonObject();
            String type = current.has("typ") ? current.get("typ").getAsString() : null;
            for (String name : names) {
                if (name.equals(type)) {
                    String value = current.has("val") ? current.get("val").getAsString() : null;
                    return value == null || value.isBlank() ? null : value.trim();
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static Set<String> roles(JsonObject root) {
        Set<String> out = new HashSet<>();
        JsonArray claims = root.getAsJsonArray("claims");
        if (claims != null) {
            for (JsonElement element : claims) {
                JsonObject current = element.getAsJsonObject();
                String type = current.has("typ") ? current.get("typ").getAsString() : null;
                String value = current.has("val") ? current.get("val").getAsString() : null;
                if (value != null && ("roles".equals(type) || "role".equals(type)
                        || "http://schemas.microsoft.com/ws/2008/06/identity/claims/role".equals(type))) {
                    out.add(value);
                }
            }
        }
        return Set.copyOf(out);
    }
}
