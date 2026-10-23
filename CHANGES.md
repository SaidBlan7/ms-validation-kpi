
# Spec 07 closure

- Spec 07 is closed: canonical actor is `proposed_by_user_id` + immutable Entra tenant/object identity.
- `proposed_at` remains the absolute source-of-truth instant; presentation uses `core.plant.timezone_name`.
- Standard display is name + corporate email when available plus canonical/immutable identifier.
- Disabled accounts and later profile changes do not invalidate historical validation evidence.
- The service does not delete validation/audit evidence; retention follows the canonical data lifecycle.
- Approval KPI and reporting reconcile `kpi_validation_id`, `proposed_by_user_id` and `proposed_at`.
- No schema changes were introduced.

# Spec 03 — cierre definitivo

- Se verificó el ZIP integrado y la implementación temporal ya estaba alineada con la definición cerrada.
- La única fecha editable permanece como D-1 calendario en `core.plant.timezone_name`; no se interpreta como ventana móvil de 24 horas.
- Fines de semana y festivos no se omiten ni requieren calendario laboral.
- `temporal-context`, `eligibility`, referencias, prevalidaciones y persistencias protegidas recalculan la fecha en servidor; una pantalla o URL que cruza medianoche queda obsoleta y se rechaza al guardar.
- No fue necesario modificar lógica de Specs 01 o 02: ambas ya consumen la misma política temporal y respetan la revocación/asignación específica por planta.
- Se eliminó de `FINAL-REVIEW-NOTES.md` el pendiente obsoleto que todavía presentaba timezone/semántica de 24 horas como abiertos.


## Spec 02 closure
- Closed validator assignments as indefinite until explicit revocation; removed programmable `validTo` from assignment APIs.
- Closed pending-validation behavior: revocation removes future access only; existing `PENDING` proposals remain immutable and continue to Approval KPI.
- Confirmed multi-plant assignments with no global user limit and retained the transactional max-two-per-plant guard.

## Spec 05 - canonical-schema correction

- Removed the assumption of `kpi.kpi_validation_mass_detail`; no non-canonical table is required.
- Spec 05 reference/prevalidation remains implemented, but `readyForSubmission=false` while the canonical model cannot durably identify ELABORATED_MASS versus DELIVERED_MASS.
- Submission endpoints remain contract placeholders and fail before persistence instead of writing ambiguous data.
# Spec 05 v1.2 — migración canónica y hardening

- eliminado uso legacy de `scheduling.intermediate_material_movement`, `core.item` e `itemId`.
- [Historical, superseded by Spec 05 closure] An intermediate iteration preferred `telemetry.derived_metric`; the closed implementation no longer does so.
- Final canonical sources: `scheduling.batch_execution` for elaborated mass and `scheduling.finished_product_output` for delivered mass, with canonical packaging conversion when needed.
- contrato de referencia/prevalidación cambiado a `línea + D-1 + variable + UOM`.
- UOM validada con `dimension_code=MASS`.
- original revalidado server-side; stale/manipulado bloquea con `ORIGINAL_MASS_VALUE_CHANGED`.
- ausencia de original bloquea con `MASS_ORIGINAL_VALUE_NOT_FOUND`; no se asume cero.
- agregado `sourceAggregateKey`, `sourceType`, target status `PENDING` y `/masses/policy`; persistence is now canonical through `metric_definition_id`.
- OpenAPI 0.9.0, docs y configuración local actualizados.
- sin escrituras ni DDL sobre la baseline canónica.

# Spec 04 v1.2 — migración canónica y hardening

