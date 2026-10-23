# gb-gbc2-ms-kpi-validation

Microservicio incremental para **Validación KPI: OEE**, con Specs 01 a 07 integradas. La migración a la baseline física v1.2 se está realizando por especificación; **Specs 01, 02 y 03 ya están alineadas/revisadas contra v1.2 en su alcance actual**.

No crea tablas ni columnas nuevas de workflow KPI. Sobre la base canónica actual **lee y escribe** únicamente las estructuras ya existentes que corresponden a Specs 01–03. Para Spec 01/02 utiliza:

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
- `POST /api/v1/oee-validation/stop-times/validations`
- `GET /api/v1/oee-validation/stop-times/validations/{validationId}`
- `GET /api/v1/oee-validation/masses/policy`
- `GET /api/v1/oee-validation/masses/reference?plantId=&lineId=&businessDate=`
- `POST /api/v1/oee-validation/masses/prevalidate`
- `POST /api/v1/oee-validation/masses/validations`
- `GET /api/v1/oee-validation/masses/validations/{validationId}`
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

En PostgreSQL/Lakebase v1.2, la autorización funcional ya se resuelve desde `app_security`; no depende de `GBC_KPI_VALIDATOR_ASSIGNMENTS`. Los códigos de métrica configurables, centralizados y reemplazables, son:

- role code: `KPI_OEE_VALIDATOR` (`GBC_KPI_VALIDATOR_ROLE_CODE`)
- capability code: `KPI_OEE_VALIDATION` (`GBC_KPI_VALIDATOR_CAPABILITY_CODE`)
- admin capability code: `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` (`GBC_KPI_VALIDATOR_ADMIN_CAPABILITY_CODE`)

El acceso funcional requiere simultáneamente:

1. Identidad activa en `app_security.user_identity`.
2. `user_role_assignment` vigente y **específico para la planta** con el rol configurado.
3. Mapeo vigente `role_capability` hacia la capability configurada.
4. Acceso genérico vigente en `app_security.user_plant_access`.
5. Asignación vigente `core.line_plant_assignment` entre la línea y la planta.

Cada operación valida además que la planta no tenga más de dos **validadores OEE efectivos**. El conteo exige asignación KPI/capability vigente **y** `user_plant_access` vigente, para que un rol residual sin acceso general no consuma uno de los dos lugares. Como el DDL v1.2 no incluye un constraint que impida crear una tercera asignación, este micro protege su propio write path administrativo con serialización transaccional por planta y vuelve a validar el máximo al usar el módulo. Un escritor externo directo a BD todavía podría romper esa cardinalidad.

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

La regla temporal queda cerrada como **D-1 calendario 00:00–00:00 en `core.plant.timezone_name`**, confirmado por Data. No se interpreta como ventana móvil de 24 horas transcurridas.


### Cierre Spec 01

- Una propuesta OEE se identifica por `kpi_result_id` y sólo puede crearse una vez a través de este micro; el submit se serializa y vuelve a comprobar existencia dentro de la transacción.
- `original_value` queda congelado como evidencia de la propuesta. Un recálculo posterior del KPI fuente no modifica ni rebasa la validación ya creada.
- `expires_at` se calcula al final del día local de **creación de la propuesta** usando `core.plant.timezone_name`, de modo que una propuesta sobre D-1 conserve una ventana de aprobación válida.
- El micro crea únicamente estado `PENDING`; aprobar, rechazar y aplicar el valor efectivo corresponde al módulo Aprobación KPI.

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
| `GBC_MASS_PERFORMANCE_SOURCE_MODE` | `AUTO` (default), `DERIVED_METRIC_ONLY` o `SCHEDULING_FALLBACK_ONLY` |
| `GBC_ELABORATED_MASS_METRIC_CODE` | `kpi.metric_definition.metric_code` para masa elaborada; default `ELABORATED_MASS` |
| `GBC_DELIVERED_MASS_METRIC_CODE` | `kpi.metric_definition.metric_code` para masa entregada; default `DELIVERED_MASS` |
| `DEV_TENANT_ID`, `DEV_USER_OID` | identidad inmutable usada únicamente con `AUTH_MODE=mock` |
| `DEV_USER_DISPLAY_NAME`, `DEV_USER_EMAIL` | snapshots opcionales de presentación para Spec 07 en modo mock |
| `JDBC_URL`, `DB_USER`, `DB_PASSWORD` | modo `postgres-password` |
| variables Databricks/Lakebase | modo `lakebase-oauth` |

Ejemplo local de asignaciones (`memory`):

```text
GBC_KPI_VALIDATOR_ASSIGNMENTS=1=<tenant>/<oid1>,<tenant>/<oid2>;2=<tenant>/<oid1>
```

## Importante

- `prevalidate` resuelve el valor OEE/KPI original desde `kpi.kpi_result`, devuelve `sourceRecordKey`, valida una sola oportunidad y declara estado objetivo `PENDING`. `POST /api/v1/oee-validation/validations` persiste la propuesta en `kpi.kpi_validation` + `kpi.kpi_validation_detail`.
- `GET /api/v1/oee-validation/workflow/policy` publica los estados canónicos `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`; Aprobación KPI conserva la segunda etapa.
- Ver `docs/spec-01-status.md`, `docs/spec-02-status.md` y `docs/spec-03-status.md` para el alcance ya revisado en esta iteración; Specs posteriores conservan sus documentos incrementales hasta que se revisen.

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

