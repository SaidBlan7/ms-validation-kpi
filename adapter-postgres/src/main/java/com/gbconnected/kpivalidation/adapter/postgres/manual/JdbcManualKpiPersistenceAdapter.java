package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.manual.*;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiPersistencePort;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import com.google.gson.*;
import org.postgresql.util.PGobject;

import javax.sql.DataSource;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Canonical Manual KPI persistence.
 *
 * The current canonical model separates source facts from the consolidated KPI result:
 * - stops.daily_classification_time stores daily manual OEE stop-time source facts.
 * - waste.daily_manual_quantity stores daily manual waste source facts.
 * - kpi.kpi_result stores the versioned MANUAL consolidated result/snapshot.
 *
 * Source facts and kpi_result are written in the same PostgreSQL transaction. Snapshots V1 remain
 * readable for backwards compatibility; new writes use MANUAL_KPI_V2 and include source-fact ids.
 */
public final class JdbcManualKpiPersistenceAdapter implements ManualKpiPersistencePort {
    private static final String SOURCE_MODE = "MANUAL";
    private static final String SNAPSHOT_SCHEMA_V1 = "MANUAL_KPI_V1";
    private static final String SNAPSHOT_SCHEMA_V2 = "MANUAL_KPI_V2";
    private static final String KG_UOM_CODE = "KG";
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");
    private static final BigDecimal CONTAINER_RESULT_VALUE = BigDecimal.ZERO;

