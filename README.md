# gb-gbc2-ms-kpi-validation

Microservicio incremental para **Validación KPI: OEE**, con Specs 01 a 07 integradas. La migración a la baseline física v1.2 se está realizando por especificación; **Specs 01, 02 y 03 ya están alineadas/revisadas contra v1.2 en su alcance actual**.

No crea tablas ni columnas de workflow KPI y no ejecuta escrituras sobre PostgreSQL. Para **Spec 01 sobre v1.2** lee:

- `app_security.user_identity`
- `app_security.user_plant_access`
- `app_security.role` + `app_security.capability` + `app_security.role_capability` + `app_security.user_role_assignment`
- `core.plant`
- `core.line` + `core.line_plant_assignment`
- `kpi.kpi_definition`
- `kpi.kpi_result`

Los adapters de Specs 04-05 todavía reflejan la baseline anterior y se migrarán cuando revisemos esas especificaciones; por tanto este paso no declara compatibilidad v1.2 completa del micro.

## Endpoints actuales

- `GET /api/v1/oee-validation/capabilities`
- `GET /api/v1/oee-validation/justification/policy`
- `GET /api/v1/oee-validation/audit/policy`
- `GET /api/v1/oee-validation/workflow/policy`
- `GET /api/v1/oee-validation/assignments/policy`
- `GET /api/v1/oee-validation/assignments/me`
- `GET /api/v1/oee-validation/temporal-context?plantId=1&lineId=10`
- `GET /api/v1/oee-validation/eligibility?plantId=1&lineId=10&businessDate=2026-10-08`
- `GET /api/v1/oee-validation/oee/reference?plantId=1&lineId=10&businessDate=2026-10-08&metricCode=OEE`
- `POST /api/v1/oee-validation/prevalidate`
- `GET /api/v1/oee-validation/stop-times/policy`
- `GET /api/v1/oee-validation/stop-times/reference?plantId=&lineId=&businessDate=`
- `POST /api/v1/oee-validation/stop-times/prevalidate`
- `GET /api/v1/oee-validation/masses/policy`
- `GET /api/v1/oee-validation/masses/reference?plantId=&lineId=&businessDate=`
- `POST /api/v1/oee-validation/masses/prevalidate`
- `GET /api/v1/health`

### Prevalidate

```json
{
  "plantId": 1,
  "lineId": 10,
  "businessDate": "2026-10-08",
  "metricCode": "OEE",
  "proposedValue": 87.45,
  "reasonType": "SYSTEM",
  "incidentNumber": "INC-12345"
}
```

`metricCode` ahora se resuelve contra `kpi.kpi_definition.kpi_code`. El micro no inventa ni hardcodea cuáles códigos representan OEE 1, OEE 2 u OEE completo; esos códigos deben provenir de los datos/configuración canónica.

## Seguridad y asignación KPI

En PostgreSQL/Lakebase v1.2, la autorización funcional ya se resuelve desde `app_security`; no depende de `GBC_KPI_VALIDATOR_ASSIGNMENTS`. Los códigos semánticos provisionales, centralizados y reemplazables, son:

- role code: `KPI_OEE_VALIDATOR` (`GBC_KPI_VALIDATOR_ROLE_CODE`)
- capability code: `KPI_OEE_VALIDATION` (`GBC_KPI_VALIDATOR_CAPABILITY_CODE`)
- admin capability code: `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` (`GBC_KPI_VALIDATOR_ADMIN_CAPABILITY_CODE`)

El acceso funcional requiere simultáneamente:

1. Identidad activa en `app_security.user_identity`.
2. `user_role_assignment` vigente y **específico para la planta** con el rol configurado.
3. Mapeo vigente `role_capability` hacia la capability configurada.
4. Acceso genérico vigente en `app_security.user_plant_access`.
5. Asignación vigente `core.line_plant_assignment` entre la línea y la planta.

Cada operación valida además que la planta no tenga más de dos **validadores OEE efectivos**. El conteo exige asignación KPI/capability vigente **y** `user_plant_access` vigente, para que un rol residual sin acceso general no consuma uno de los dos lugares. Como el DDL v1.2 no incluye un constraint que impida crear una tercera asignación, este micro detecta la configuración inválida y bloquea el uso de la planta; la prevención transaccional en el write path administrativo queda fuera de su ownership.

`GET /api/v1/oee-validation/assignments/policy` documenta la política Spec 02. La aprobación de Dirección Global sigue siendo externa, pero la administración técnica ya está implementada en este micro: alta, modificación, revocación, máximo de dos con serialización transaccional por planta y auditoría canónica. `GET /assignments/me` mantiene la visibilidad del validador y `GET /assignments?plantId=...` expone la vista administrativa.

En `memory` se conserva `GBC_KPI_VALIDATOR_ASSIGNMENTS` como fixture local y `GBC_KPI_VALIDATOR_ROLE` como role claim simulado. `AUTH_MODE=mock` sigue habilitado únicamente para desarrollo local.