- `GET /api/v1/oee-validation/stop-times/policy`: contrato actual de fuente, `oee_stop_type_code`, clasificación inactiva, reconciliación, persistencia y pendientes.
- `GET /api/v1/oee-validation/stop-times/reference?plantId=&lineId=&businessDate=`: reconstrucción read-only sobre el DDL v1.2 (`stop_event` + `stop_fragment` + última `classification_history`). Expone el catálogo activo global/organización de la planta incluso con `0` segundos y retiene clasificaciones inactivas cuando sí fueron observadas en D-1.
- `POST /api/v1/oee-validation/stop-times/prevalidate`: valida una corrección y vuelve a resolver en servidor el valor original; un original stale/manipulado se bloquea.
- `POST /api/v1/oee-validation/stop-times/validations`: persiste la propuesta canónica `PENDING` con `stop_classification_id`, original y propuesto.
- `GET /api/v1/oee-validation/stop-times/validations/{validationId}`: devuelve trazabilidad completa para reconciliación/aprobación.
- PROGRAMMED/UNPROGRAMMED proviene directamente de `stops.stop_classification.oee_stop_type_code`; no se requiere mapping externo.
- Una clasificación observada en D-1 permanece reconciliable aunque actualmente esté inactiva.
- Sólo `PROGRAMMED` y `UNPROGRAMMED` son editables; otros/null `oee_stop_type_code` quedan visibles para diagnóstico pero bloqueados para corrección.
- Cierre de agrupación: una clasificación corregida = una propuesta de validación independiente. Varias clasificaciones generan varias propuestas.

El único punto funcional de Spec 04 que permanece abierto es la fórmula exacta de consistencia/suma total entre clasificaciones; no se hereda una regla de 24 h sin evidencia. Ver `docs/spec-04-status.md`.

## Spec 05 - masas elaboradas y entregadas

- `GET /api/v1/oee-validation/masses/policy`: publica la estrategia de fuente, códigos semánticos, identidad del dato y reglas pendientes.
- `GET /api/v1/oee-validation/masses/reference`: referencia read-only D-1 por `línea + variable + UOM`; ya no depende del `itemId` ni de tablas legacy.
- Fuente canónica confirmada por Data: `scheduling.batch_execution` para masa elaborada y `scheduling.finished_product_output` para masa entregada.
- Si una salida terminada está en una UOM no-masa, se convierte mediante una `manufacturing.packaging_conversion` vigente hacia una UOM con `dimension_code=MASS`. La propuesta persistida se identifica con `kpi_validation_detail.metric_definition_id`.
- `POST /masses/prevalidate` vuelve a consultar el original en servidor y bloquea un `originalValue` stale/manipulado.
- Un dato inexistente no se convierte silenciosamente en cero: Spec 05 es corrección de datos registrados/calculados, no captura manual desde cero.
- Se aceptan fracciones y cero cuando el valor canónico existe; negativos se rechazan y no se aplica redondeo adicional.
- La relación permitida elaborada/entregada, lógicas especiales, redondeo oficial, precedencia después de persistir y el workflow de aprobación siguen pendientes de definición/contrato.


## Spec 06 - justificación de la corrección

- Motivos permitidos: `OPERATIVE` y `SYSTEM`.
- `OPERATIVE` exige `operationalCause` + `actionPlan`; `SYSTEM` exige `incidentNumber`.
- La prevalidación devuelve `justification` normalizada y descarta campos del motivo anterior.
- OEE general y tiempos de paro persisten motivo/causa/plan/incidente de forma atómica en `kpi.kpi_validation`.
- `GET /api/v1/oee-validation/justification/policy` publica la matriz efectiva.
- Longitudes y regex son opcionales/configurables; no sustituyen validación online contra GB Support.
- Spec 05 conserva la justificación en prevalidación, pero su persistencia sigue bloqueada por el modelo canónico de masas.

## Spec 07 - fecha, hora y usuario de la validación

- Las prevalidaciones devuelven `auditEvidence` con `tid`, `oid`, identidad inmutable y `eventTimestamp`.
- OEE general y tiempos persisten `proposed_by_user_id` + `proposed_at`.
- Las consultas de detalle unen al usuario canónico para exponer `user_id`, ids Entra, nombre/correo actuales y estado de cuenta junto al timestamp persistido.
- `eventTimestamp` usa `Instant` UTC, preservando el instante absoluto.
- `GET /api/v1/oee-validation/audit/policy` publica la semántica de identidad/timestamp.
- La base no conserva snapshot histórico del nombre/correo dentro de la validación; si cambian, el id inmutable y `user_id` siguen siendo reconciliables pero el perfil visible será el actual.

## Artefactos de build

`target/` es salida generada y está ignorado por Git. **No debe tomarse como fuente canónica ni desplegarse un JAR recibido dentro de un ZIP sin reconstruirlo.** Antes de empaquetar/desplegar una integración nueva, ejecutar:

```bash
rm -rf */target target
mvn clean verify
```

Después, empaquetar el runtime requerido. Esto es especialmente importante cuando se integran patches incrementales: un `target/` anterior puede arrancar correctamente pero no contener los endpoints/clases más recientes.



