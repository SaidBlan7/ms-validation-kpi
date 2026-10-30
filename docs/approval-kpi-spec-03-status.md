# Aprobación KPI — Spec 03: visualización de validación OEE y desperdicios

Estado: **IMPLEMENTADA**.

## Cobertura funcional

- `GET /api/v1/kpi-approval/pending` conserva la vista rápida de pendientes.
- `GET /api/v1/kpi-approval/validations` agrega listado histórico de solo lectura con filtros `status`, `domain`, `plantId`, `fromDate`, `toDate` y paginación `offset/limit` (50 por default, máximo 200).
- `GET /api/v1/kpi-approval/validations/{id}` mantiene el detalle individual para estados PENDING, APPROVED, REJECTED y EXPIRED.
- La autorización sigue siendo server-side por acceso general de planta + asignación/capability específica de Aprobación KPI.
- Antes de listar o consultar, los PENDING cuyo `expires_at` ya pasó se transicionan a `EXPIRED`; por lo tanto `decidable=false` para registros históricos.
- OEE y WASTE se distinguen en el mismo contrato. WASTE se identifica canónicamente por detalles con `metric_definition.source_domain = 'WASTE'` y sin mezcla de clasificación de paro OEE.
- El detalle expone valores original/propuesto y referencias legibles disponibles: KPI, métrica, clasificación de paro, agrupación OEE y unidad.
- Se expone el ID estable del validador/decisor y su `display_name` actual cuando existe en `app_security.user_identity`.
- Justificación, causa, plan de acción e `incident_reference` permanecen disponibles como evidencia común del flujo de origen.

## Decisiones para preguntas abiertas

- Filtros iniciales: estado, dominio, planta y rango de fecha objetivo.
- Orden: `proposedAt` descendente y `validationId` descendente para desempate.
- Paginación: offset/limit, máximo 200 filas por petición.
- Las validaciones decididas o expiradas pueden abrirse en lectura mientras el registro canónico exista; esta capa no introduce TTL adicional.
- Para Waste se muestran los campos comunes y las métricas/evidencia ya existentes en el modelo canónico; no se inventan campos de retorno/transfer fuera de `kpi_validation_detail` / `metric_definition`.

## Alineación con base canónica

La lectura usa exclusivamente estructuras existentes:

- `kpi.kpi_validation`
- `kpi.kpi_validation_detail`
- `kpi.kpi_result` / `kpi.kpi_definition`
- `kpi.metric_definition`
- `stops.stop_classification`
- `core.unit_of_measure`
- `app_security.user_identity`

No requiere cambios de esquema.

## Nota sobre “sin dato anterior”

El esquema canónico actual define `kpi_validation_detail.original_value` como `NOT NULL`. Por ello esta implementación muestra el original siempre que exista un detalle canónico. Si un futuro flujo Waste requiere una propuesta sin valor anterior, esa semántica necesitará primero soporte en el modelo de datos; el micro no inventa un sentinel.