- migrado `JdbcStopTimeReferenceAdapter` del esquema legacy al DDL físico v1.2 (`stops.stop_event`, `stop_fragment`, `classification_history`, `stop_classification`).
- prevalidación ahora revalida el original contra el agregado server-side y bloquea valores stale/manipulados.
- agregado mapping configurable PROGRAMMED/UNPROGRAMMED por `classification_code` mediante `GBC_STOP_PROGRAMMING_TYPE_BY_CODE`.
- agregado `sourceAggregateKey` para la identidad semántica línea/fecha/clasificación.
- clasificaciones observadas e inactivas siguen visibles/prevalidatables para reconciliación histórica.
- nuevo `GET /api/v1/oee-validation/stop-times/policy` en App Service y Azure Functions.
- Specs 01-03 revisadas sobre el ZIP integrado; no se encontraron regresiones de código que requirieran cambio funcional adicional.
- sin escrituras ni DDL sobre la base canónica.

# Spec 03 v1.2 — cierre temporal y revisión Specs 01-02

- revisadas Specs 01-02 sobre el ZIP integrado; el núcleo de autorización v1.2, fuente `kpi.kpi_result` y límite de dos validadores permanece correcto.
- corregido `docs/spec-02-status.md`, que todavía describía la solución legacy por configuración externa aunque producción ya usa `app_security` v1.2.
- `temporal-context` ahora publica `serverEvaluatedAtUtc`, inicio/fin exclusivo de la ventana local de elegibilidad, semántica de ventana, obligación de revalidación y comportamiento al cruzar medianoche.
- una zona horaria de planta vacía o inválida produce blockers controlados en lugar de error interno.
- se mantiene explícita la diferencia entre la regla implementada D-1 calendario y la definición todavía abierta de “24 horas transcurridas”/corte OEE.
- no se agregan tablas, columnas ni escrituras SQL.

# Spec 02 v1.2 — cierre funcional de asignaciones

- conteo de máximo dos cambiado a **validadores efectivos**: rol/capability KPI vigente + `user_plant_access` vigente.
- `GET /api/v1/oee-validation/assignments/me` enriquecido con conteo y lugares restantes por planta.
- nuevo `GET /api/v1/oee-validation/assignments/policy` en App Service y Functions.
- política interina explícita: role/capability semánticos configurables, aprobación externa, `valid_to` opcional, multi-planta sin límite global, revocación por cierre de vigencia.
- auditoría de asignaciones reclasificada correctamente como parcial: v1.2 conserva historia/assigned_by/reason, pero no trae `revoked_by` dedicado ni trigger de auditoría para `user_role_assignment`.
- no se agregan escrituras ni DDL sobre la baseline canónica.

# Spec 01 v1.2 — hardening sin persistencia

- autorización PostgreSQL/Lakebase migrada a `app_security.user_role_assignment` + role/capability vigentes.
- códigos semánticos configurables: `KPI_OEE_VALIDATOR` y `KPI_OEE_VALIDATION`.
- `GBC_KPI_VALIDATOR_ASSIGNMENTS` queda sólo como fixture `memory`.
- detección runtime del máximo de dos validadores activos por planta.
- contrato semántico de estados `PENDING_APPROVAL`, `APPROVED`, `REJECTED`.
- nueva policy `GET /api/v1/oee-validation/workflow/policy`.
- `prevalidate` declara `targetStatusIfPersisted=PENDING_APPROVAL`; no simula persistencia.
- sigue pendiente exclusivamente la garantía que requiere almacenar la propuesta: unicidad, concurrencia, estado persistido y Approval KPI.

# Revisión posterior a Spec 03

Archivos corregidos sobre el ZIP recibido después de integrar Specs 01 + 02 + 03.

- `RequestActorContextResolver.java`: el actor DEV sólo se permite cuando `AUTH_MODE=mock`; `GBC_PERSISTENCE_MODE=memory` ya no desactiva autenticación.
- `compose.yaml`: declara `AUTH_MODE=mock` explícitamente para desarrollo local.
- `README.md`: actualizado al estado real de Specs 01 + 02 + 03, endpoints y configuración actual.

No se agregan tablas, columnas ni escrituras a la BD canónica.

# Revisión posterior a Spec 04 — baseline anterior (supersedida por migración v1.2)

