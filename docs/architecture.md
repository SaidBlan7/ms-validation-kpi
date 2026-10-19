# Architecture

The service follows a hexagonal/module split:

- `domain`: validation reason and KPI-validation value rules that do not depend on infrastructure.
- `application`: eligibility, temporal context, reference queries, prevalidation, justification/audit evidence and outbound ports.
- `adapter-memory`: deterministic local/demo operational context and reference data.
- `adapter-postgres`: **read-only** access to the supplied canonical DB.
- `bootstrap`: environment-driven runtime composition and KPI-specific validator assignments.
- `runtime-app-service`: Spring Boot / Entra JWT runtime.
- `runtime-functions`: Azure Functions / Easy Auth principal runtime.

## Canonical DB boundary and v1.2 migration status

Specs 01-05 are aligned/reviewed against the physical v1.2 DDL for their current scope. The PostgreSQL path used by these specs reads:

- `app_security.user_identity`
- `app_security.user_plant_access` with temporal validity
- `app_security.user_role_assignment` + `role` + `role_capability` + `capability` with temporal validity
- `core.plant`
- `core.line` + `core.line_plant_assignment` with temporal validity
- `kpi.kpi_definition`
- `kpi.kpi_result`

The canonical OEE original-value adapter is read-only and accepts only a line-level result whose period exactly matches the current provisional plant-local D-1 window. It intentionally does not aggregate shift/product/execution rows or infer a different OEE cutoff.

Specs 04-05 are also migrated to physical v1.2. Spec 04 reads the canonical stops model. Spec 05 prefers line-level `telemetry.derived_metric` and can use an explicitly provisional scheduling fallback from `scheduling.batch_execution` / `scheduling.finished_product_output`. Health uses `SELECT 1`. No canonical operational/KPI rows are mutated.

## Deliberate KPI boundary

The service does not create or write KPI validation tables. The supplied canonical DB does not provide the persistence/workflow needed to save a validation proposal, guarantee one modification per datum, enter `PENDING_APPROVAL`, reconcile Approval KPI, or retain the Spec 07 audit snapshot historically.

`OeeMetricSourcePort` is now backed by canonical v1.2 `kpi.kpi_result` for Spec 01. `ValidationPersistencePort` remains an external boundary because the v1.2 DDL still has no KPI-validation proposal/approval workflow table.

Canonical v1.2 authorization uses `app_security.user_role_assignment` as the plant-scoped validator assignment and verifies the role-capability relationship. Semantic defaults are `KPI_OEE_VALIDATOR` / `KPI_OEE_VALIDATION`; they are configuration, not hard-coded business truth. Generic `user_plant_access` remains an independent minimum-privilege requirement. `GBC_KPI_VALIDATOR_ASSIGNMENTS` exists only as a memory/local fixture.

The service checks the Spec 01 maximum of two active validators on each plant-scoped operation. This detects and blocks invalid canonical reference data but cannot prevent an administrator or another service from inserting a third assignment because this micro is read-only and the canonical DDL has no corresponding exclusion/unique rule.

The workflow state vocabulary is modeled internally as `PENDING_APPROVAL`, `APPROVED`, `REJECTED`. It is a contract only: `PENDING_APPROVAL` becomes real only when a persistence owner/API/table exists. Source identity for future uniqueness/concurrency is `kpi_result_id`.

Spec 03 uses `core.plant.timezone_name` as the current provisional authority and implements the explicit D-1 calendar rule from the specification. Temporal context includes the next local-midnight expiry of the current D-1 selection and server-side revalidation is mandatory on every operation. Invalid timezone configuration is returned as a controlled blocker. Exact elapsed-24-hour/OEE-cutoff semantics remain a functional dependency rather than being inferred.

## Specs 04-07 reference semantics

- Spec 04 stop times are a read-only canonical-v1.2 reconstruction from `stops.stop_event`, `stop_fragment` and the latest `classification_history` row per event. D-1 fragments are clipped to the plant-local day window. PROGRAMMED/UNPROGRAMMED is an explicit configurable mapping by `classification_code`; no heuristic inference is used. Prevalidation re-reads the aggregate and rejects stale originals. Active plant-organization/global classifications are used as the provisional applicability fallback so zero values can be shown; a stricter per-line applicability model, total-time mathematics and durable workflow remain external/open.
- Spec 05 masses are read-only D-1 references keyed by line + variable + UOM. The preferred v1.2 source is an exact line-level `telemetry.derived_metric` using configurable semantic metric codes. In AUTO mode, missing variables may fall back to `scheduling.batch_execution` (elaborated) or `scheduling.finished_product_output` (delivered), both explicitly marked provisional. Only canonical UOMs with `dimension_code=MASS` are accepted. Prevalidation re-reads the source and blocks stale originals.
- Spec 06 justification is validated/normalized in domain/application. Optional per-field max lengths and an optional incident regex are environment-configurable; persistence and GB Support validation remain external.
- Spec 07 returns immutable Entra `tid + oid`, canonical `app_security.user_identity.user_id` when available, canonical/token-fallback display snapshots, account-active snapshot and an absolute UTC `Instant` as **prevalidation evidence**. Historical retention and cross-module reconciliation require persistence.
