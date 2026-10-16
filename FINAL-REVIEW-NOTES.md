# Review checkpoint — Specs 01-05 on canonical v1.2

## Current source status

- Specs 01-04 fueron revisadas nuevamente sobre el ZIP integrado y conservan sus garantías previas.
- Spec 01: identidad/scope canónico, OEE original desde `kpi.kpi_result`, original/propuesto, justificación/evidencia y contrato `PENDING_APPROVAL` sin fingir persistencia.
- Spec 02: entitlement KPI específico por planta con role/capability v1.2, acceso genérico independiente y bloqueo operativo si la planta excede dos validadores efectivos.
- Spec 03: D-1 calendario local, fines de semana/festivos incluidos, revalidación server-side y expiración de contexto al siguiente midnight local.
- Spec 04: `stops` v1.2, agregado por clasificación, original server-side, mapping PROGRAMMED/UNPROGRAMMED configurable y conservación histórica provisional.
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
- Spec 04 total-time mathematics, multi-classification grouping and official PROGRAMMED/UNPROGRAMMED source if configuration is not accepted.
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
