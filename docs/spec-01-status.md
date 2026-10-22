# Spec 01 — CERRADA contra base canónica actual

## Decisiones de cierre

- **Fecha elegible:** exclusivamente D-1 calendario de la planta. No se usa una ventana móvil de 24 horas y no se saltan fines de semana/festivos.
- **Zona horaria autoritativa:** `core.plant.timezone_name`, confirmada por Data.
- **Ventana OEE usada por este módulo:** 00:00 a 00:00 del día calendario D-1 en la zona de la planta. El micro no inventa agregaciones por turno/producto/ejecución cuando busca el valor original OEE.
- **Expiración de la propuesta:** fin del día local en que se crea la propuesta. Esta decisión conserva el escenario funcional de propuestas creadas cerca del cierre con poco tiempo para aprobación y satisface `expires_at >= proposed_at`.
- **Cambio posterior del dato fuente:** el `original_value` persistido en `kpi.kpi_validation_detail` es el snapshot de evidencia de la propuesta. Un recálculo posterior de `kpi.kpi_result` no modifica ni rebasa la propuesta ya creada; cualquier decisión posterior pertenece al flujo de Aprobación KPI.
- **Concurrencia / única oportunidad:** la identidad del dato OEE es `kpi_result_id`; el write path usa advisory lock transaccional + re-check antes de insertar.
- **Códigos OEE:** no se hardcodean OEE 1/OEE 2/OEE completo. El contrato recibe un `metricCode` y sólo acepta valores existentes en `kpi.kpi_definition.kpi_code` con un resultado canónico diario para planta/línea/D-1.

## Implementado

- Autenticación Entra y resolución de identidad canónica en `app_security.user_identity`.
- Autorización por planta/línea, capability/rol específico y asignación KPI por planta.
- Máximo de dos validadores efectivos por planta.
- Activación del módulo mediante `configuration.configuration_definition` + `configuration.configuration_assignment` (`KPI_OEE_VALIDATION_ENABLED`, configurable).
- Revalidación de D-1 en servidor en consulta, prevalidación y submit.
- Lectura del valor original desde `kpi.kpi_result` + `kpi.kpi_definition`.
- Conservación del valor original y propuesto.
- Motivos `OPERATIVE` y `SYSTEM` con reglas de Spec 06.
- Persistencia atómica en `kpi.kpi_validation` + `kpi.kpi_validation_detail` con estado inicial `PENDING`.
- Usuario y timestamp canónicos (`proposed_by_user_id`, `proposed_at`).
- Contexto de auditoría actor/source/correlation para `audit.capture_change`.
- Una sola propuesta por `kpi_result_id` dentro del micro con serialización transaccional.
- Consulta de detalle persistido con actor, timestamp, motivo, original y propuesto.
- App Service y Azure Functions exponen creación y consulta de validación.

## Dependencias externas que no bloquean el cierre de Spec 01

- **Aprobación KPI** es el segundo paso y es quien aprueba/rechaza/aplica; Validación KPI sólo crea `PENDING`.
- **GB Support:** Spec 06 sólo confirma captura obligatoria del número de incidente para `SYSTEM`; la validación online del ticket no forma parte del cierre actual.
- **Datos maestros/configuración:** deben existir los `kpi_code`, roles/capabilities, asignaciones y feature flag correspondientes. Son datos operativos, no lógica faltante del micro.

## Nota de esquema

El comentario actual del DDL de `kpi.kpi_validation.expires_at` menciona 23:59:59 del `oee_date`. Para un flujo que sólo crea propuestas sobre D-1 eso contradice `CHECK (expires_at >= proposed_at)`. El servicio adopta como regla ejecutable el cierre del **día local de creación de la propuesta**, sin requerir una tabla o columna nueva. Si Data actualiza ese comentario en una versión futura, no cambia el contrato de este micro.