- En la baseline anterior se verificó el recorte de fragmentos D-1 y la vista de clasificación vigente de ese modelo.
- Esa implementación quedó supersedida por la migración física v1.2 documentada al inicio de este archivo.


# Revisión posterior a Spec 05

- Confirmada integración de endpoints de masas en App Service, Azure Functions y OpenAPI.
- `masses/prevalidate` ahora contrasta la combinación `item + UOM + variable` contra la referencia operacional observada en esa línea/D-1.
- Si no hay dato observado se devuelve `MASS_SOURCE_PAIR_NOT_OBSERVED`; no se bloquea porque la Spec 05 deja abierto el caso de dato original inexistente.
- Si `originalValue` difiere de la referencia se devuelve `ORIGINAL_MASS_DIFFERS_FROM_OPERATIONAL_REFERENCE`; tampoco bloquea porque la fuente OEE oficial sigue pendiente.
- README y `spec-05-status.md` alineados con el estado real Specs 01-05.
- Sin tablas, columnas ni escrituras nuevas sobre la BD canónica.

# Spec 06 - justificación de correcciones

- Centralizada la validación `OPERATIVE` / `SYSTEM` en `CorrectionReason`.
- La justificación se normaliza antes de devolverse: trim, control de caracteres y descarte de campos que ya no corresponden al motivo seleccionado.
- Todas las prevalidaciones devuelven `justification` normalizada sin persistirla.
- Nuevo endpoint `GET /api/v1/oee-validation/justification/policy` en App Service y Azure Functions.
- No se inventa longitud máxima, patrón de incidente ni validación contra GB Support; permanecen explícitamente pendientes.
- Los logs registran `reasonType` pero no el texto libre de causa/plan/incidente.
- Sin tablas, columnas ni escrituras nuevas sobre la BD canónica.

# Spec 07 - evidencia de usuario y timestamp

- Las prevalidaciones OEE general, tiempos y masas devuelven `auditEvidence`.
- La identidad inmutable usa Entra `tid + oid`; nombre/principal son snapshots opcionales de presentación.
- `eventTimestamp` usa `Instant` absoluto UTC y Azure Functions lo serializa en ISO-8601.
- Nuevo endpoint `GET /api/v1/oee-validation/audit/policy` en App Service y Azure Functions.
- Persistencia histórica, retención y reconciliación con Aprobación/Reportes permanecen pendientes del contrato KPI.
- Sin tablas, columnas ni escrituras nuevas sobre la BD canónica.

# Revisión final Specs 01-07

- Verificado el source acumulado de Specs 01-07 y el wiring de App Service/Functions/OpenAPI.
- Añadidas capabilities explícitas para la evidencia de auditoría de Spec 07.
- README y arquitectura alineados al estado real de Specs 01-07.
- Los ejemplos locales incluyen snapshots opcionales `DEV_USER_DISPLAY_NAME` / `DEV_USER_EMAIL`.
- Se detectó que los `target/` incluidos en el ZIP recibido son artefactos **stale**: el App Service JAR arranca pero no contiene endpoints recientes como `/justification/policy` y `/audit/policy`, y el staging de Functions tampoco contiene todas las funciones de Specs 04-07. Deben eliminarse y reconstruirse con `mvn clean verify/package` antes de desplegar.
- El source actual sí compila con Java 21 y el App Service levantado desde clases recompiladas expone correctamente Specs 01-07.
- Sin INSERT/UPDATE/DELETE/CREATE TABLE/ALTER TABLE.


# Migración canónica v1.2 — Spec 01