## Regla temporal actual

La única fecha elegible es **el día calendario anterior (`D-1`) en la zona horaria configurada para la planta**.

- No se saltan sábados ni domingos.
- No se utiliza calendario de festivos: un festivo también puede ser D-1.
- `eligibility` y `prevalidate` recalculan D-1 en cada solicitud.
- `temporal-context` devuelve por separado la fecha/hora actual de operación, la fecha KPI elegible y la ventana local en la que ese D-1 permanece vigente.
- `eligibilityWindowEndsAtExclusive` permite al front refrescar la pantalla al llegar a la siguiente medianoche local.
- `revalidateOnSubmit=true`: el backend vuelve a calcular D-1 al guardar/prevalidar, aunque la pantalla no se haya refrescado.
- Una `timezone_name` vacía o inválida bloquea la operación de forma controlada en vez de producir un error interno.

La interpretación exacta de “máximo 24 hrs”, el corte oficial del día OEE y la autoridad definitiva de timezone continúan pendientes de definición funcional. La política implementada se identifica explícitamente como día calendario local, no como una garantía de 24 horas transcurridas.

## Configuración

| Variable | Uso |
|---|---|
| `GBC_PERSISTENCE_MODE` | `memory`, `postgres-password`, `lakebase-oauth` |
| `GBC_SECURITY_ENABLED` | activa OAuth2 Resource Server en App Service |
| `AUTH_MODE` | `entra` por defecto; `mock` sólo para desarrollo local |
| `GBC_KPI_VALIDATOR_ROLE_CODE` | role_code canónico; default semántico `KPI_OEE_VALIDATOR` |
| `GBC_KPI_VALIDATOR_CAPABILITY_CODE` | capability_code canónico; default semántico `KPI_OEE_VALIDATION` |
| `GBC_KPI_VALIDATOR_ADMIN_CAPABILITY_CODE` | capability_code para administrar asignaciones Spec 02; default semántico `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` |
| `GBC_KPI_VALIDATOR_ROLE` | role claim usado sólo en modo local/external-config |
| `GBC_KPI_VALIDATOR_ASSIGNMENTS` | fixture de asignaciones usado sólo en `memory` |
| `GBC_STOP_PROGRAMMING_TYPE_BY_CODE` | mapping explícito `classificationCode=PROGRAMMED|UNPROGRAMMED`; acepta múltiples entradas separadas por coma o punto y coma |
| `GBC_MASS_PERFORMANCE_SOURCE_MODE` | `AUTO` (default), `DERIVED_METRIC_ONLY` o `SCHEDULING_FALLBACK_ONLY` |
| `GBC_ELABORATED_MASS_METRIC_CODE` | código semántico de `telemetry.derived_metric`; default `ELABORATED_MASS` |
| `GBC_DELIVERED_MASS_METRIC_CODE` | código semántico de `telemetry.derived_metric`; default `DELIVERED_MASS` |
| `DEV_TENANT_ID`, `DEV_USER_OID` | identidad inmutable usada únicamente con `AUTH_MODE=mock` |
| `DEV_USER_DISPLAY_NAME`, `DEV_USER_EMAIL` | snapshots opcionales de presentación para Spec 07 en modo mock |
| `JDBC_URL`, `DB_USER`, `DB_PASSWORD` | modo `postgres-password` |
| variables Databricks/Lakebase | modo `lakebase-oauth` |

Ejemplo local de asignaciones (`memory`):

```text
GBC_KPI_VALIDATOR_ASSIGNMENTS=1=<tenant>/<oid1>,<tenant>/<oid2>;2=<tenant>/<oid1>
```

## Importante

- Todavía no existe `POST /validations`: no hay persistencia KPI disponible y el servicio no simula guardar propuestas.
- `prevalidate` resuelve el valor OEE/KPI original desde `kpi.kpi_result`, devuelve `sourceRecordKey`, valida una sola oportunidad y declara estado objetivo `PENDING`. `POST /api/v1/oee-validation/validations` persiste la propuesta en `kpi.kpi_validation` + `kpi.kpi_validation_detail`.
- `GET /api/v1/oee-validation/workflow/policy` publica los estados canónicos `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`; Aprobación KPI conserva la segunda etapa.
- Ver `docs/spec-01-status.md` a `docs/spec-07-status.md` para el estado de cada especificación.

## Build

Requiere Java 21 y Maven 3.9+:

```bash
mvn clean verify
```

App Service:

```bash
mvn -pl runtime-app-service -am spring-boot:run
```

Azure Functions:

```bash
mvn -pl runtime-functions -am azure-functions:run
```

## Spec 04 - tiempos por clasificación de paro