    private final DataSource dataSource;
    private final String captureKpiCode;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public JdbcManualKpiPersistenceAdapter(DataSource dataSource, String captureKpiCode) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (captureKpiCode == null || captureKpiCode.isBlank()) throw new IllegalArgumentException("captureKpiCode is required");
        this.captureKpiCode = captureKpiCode.trim();
    }

    @Override public boolean available() { return true; }
    @Override public String source() { return "CANONICAL_SOURCE_FACTS+KPI_RESULT:" + captureKpiCode; }

    @Override
    public Optional<ManualKpiRecord> find(long plantId, long lineId, LocalDate businessDate) {
        try (Connection c = dataSource.getConnection()) {
            return findInTransaction(c, plantId, lineId, businessDate);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read Manual KPI from canonical persistence", e);
        }
    }

    @Override
    public ManualKpiRecord create(SaveManualKpiCommand command, String timezone, long actorUserId,
            Instant createdAt, Instant editableUntil, String correlationId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(timezone, "timezone");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setAuditContext(c, actorUserId, correlationId);
                lockBusinessKey(c, command.plantId(), command.lineId(), command.businessDate());
                if (findInTransaction(c, command.plantId(), command.lineId(), command.businessDate()).isPresent()) {
                    throw new ManualKpiConflictException("Manual KPI record already exists");
                }
                ManualKpiAuditEvent createEvent = new ManualKpiAuditEvent(1, "CREATE", actorUserId, createdAt, correlationId, "SUCCESS");
                ManualKpiRecord provisional = new ManualKpiRecord(0L, 1L, command.plantId(), command.lineId(), command.businessDate(),
                        timezone, normalizeOee(command.oee()), normalizeWaste(command.waste()), actorUserId, createdAt,
                        actorUserId, createdAt, editableUntil, SOURCE_MODE, correlationId, List.of(createEvent));
                SourceFactRefs sourceFacts = synchronizeSourceFacts(c, provisional, actorUserId, createdAt);
                long rowId = insertVersion(c, resolveCaptureDefinition(c), provisional, sourceFacts);
                ManualKpiRecord created = copyWithRecordId(provisional, rowId);
                updateSnapshot(c, rowId, created, sourceFacts);
                c.commit();
                return created;
            } catch (RuntimeException | SQLException e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create Manual KPI in canonical persistence", e);
        }
    }

    @Override
    public ManualKpiRecord update(ManualKpiRecord existing, SaveManualKpiCommand command, long actorUserId,
            Instant updatedAt, String correlationId) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(command, "command");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setAuditContext(c, actorUserId, correlationId);
                lockBusinessKey(c, existing.plantId(), existing.lineId(), existing.businessDate());
                ManualKpiRecord current = findInTransaction(c, existing.plantId(), existing.lineId(), existing.businessDate())
                        .orElseThrow(() -> new ManualKpiNotFoundException("Manual KPI record was not found"));
                if (command.expectedVersion() == null || command.expectedVersion().longValue() != current.version()
                        || existing.version() != current.version()) {
                    throw new ManualKpiConflictException("Manual KPI record changed since it was read; reload it before saving");
                }
                long nextVersion = current.version() + 1L;
                List<ManualKpiAuditEvent> events = new ArrayList<>(current.auditEvents());
                events.add(new ManualKpiAuditEvent(nextVersion, "UPDATE", actorUserId, updatedAt, correlationId, "SUCCESS"));
                ManualKpiRecord updated = new ManualKpiRecord(current.recordId(), nextVersion, current.plantId(), current.lineId(),
                        current.businessDate(), current.plantTimezone(), normalizeOee(command.oee()), normalizeWaste(command.waste()),
                        current.createdByUserId(), current.createdAt(), actorUserId, updatedAt, current.editableUntil(), SOURCE_MODE,
                        correlationId, List.copyOf(events));
                SourceFactRefs sourceFacts = synchronizeSourceFacts(c, updated, actorUserId, updatedAt);
                insertVersion(c, resolveCaptureDefinition(c), updated, sourceFacts);
                c.commit();
                return updated;
            } catch (RuntimeException | SQLException e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update Manual KPI in canonical persistence", e);
        }
    }

    private Optional<ManualKpiRecord> findInTransaction(Connection c, long plantId, long lineId, LocalDate businessDate) throws SQLException {
        String sql = """
                SELECT kr.kpi_result_id, kr.source_snapshot
                  FROM kpi.kpi_result kr
                  JOIN kpi.kpi_definition kd ON kd.kpi_definition_id = kr.kpi_definition_id
                 WHERE kd.kpi_code = ? AND kr.source_mode = ?
                   AND kr.plant_id = ? AND kr.line_id = ?
                   AND kr.shift_id IS NULL AND kr.product_id IS NULL AND kr.production_execution_id IS NULL
                   AND kr.source_snapshot ->> 'schema' IN (?, ?)
                   AND kr.source_snapshot ->> 'businessDate' = ?
                 ORDER BY (kr.source_snapshot ->> 'version')::BIGINT DESC, kr.kpi_result_id DESC
                 LIMIT 1
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, captureKpiCode); p.setString(2, SOURCE_MODE); p.setLong(3, plantId); p.setLong(4, lineId);
            p.setString(5, SNAPSHOT_SCHEMA_V1); p.setString(6, SNAPSHOT_SCHEMA_V2); p.setString(7, businessDate.toString());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                String snapshot = r.getString("source_snapshot");
                ManualKpiRecord record = readSnapshotWithContext(snapshot, r.getLong("kpi_result_id"), plantId, lineId);
                return Optional.of(hydrateCanonicalSourceFacts(c, record, snapshotSchema(snapshot)));
            }
        }
    }

    private ManualKpiRecord hydrateCanonicalSourceFacts(Connection c, ManualKpiRecord snapshotRecord, String schema) throws SQLException {
        List<ManualStopTimeInput> stopFacts = readStopFacts(c, snapshotRecord.lineId(), snapshotRecord.businessDate());
        List<ManualWasteInput> wasteFacts = readWasteFacts(c, snapshotRecord.plantId(), snapshotRecord.lineId(), snapshotRecord.businessDate());
        boolean canonicalSnapshot = SNAPSHOT_SCHEMA_V2.equals(schema);
        ManualOeeSection oee = snapshotRecord.oee();
        ManualWasteSection waste = snapshotRecord.waste();
        if (canonicalSnapshot || !stopFacts.isEmpty()) oee = new ManualOeeSection(stopFacts, oee.massPerformance());
        if (canonicalSnapshot || !wasteFacts.isEmpty()) waste = new ManualWasteSection(wasteFacts, waste.producedTonnes());
        return new ManualKpiRecord(snapshotRecord.recordId(), snapshotRecord.version(), snapshotRecord.plantId(), snapshotRecord.lineId(),
                snapshotRecord.businessDate(), snapshotRecord.plantTimezone(), oee, waste, snapshotRecord.createdByUserId(),
                snapshotRecord.createdAt(), snapshotRecord.updatedByUserId(), snapshotRecord.updatedAt(), snapshotRecord.editableUntil(),
                snapshotRecord.sourceMode(), snapshotRecord.correlationId(), snapshotRecord.auditEvents());
    }

    private List<ManualStopTimeInput> readStopFacts(Connection c, long lineId, LocalDate date) throws SQLException {
        String sql = """
                SELECT stop_classification_id, duration_seconds
                  FROM stops.daily_classification_time
                 WHERE line_id=? AND operational_date=?
                 ORDER BY stop_classification_id
                """;
        List<ManualStopTimeInput> out = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId); p.setObject(2, date);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new ManualStopTimeInput(r.getLong(1), secondsToHours(r.getInt(2))));
            }
        }
        return List.copyOf(out);
    }

    private List<ManualWasteInput> readWasteFacts(Connection c, long plantId, long lineId, LocalDate date) throws SQLException {
        String sql = """
                SELECT ws.waste_classification_id, q.waste_subclassification_id, q.quantity
                  FROM waste.daily_manual_quantity q
                  JOIN waste.waste_subclassification ws ON ws.waste_subclassification_id=q.waste_subclassification_id
                 WHERE q.plant_id=? AND q.line_id=? AND q.operational_date=?
                 ORDER BY q.waste_subclassification_id
                """;
        List<ManualWasteInput> out = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId); p.setLong(2, lineId); p.setObject(3, date);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new ManualWasteInput(r.getLong(1), r.getLong(2), r.getBigDecimal(3)));
            }
        }
        return List.copyOf(out);
    }

    private SourceFactRefs synchronizeSourceFacts(Connection c, ManualKpiRecord record, long actorUserId, Instant occurredAt) throws SQLException {
        List<Long> stopIds = syncStopFacts(c, record, actorUserId, occurredAt);
        Long kgUomId = null;
        List<Long> wasteIds = List.of();
        if (!record.waste().waste().isEmpty()) {
            kgUomId = resolveKgUomId(c);
            wasteIds = syncWasteFacts(c, record, actorUserId, occurredAt, kgUomId);
        } else {
            deleteMissingWasteFacts(c, record.plantId(), record.lineId(), record.businessDate(), Set.of());
        }
        return new SourceFactRefs(stopIds, wasteIds, kgUomId);
    }

    private List<Long> syncStopFacts(Connection c, ManualKpiRecord record, long actorUserId, Instant occurredAt) throws SQLException {
        Set<Long> keep = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        String sql = """
                INSERT INTO stops.daily_classification_time
                    (line_id, operational_date, stop_classification_id, duration_seconds, recorded_by_user_id, recorded_at, source_system)
                VALUES (?, ?, ?, ?, ?, ?, 'GBC_APP')
                ON CONFLICT (line_id, operational_date, stop_classification_id)
                DO UPDATE SET duration_seconds=EXCLUDED.duration_seconds,
                              updated_by_user_id=?, updated_at=?
                RETURNING daily_classification_time_id
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (ManualStopTimeInput s : record.oee().stopTimes()) {
                keep.add(s.stopClassificationId());
                p.setLong(1, record.lineId()); p.setObject(2, record.businessDate()); p.setLong(3, s.stopClassificationId());
                p.setInt(4, hoursToSecondsExact(s.hours())); p.setLong(5, actorUserId);
                p.setObject(6, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC)); p.setLong(7, actorUserId);
                p.setObject(8, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
                try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Stop source-fact upsert returned no id"); ids.add(r.getLong(1)); }
            }
        }
        deleteMissingStopFacts(c, record.lineId(), record.businessDate(), keep);
        return List.copyOf(ids);
    }

    private void deleteMissingStopFacts(Connection c, long lineId, LocalDate date, Set<Long> keep) throws SQLException {
        String sql = keep.isEmpty()
                ? "DELETE FROM stops.daily_classification_time WHERE line_id=? AND operational_date=?"
                : "DELETE FROM stops.daily_classification_time WHERE line_id=? AND operational_date=? AND NOT (stop_classification_id = ANY (?))";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, lineId); p.setObject(2, date);
            if (!keep.isEmpty()) p.setArray(3, c.createArrayOf("bigint", keep.toArray(Long[]::new)));
            p.executeUpdate();
        }
    }

    private List<Long> syncWasteFacts(Connection c, ManualKpiRecord record, long actorUserId, Instant occurredAt, long kgUomId) throws SQLException {
        Set<Long> keep = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        String sql = """
                INSERT INTO waste.daily_manual_quantity
                    (plant_id, line_id, operational_date, waste_subclassification_id, quantity, uom_id,
                     recorded_by_user_id, recorded_at, source_system)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GBC_APP')
                ON CONFLICT (plant_id, line_id, operational_date, waste_subclassification_id)
                DO UPDATE SET quantity=EXCLUDED.quantity, uom_id=EXCLUDED.uom_id,
                              updated_by_user_id=?, updated_at=?
                RETURNING daily_manual_quantity_id
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (ManualWasteInput w : record.waste().waste()) {
                keep.add(w.wasteSubclassificationId());
                p.setLong(1, record.plantId()); p.setLong(2, record.lineId()); p.setObject(3, record.businessDate());
                p.setLong(4, w.wasteSubclassificationId()); p.setBigDecimal(5, w.kilograms()); p.setLong(6, kgUomId);
                p.setLong(7, actorUserId); p.setObject(8, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC)); p.setLong(9, actorUserId);
                p.setObject(10, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
                try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Waste source-fact upsert returned no id"); ids.add(r.getLong(1)); }
            }
        }
        deleteMissingWasteFacts(c, record.plantId(), record.lineId(), record.businessDate(), keep);
        return List.copyOf(ids);
    }

    private void deleteMissingWasteFacts(Connection c, long plantId, long lineId, LocalDate date, Set<Long> keep) throws SQLException {
        String sql = keep.isEmpty()
                ? "DELETE FROM waste.daily_manual_quantity WHERE plant_id=? AND line_id=? AND operational_date=?"
                : "DELETE FROM waste.daily_manual_quantity WHERE plant_id=? AND line_id=? AND operational_date=? AND NOT (waste_subclassification_id = ANY (?))";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, plantId); p.setLong(2, lineId); p.setObject(3, date);
            if (!keep.isEmpty()) p.setArray(4, c.createArrayOf("bigint", keep.toArray(Long[]::new)));
            p.executeUpdate();
        }
    }

    private long resolveKgUomId(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT uom_id FROM core.unit_of_measure WHERE UPPER(uom_code)=? AND is_active=TRUE")) {
            p.setString(1, KG_UOM_CODE);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new ManualKpiStorageUnavailableException("Active core.unit_of_measure with uom_code=KG is required for Manual KPI waste source facts");
                long id = r.getLong(1);
                if (r.next()) throw new ManualKpiStorageUnavailableException("More than one active KG unit was resolved");
                return id;
            }
        }
    }

    private long insertVersion(Connection c, KpiDefinition definition, ManualKpiRecord record, SourceFactRefs sourceFacts) throws SQLException {
        ZoneId zone = ZoneId.of(record.plantTimezone());
        Instant periodStart = record.businessDate().atStartOfDay(zone).toInstant();
        Instant periodEnd = periodStart.plus(Duration.ofHours(24));
        String sql = """
                INSERT INTO kpi.kpi_result
                    (kpi_definition_id, calculation_rule_id, plant_id, line_id, shift_id, product_id,
                     production_execution_id, period_start, period_end, result_value, result_uom_id,
                     source_mode, calculated_at, source_snapshot)
                VALUES (?, NULL, ?, ?, NULL, NULL, NULL, ?, ?, ?, ?, ?, ?, ?)
                RETURNING kpi_result_id
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, definition.id()); p.setLong(2, record.plantId()); p.setLong(3, record.lineId());
            p.setObject(4, OffsetDateTime.ofInstant(periodStart, ZoneOffset.UTC)); p.setObject(5, OffsetDateTime.ofInstant(periodEnd, ZoneOffset.UTC));
            p.setBigDecimal(6, CONTAINER_RESULT_VALUE);
            if (definition.resultUomId() == null) p.setNull(7, Types.BIGINT); else p.setLong(7, definition.resultUomId());
            p.setString(8, SOURCE_MODE); p.setObject(9, OffsetDateTime.ofInstant(record.updatedAt(), ZoneOffset.UTC));
            p.setObject(10, jsonb(snapshotJson(record, sourceFacts)));
            try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("INSERT kpi.kpi_result did not return kpi_result_id"); return r.getLong(1); }
        }
    }

    private void updateSnapshot(Connection c, long rowId, ManualKpiRecord record, SourceFactRefs sourceFacts) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE kpi.kpi_result SET source_snapshot=? WHERE kpi_result_id=?")) {
            p.setObject(1, jsonb(snapshotJson(record, sourceFacts))); p.setLong(2, rowId); p.executeUpdate();
        }
    }

    private KpiDefinition resolveCaptureDefinition(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT kpi_definition_id, result_uom_id FROM kpi.kpi_definition WHERE kpi_code=? AND is_active=TRUE")) {
            p.setString(1, captureKpiCode);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new ManualKpiStorageUnavailableException("Active kpi.kpi_definition not found for configured Manual KPI capture code=" + captureKpiCode);
                return new KpiDefinition(r.getLong("kpi_definition_id"), nullableLong(r, "result_uom_id"));
            }
        }
    }

    private void setAuditContext(Connection c, long actorUserId, String correlationId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.user_id', ?, true), set_config('app.source_system', 'GBC_APP', true), set_config('app.correlation_id', ?, true)")) {
            p.setString(1, Long.toString(actorUserId)); p.setString(2, correlationId == null ? "" : correlationId); p.executeQuery().close();
        }
    }

    private void lockBusinessKey(Connection c, long plantId, long lineId, LocalDate date) throws SQLException {
        String key = "manual-kpi:" + plantId + ":" + lineId + ":" + date;
        try (PreparedStatement p = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) { p.setString(1, key); p.executeQuery().close(); }
    }

    private String snapshotJson(ManualKpiRecord r, SourceFactRefs sourceFacts) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SNAPSHOT_SCHEMA_V2); root.addProperty("recordId", r.recordId()); root.addProperty("version", r.version());
        root.addProperty("businessDate", r.businessDate().toString()); root.addProperty("plantTimezone", r.plantTimezone());
        root.addProperty("createdByUserId", r.createdByUserId()); root.addProperty("createdAt", r.createdAt().toString());
        root.addProperty("updatedByUserId", r.updatedByUserId()); root.addProperty("updatedAt", r.updatedAt().toString());
        root.addProperty("editableUntil", r.editableUntil().toString()); root.addProperty("sourceMode", SOURCE_MODE);
        if (r.correlationId() == null) root.add("correlationId", JsonNull.INSTANCE); else root.addProperty("correlationId", r.correlationId());

        JsonObject source = new JsonObject();
        JsonArray stopSourceIds = new JsonArray(); sourceFacts.stopIds().forEach(stopSourceIds::add);
        JsonArray wasteSourceIds = new JsonArray(); sourceFacts.wasteIds().forEach(wasteSourceIds::add);
        source.add("dailyClassificationTimeIds", stopSourceIds); source.add("dailyManualWasteQuantityIds", wasteSourceIds);
        if (sourceFacts.wasteUomId() == null) source.add("wasteUomId", JsonNull.INSTANCE); else source.addProperty("wasteUomId", sourceFacts.wasteUomId());
        root.add("sourceFacts", source);

        JsonObject oee = new JsonObject(); JsonArray stops = new JsonArray();
        for (ManualStopTimeInput s : r.oee().stopTimes()) { JsonObject x = new JsonObject(); x.addProperty("stopClassificationId", s.stopClassificationId()); addDecimal(x, "hours", s.hours()); stops.add(x); }
        oee.add("stopTimes", stops);
        if (r.oee().massPerformance() == null) oee.add("massPerformance", JsonNull.INSTANCE);
        else { JsonObject m = new JsonObject(); addDecimal(m, "elaboratedMass", r.oee().massPerformance().elaboratedMass()); addDecimal(m, "deliveredMass", r.oee().massPerformance().deliveredMass()); oee.add("massPerformance", m); }
        root.add("oee", oee);

        JsonObject waste = new JsonObject(); JsonArray entries = new JsonArray();
        for (ManualWasteInput w : r.waste().waste()) { JsonObject x = new JsonObject(); x.addProperty("wasteClassificationId", w.wasteClassificationId()); x.addProperty("wasteSubclassificationId", w.wasteSubclassificationId()); addDecimal(x, "kilograms", w.kilograms()); entries.add(x); }
        waste.add("waste", entries); addDecimal(waste, "producedTonnes", r.waste().producedTonnes()); root.add("waste", waste);

        JsonArray audit = new JsonArray();
        for (ManualKpiAuditEvent e : r.auditEvents()) {
            JsonObject x = new JsonObject(); x.addProperty("version", e.version()); x.addProperty("action", e.action()); x.addProperty("actorUserId", e.actorUserId()); x.addProperty("occurredAt", e.occurredAt().toString());
            if (e.correlationId() == null) x.add("correlationId", JsonNull.INSTANCE); else x.addProperty("correlationId", e.correlationId());
            if (e.result() == null) x.add("result", JsonNull.INSTANCE); else x.addProperty("result", e.result()); audit.add(x);
        }
        root.add("auditEvents", audit); return gson.toJson(root);
    }

    private String snapshotSchema(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return nullableString(root, "schema");
    }

    private ManualKpiRecord readSnapshot(String json, long fallbackRowId) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        long recordId = longValue(root, "recordId", fallbackRowId); long version = longValue(root, "version", 1L);
        LocalDate businessDate = LocalDate.parse(string(root, "businessDate")); String timezone = string(root, "plantTimezone");
        ManualOeeSection oee = readOee(root.getAsJsonObject("oee")); ManualWasteSection waste = readWaste(root.getAsJsonObject("waste"));
        List<ManualKpiAuditEvent> audit = readAudit(root.getAsJsonArray("auditEvents"));
        return new ManualKpiRecord(recordId, version, 0L, 0L, businessDate, timezone, oee, waste,
                longValue(root, "createdByUserId", 0L), Instant.parse(string(root, "createdAt")),
                longValue(root, "updatedByUserId", 0L), Instant.parse(string(root, "updatedAt")),
                Instant.parse(string(root, "editableUntil")), SOURCE_MODE, nullableString(root, "correlationId"), audit);
    }

    private ManualKpiRecord readSnapshotWithContext(String json, long fallbackRowId, long plantId, long lineId) {
        ManualKpiRecord r = readSnapshot(json, fallbackRowId);
        return new ManualKpiRecord(r.recordId(), r.version(), plantId, lineId, r.businessDate(), r.plantTimezone(), r.oee(), r.waste(),
                r.createdByUserId(), r.createdAt(), r.updatedByUserId(), r.updatedAt(), r.editableUntil(), r.sourceMode(), r.correlationId(), r.auditEvents());
    }

    private ManualOeeSection readOee(JsonObject o) {
        if (o == null) return new ManualOeeSection(List.of(), null);
        List<ManualStopTimeInput> stops = new ArrayList<>(); JsonArray a = o.getAsJsonArray("stopTimes");
        if (a != null) for (JsonElement e : a) { JsonObject x=e.getAsJsonObject(); stops.add(new ManualStopTimeInput(x.get("stopClassificationId").getAsLong(), decimal(x,"hours"))); }
        ManualMassPerformanceInput mass = null; JsonElement m = o.get("massPerformance");
        if (m != null && !m.isJsonNull()) { JsonObject x=m.getAsJsonObject(); mass=new ManualMassPerformanceInput(decimal(x,"elaboratedMass"),decimal(x,"deliveredMass")); }
        return new ManualOeeSection(stops,mass);
    }

    private ManualWasteSection readWaste(JsonObject o) {
        if (o == null) return new ManualWasteSection(List.of(), null);
        List<ManualWasteInput> entries = new ArrayList<>(); JsonArray a=o.getAsJsonArray("waste");
        if(a!=null) for(JsonElement e:a){JsonObject x=e.getAsJsonObject();entries.add(new ManualWasteInput(x.get("wasteClassificationId").getAsLong(),x.get("wasteSubclassificationId").getAsLong(),decimal(x,"kilograms")));}
        return new ManualWasteSection(entries, decimal(o,"producedTonnes"));
    }

    private List<ManualKpiAuditEvent> readAudit(JsonArray a) {
        if (a == null) return List.of(); List<ManualKpiAuditEvent> out=new ArrayList<>();
        for(JsonElement e:a){JsonObject x=e.getAsJsonObject();out.add(new ManualKpiAuditEvent(longValue(x,"version",1),string(x,"action"),longValue(x,"actorUserId",0),Instant.parse(string(x,"occurredAt")),nullableString(x,"correlationId"),nullableString(x,"result")));}
        return List.copyOf(out);
    }

    private static int hoursToSecondsExact(BigDecimal hours) {
        if (hours == null) throw new DomainValidationException("Stop time hours are required");
        BigDecimal seconds = hours.multiply(SECONDS_PER_HOUR).stripTrailingZeros();
        if (seconds.scale() > 0) throw new DomainValidationException("Stop time precision is finer than one second; canonical storage uses whole seconds");
        try { return seconds.intValueExact(); }
        catch (ArithmeticException e) { throw new DomainValidationException("Stop time cannot be represented as canonical duration_seconds"); }
    }
    private static BigDecimal secondsToHours(int seconds) { return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, MathContext.DECIMAL128).stripTrailingZeros(); }
    private static void addDecimal(JsonObject o,String key,BigDecimal v){if(v==null)o.add(key,JsonNull.INSTANCE);else o.addProperty(key,v.toPlainString());}
    private static BigDecimal decimal(JsonObject o,String key){JsonElement e=o==null?null:o.get(key);return e==null||e.isJsonNull()?null:new BigDecimal(e.getAsString());}
    private static String string(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||e.isJsonNull())throw new IllegalStateException("Manual KPI source_snapshot missing "+key);return e.getAsString();}
    private static String nullableString(JsonObject o,String key){JsonElement e=o.get(key);return e==null||e.isJsonNull()?null:e.getAsString();}
    private static long longValue(JsonObject o,String key,long fallback){JsonElement e=o.get(key);return e==null||e.isJsonNull()?fallback:e.getAsLong();}
    private static Long nullableLong(ResultSet r,String column)throws SQLException{long v=r.getLong(column);return r.wasNull()?null:v;}
    private static PGobject jsonb(String json)throws SQLException{PGobject p=new PGobject();p.setType("jsonb");p.setValue(json);return p;}
    private static ManualOeeSection normalizeOee(ManualOeeSection o){return o==null?new ManualOeeSection(List.of(),null):o;}
    private static ManualWasteSection normalizeWaste(ManualWasteSection w){return w==null?new ManualWasteSection(List.of(),null):w;}
    private static ManualKpiRecord copyWithRecordId(ManualKpiRecord r,long id){return new ManualKpiRecord(id,r.version(),r.plantId(),r.lineId(),r.businessDate(),r.plantTimezone(),r.oee(),r.waste(),r.createdByUserId(),r.createdAt(),r.updatedByUserId(),r.updatedAt(),r.editableUntil(),r.sourceMode(),r.correlationId(),r.auditEvents());}
    private static void rollbackQuietly(Connection c){try{c.rollback();}catch(SQLException ignored){}}
    private record KpiDefinition(long id, Long resultUomId) {}
    private record SourceFactRefs(List<Long> stopIds, List<Long> wasteIds, Long wasteUomId) {
        SourceFactRefs { stopIds = List.copyOf(stopIds); wasteIds = List.copyOf(wasteIds); }
    }
}
