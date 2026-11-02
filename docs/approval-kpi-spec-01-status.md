# Aprobación KPI — Spec 01: módulo de aprobación de validaciones

## Estado
Cerrada para este microservicio sobre el modelo canónico `kpi.kpi_validation` / `kpi.kpi_validation_detail`. El flujo de Approval resuelve de forma explícita el valor efectivo sin sobrescribir la evidencia original: cada detalle expone `effectiveValue`, que usa `proposedValue` únicamente cuando el estado final es `APPROVED`; en `PENDING`, `REJECTED` y `EXPIRED` usa `originalValue`.

## Comportamiento cerrado
- Solo se listan/consultan validaciones de plantas dentro del alcance del usuario y con capability de aprobación vigente.
- La funcionalidad usa activación por configuración (`GBC_KPI_APPROVAL_FEATURE_CODE`, default `KPI_APPROVAL_ENABLED`).
- La expiración autoritativa es `kpi.kpi_validation.expires_at`; Validación KPI ya la calcula como 23:59:59 del `oee_date` en timezone de planta.
- Solo existe una decisión final: `APPROVED` o `REJECTED`. No se expone reversión en este flujo.
- No se exige comentario adicional porque la Spec 01 lo deja abierto y la tabla canónica no define un campo específico de comentario de decisión.
- Concurrencia: primera decisión válida gana mediante `UPDATE ... WHERE status_code='PENDING' AND expires_at>=?`; un segundo aprobador recibe conflicto.
- `REJECTED` y `EXPIRED` conservan el valor original; `APPROVED` hace efectivo el propuesto en la lógica consumidora, conforme al comentario canónico de `kpi_validation_detail`.
- La respuesta de Approval materializa esa regla con `effectiveValue` y `effectiveValueSource`, evitando que cada consumidor tenga que reinterpretar el estado. `kpi_result.result_value` no se sobrescribe porque la BD canónica declara que el resultado original permanece como evidencia.
- Actor, timestamp, estado y correlation id quedan auditables: `approved_by_user_id`/`approved_at` más trigger `audit.capture_change`.

## API
- `GET /api/v1/kpi-approval/policy`
- `GET /api/v1/kpi-approval/pending`
- `GET /api/v1/kpi-approval/validations/{id}`
- `POST /api/v1/kpi-approval/validations/{id}/decision` body `{ "decision": "APPROVED" | "REJECTED" }`

## Integración de desperdicio
La Spec 01 soporta también validaciones de desperdicio sin requerir una FK directa a `waste.waste_event`. La creación de la validación de desperdicio pertenece a la dependencia APP/tableta indicada por la propia Spec; una vez que esa validación existe en el modelo canónico, el flujo reutiliza `kpi.kpi_validation` / `kpi.kpi_validation_detail`: cuando un detalle referencia `metric_definition_id` cuya `kpi.metric_definition.source_domain = 'WASTE'`, Aprobación lo clasifica como dominio `WASTE` y aplica el mismo ciclo `PENDING -> APPROVED/REJECTED/EXPIRED`. La relación con el dominio de desperdicio es semántica por la métrica canónica; no se inventan columnas ni relaciones físicas nuevas.