- `GET /api/v1/oee-validation/stop-times/policy`: contrato actual de fuente, mapping PROGRAMMED/UNPROGRAMMED, clasificación inactiva, reconciliación y pendientes.
- `GET /api/v1/oee-validation/stop-times/reference?plantId=&lineId=&businessDate=`: reconstrucción read-only sobre el DDL v1.2 (`stop_event` + `stop_fragment` + última `classification_history`). Expone el catálogo activo global/organización de la planta incluso con `0` segundos y retiene clasificaciones inactivas cuando sí fueron observadas en D-1.
- `POST /api/v1/oee-validation/stop-times/prevalidate`: valida una corrección y vuelve a resolver en servidor el valor original; un original stale/manipulado se bloquea.
- `GBC_STOP_PROGRAMMING_TYPE_BY_CODE` permite mapear explícitamente `classification_code=PROGRAMMED|UNPROGRAMMED` sin heurísticas. Ejemplo local: `MECHANICAL=UNPROGRAMMED`.
- Una clasificación observada en D-1 permanece reconciliable aunque actualmente esté inactiva.

La regla de suma total, una eventual aplicabilidad distinta por línea, agrupación de múltiples correcciones y persistencia/aprobación siguen pendientes de definición o del contrato de Validación KPI. Ver `docs/spec-04-status.md`.

## Spec 05 - masas elaboradas y entregadas

- `GET /api/v1/oee-validation/masses/policy`: publica la estrategia de fuente, códigos semánticos, identidad del dato y reglas pendientes.
- `GET /api/v1/oee-validation/masses/reference`: referencia read-only D-1 por `línea + variable + UOM`; ya no depende del `itemId` ni de tablas legacy.
- Fuente preferida: `telemetry.derived_metric` con ventana exacta D-1 de línea y códigos configurables `ELABORATED_MASS` / `DELIVERED_MASS`.
- En `AUTO`, si falta el derived metric de una variable, se usa fallback canónico explícitamente provisional: `batch_execution` para elaborada y `finished_product_output` para entregada, sólo con UOM `dimension_code=MASS`.
- `POST /masses/prevalidate` vuelve a consultar el original en servidor y bloquea un `originalValue` stale/manipulado.
- Un dato inexistente no se convierte silenciosamente en cero: Spec 05 es corrección de datos registrados/calculados, no captura manual desde cero.
- Se aceptan fracciones y cero cuando el valor canónico existe; negativos se rechazan y no se aplica redondeo adicional.
- La relación permitida elaborada/entregada, lógicas especiales, redondeo oficial, precedencia después de persistir y el workflow de aprobación siguen pendientes de definición/contrato.


## Spec 06 - justificación de la corrección

- Motivos permitidos: `OPERATIVE` y `SYSTEM`.
- `OPERATIVE` exige `operationalCause` + `actionPlan`; `SYSTEM` exige `incidentNumber`.
- La prevalidación devuelve `justification` normalizada y descarta campos del motivo anterior.
- `GET /api/v1/oee-validation/justification/policy` publica la matriz efectiva.
- Longitudes opcionales: `GBC_INCIDENT_NUMBER_MAX_LENGTH`, `GBC_OPERATIONAL_CAUSE_MAX_LENGTH`, `GBC_ACTION_PLAN_MAX_LENGTH`; `0`/ausente significa pendiente de definición y no impone un máximo arbitrario.
- `GBC_INCIDENT_NUMBER_REGEX` permite validar localmente un formato cuando exista; no sustituye una futura validación contra GB Support.
- La política de datos sensibles y la persistencia atómica con la validación siguen pendientes.

## Spec 07 - fecha, hora y usuario de la validación

- Las tres prevalidaciones devuelven `auditEvidence` con `tid`, `oid`, identidad inmutable, snapshots de presentación y `eventTimestamp`.
- En PostgreSQL/Lakebase se añade `canonicalUserId` resuelto desde `app_security.user_identity.user_id`, junto con snapshot de `display_name`, `corporate_email` e `is_active`.
- La identidad inmutable externa sigue siendo `tid + oid`; `canonicalUserId` prepara el enlace al FK interno de una futura entidad `kpi_validation`.
- `eventTimestamp` es un `Instant` absoluto ISO-8601/UTC; la zona/formato de presentación siguen pendientes.
- `GET /api/v1/oee-validation/audit/policy` publica la fuente canónica/fallback y los puntos pendientes.
- La evidencia sigue marcada `PREVALIDATION_EVIDENCE_ONLY_PENDING_PERSISTENCE`: no se representa como auditoría histórica hasta que exista almacenamiento del workflow.

## Artefactos de build

`target/` es salida generada y está ignorado por Git. **No debe tomarse como fuente canónica ni desplegarse un JAR recibido dentro de un ZIP sin reconstruirlo.** Antes de empaquetar/desplegar una integración nueva, ejecutar:

```bash
rm -rf */target target
mvn clean verify
```

Después, empaquetar el runtime requerido. Esto es especialmente importante cuando se integran patches incrementales: un `target/` anterior puede arrancar correctamente pero no contener los endpoints/clases más recientes.