- `app_security.user_plant_access` adaptado al modelo v1.2: identidad por `user_identity.user_id` y vigencia `valid_from/valid_to`.
- relación línea/planta adaptada a `core.line_plant_assignment` vigente; ya no se usa `core.line.plant_id`.
- agregado adapter read-only `JdbcOeeMetricSourceAdapter` sobre `kpi.kpi_definition` + `kpi.kpi_result`.
- `metricCode` ahora corresponde a `kpi_definition.kpi_code` sin hardcodear códigos OEE.
- agregado `GET /api/v1/oee-validation/oee/reference` en App Service y Azure Functions.
- `prevalidate` recupera `originalValue` y `sourceRecordKey` (`kpi_result_id`) desde la fuente canónica; si no existe resultado compatible bloquea la corrección.
- la selección del resultado exige período exacto D-1 local y scope de línea sin shift/product/execution para no inventar agregaciones ni cutoff OEE.
- persistencia, unicidad de validación y `PENDING_APPROVAL` siguen pendientes: v1.2 no incluye tabla/contrato de workflow de Validación KPI.
- esta migración cubre Spec 01; adapters PostgreSQL de Specs 04-05 continúan pendientes de migración v1.2 en sus respectivas revisiones.

# Specs 06-07 completion pass on v1.2

- [Historical, superseded by Spec 06 closure] Spec 06 previously supported optional configurable maximum lengths.
- [Historical, superseded by Spec 06 closure] Spec 06 previously supported an optional local incident regex.
- Spec 07 adds a read-only `JdbcActorIdentityAdapter` over `app_security.user_identity`, exposing `canonicalUserId`, canonical display/email snapshots, account-active snapshot and identity source in `auditEvidence` while preserving `tid/oid` as the external immutable identity.
- Spec 07 snapshot precedence is canonical `app_security` data first, authentication claims second; the absolute timestamp remains UTC `Instant`.
- Added missing `GetMassPerformanceReferenceUseCase.java`, which was referenced by source but absent from the uploaded ZIP and caused a clean source compilation failure.
- OpenAPI advanced to `0.10.0`; Spec 06/07 status docs, README, architecture and local config examples updated.
- Direct Java 21 source compilation passed for domain/application/memory/PostgreSQL/bootstrap/App Service. Azure Functions source compiled using temporary host API stubs plus packaged dependencies because the received Functions library contains annotations but not the runtime core API classes.
- Source test compilation passed with lightweight JUnit API stubs; Spec 06 configurable policy and Spec 07 canonical snapshot smoke tests passed.
- No INSERT/UPDATE/DELETE/CREATE/ALTER/DROP was introduced.

# Specs 06-07 canonical completion + Specs 01-05 alignment

- Spec 06: confirmed `OPERATIVE`/`SYSTEM` validation is shared across OEE, stop-time and mass prevalidation.
- Spec 06: OEE and stop-time persistence stores reason/cause/action-plan/incident atomically in existing `kpi.kpi_validation` columns.
- Spec 07: persisted OEE and stop-time detail now exposes audit evidence joined from `app_security.user_identity` plus persisted `proposed_at`.
- Spec 07: immutable Entra tenant/object ids and canonical user id are returned; current display/email are clearly treated as current profile data, not a historical snapshot.
- Spec 05: removed every PostgreSQL dependency on `kpi_validation_mass_detail`; no new table/column is assumed.
- Specs 01-04 remain on the canonical schema; no DDL was introduced.
## Spec 05 closure
- Closed Spec 05 against the updated canonical model with `kpi.kpi_validation_detail.metric_definition_id`.
- Elaborated mass now uses `scheduling.batch_execution`; delivered mass uses `scheduling.finished_product_output` with vigente `manufacturing.packaging_conversion` to MASS when required.
- Added durable PENDING persistence, duplicate protection by line/date/metric/UOM, and canonical metric-definition resolution.
- Removed the old derived-metric/provisional-fallback assumption for Spec 05.

## Spec 06 closure
- Closed justification policy: `SYSTEM` requires incidentNumber; `OPERATIVE` requires cause + action plan.
- Fixed limits are now service policy: incident 200 Unicode chars, cause 2000, action plan 2000.
- Removed environment-configurable Spec 06 length/regex knobs to avoid divergent runtime behavior.
- Incident semantics are intentionally capture-only; no regex or online GB Support validation is claimed.
- Removed pending-policy warnings for length/sensitive-data definition; application logs continue to exclude full free-text evidence.
