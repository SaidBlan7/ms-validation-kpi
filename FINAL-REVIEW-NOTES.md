# Review checkpoint — Specs 01-05 on canonical v1.2

## Current source status

- Specs 01-04 fueron revisadas nuevamente sobre el ZIP integrado y conservan sus garantías previas.
- Spec 01: identidad/scope canónico, activación por planta, OEE original desde `kpi.kpi_result`, original/propuesto, justificación/evidencia y persistencia real en `kpi.kpi_validation` + detalle con estado `PENDING`.
- Spec 02: entitlement KPI específico por planta con role/capability v1.2, acceso genérico independiente y bloqueo operativo si la planta excede dos validadores efectivos.
- Spec 03: D-1 calendario local, fines de semana/festivos incluidos, revalidación server-side y expiración de contexto al siguiente midnight local.
- Spec 04: agregado canónico por clasificación, original server-side, `oee_stop_type_code` canónico y persistencia PENDING por `stop_classification_id`.
- Spec 05: eliminado el modelo legacy `intermediate_material_movement/core.item`; referencia por línea + variable + UOM sobre fuentes físicas v1.2.

## Spec 05 source policy

Preferred source:

- exact line-level `telemetry.derived_metric` covering the D-1 local window;
- configurable semantic metric codes `ELABORATED_MASS` / `DELIVERED_MASS`.

AUTO fallback when a variable has no exact derived metric:

- `scheduling.batch_execution` for ELABORATED_MASS when the manufacturing definition contains dough;
- `scheduling.finished_product_output` for DELIVERED_MASS;
- only `core.unit_of_measure.dimension_code='MASS'` is accepted;
- fallback entries are explicitly marked provisional and must not be represented as confirmed OEE formula semantics.

Spec 05 prevalidation re-reads the canonical/provisional source. A stale/manipulated `originalValue` is blocked. Missing source data is not silently converted to zero because this is a correction flow, not manual capture.

## Remaining external/functional dependencies

- Validation KPI proposal persistence / status history (Specs 01, 04, 05).
- Approval KPI and final application semantics.
- Spec 03 final elapsed-24-hour/OEE-cutoff and authoritative timezone rule.
- Spec 04 total-time mathematics, multi-classification grouping and optional line-to-classification applicability if negocio requires it.
- Spec 05 official mass metric codes or confirmation of the scheduling fallback semantics, special performance rules, relationship between elaborated/delivered values, final precision/rounding and precedence for automatic data arriving after durable proposal creation.

## Validation performed in this review

- Java 21 direct compilation passed for domain, application, memory, PostgreSQL adapter, bootstrap and App Service.
- Azure Functions source compilation passed using API-core stubs plus the packaged Functions dependencies.
- Application/domain test sources compile against lightweight JUnit API stubs, validating updated signatures.
- Source smoke for Specs 01-05 passed in memory mode, including Spec 05 policy/reference/prevalidation and stale-original blocking.
- OpenAPI YAML parses successfully as version 0.9.0 with 17 paths.
- No new canonical INSERT/UPDATE/DELETE/DDL was introduced.

## Build artifacts

Do not deploy pre-existing `target/` artifacts from a source handoff. Rebuild cleanly in an environment with Maven:

```bash
rm -rf target */target
mvn clean verify
```

# Specs 06-07 final implementation checkpoint

## Corrections found

- The integrated handoff was missing `application/.../GetMassPerformanceReferenceUseCase.java` even though `OeeValidationService` and runtime wiring referenced it. A clean Java 21 compilation therefore failed until the interface was restored. Existing `target/` outputs could hide this problem; they remain non-canonical build artifacts.

## Spec 06

Implemented without inventing functional values:

- `OPERATIVE` requires cause + action plan; `SYSTEM` requires incident number.
- Required text is normalized and incompatible fields are discarded when reason changes.
- Optional maximum lengths are configurable and enforced in Unicode code points.
- Optional incident regex is configurable and enforced locally.
- Missing limits/regex remain visible as pending definitions instead of silently imposing arbitrary rules.
- GB Support online validation, sensitive-data policy and durable atomic persistence remain external/open.

## Spec 07

Implemented/prepared as far as possible without validation persistence:

- `tid + oid` remains the immutable external actor identity.
- PostgreSQL/Lakebase resolves existing `app_security.user_identity.user_id` plus display/email/active snapshots read-only.
- Evidence prefers canonical identity snapshots and falls back to authentication claims.
- Absolute event time remains `Instant`/UTC.
- Historical durability, retention and cross-module reconciliation require the future KPI validation persistence/workflow.

## Validation performed

- Core modules: clean direct Java 21 compile passed.
- App Service: direct Java 21 compile passed against dependencies extracted from the existing Boot artifact.
- Functions: source compile passed with temporary core-host API stubs plus the packaged annotation/dependency jars.
- Domain/application test sources: compile passed using lightweight JUnit API stubs.
- Spec 06 runtime smoke: configured max lengths + incident regex enforced; invalid incident blocked.
- Spec 07 runtime smoke: canonical user id/name/email/active snapshot takes precedence over token snapshots and the event instant is preserved.
- OpenAPI YAML parses as `0.10.0` with 17 paths.
- No canonical database writes or DDL were added.
