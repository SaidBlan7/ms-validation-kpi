package com.gbconnected.kpivalidation.adapter.postgres;

import com.gbconnected.kpivalidation.application.ActorIdentitySnapshot;
import com.gbconnected.kpivalidation.application.port.out.ActorIdentityPort;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Read-only canonical v1.2 user identity lookup for Spec 07 audit evidence. */
public final class JdbcActorIdentityAdapter implements ActorIdentityPort {
    private final DataSource dataSource;

    public JdbcActorIdentityAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<ActorIdentitySnapshot> findByEntraIdentity(UUID tenantId, UUID objectId) {
        if (tenantId == null || objectId == null) return Optional.empty();
        String sql = """
                SELECT user_id, display_name, corporate_email, is_active
                  FROM app_security.user_identity
                 WHERE entra_tenant_id = ?
                   AND entra_object_id = ?
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement p = connection.prepareStatement(sql)) {
            p.setObject(1, tenantId);
            p.setObject(2, objectId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new ActorIdentitySnapshot(
                        r.getLong("user_id"),
                        normalize(r.getString("display_name")),
                        normalize(r.getString("corporate_email")),
                        r.getBoolean("is_active"),
                        source()));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to resolve canonical actor identity", ex);
        }
    }

    @Override
    public Optional<ActorIdentitySnapshot> findByCanonicalUserId(long userId) {
        if (userId <= 0) return Optional.empty();
        String sql = """
                SELECT user_id, display_name, corporate_email, is_active
                  FROM app_security.user_identity
                 WHERE user_id = ?
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement p = connection.prepareStatement(sql)) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new ActorIdentitySnapshot(
                        r.getLong("user_id"),
                        normalize(r.getString("display_name")),
                        normalize(r.getString("corporate_email")),
                        r.getBoolean("is_active"),
                        source()));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to resolve canonical user identity", ex);
        }
    }

    @Override public String source() { return "APP_SECURITY_USER_IDENTITY_V1_2"; }
    @Override public boolean canonicalUserIdAvailable() { return true; }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
