# Spec 05 - Validación de masas elaboradas y entregadas

## Implementado

- Seguridad/autorización y alcance planta/línea reutilizando Specs 01-03.
- Sólo D-1; la fecha se revalida en servidor en cada consulta/prevalidación.
- Contrato corregido a `línea + variable + UOM`; se elimina `itemId` legacy porque la Spec 05 no define la validación por item y v1.2 ya no tiene `core.item`.
- Fuente preferida v1.2: `telemetry.derived_metric` exacto para la ventana local D-1, a nivel de línea (`machine_id` y `manufacturing_definition_id` nulos), con códigos semánticos configurables:
  - `GBC_ELABORATED_MASS_METRIC_CODE` (default `ELABORATED_MASS`).
  - `GBC_DELIVERED_MASS_METRIC_CODE` (default `DELIVERED_MASS`).
- Modo configurable `GBC_MASS_PERFORMANCE_SOURCE_MODE`: `AUTO`, `DERIVED_METRIC_ONLY`, `SCHEDULING_FALLBACK_ONLY`.
- En `AUTO`, fallback canónico por variable si no existe derived metric exacto:
  - `ELABORATED_MASS`: suma de `scheduling.batch_execution.quantity` para ejecuciones cuya `manufacturing_definition` tiene `dough_id`, agrupada por UOM MASS y fechada por `COALESCE(ended_at, started_at)`.
  - `DELIVERED_MASS`: suma de `scheduling.finished_product_output.quantity` por UOM MASS y `recorded_at`.
- El fallback se expone como `*_PROVISIONAL`; no se presenta como regla OEE oficial.
- Sólo se aceptan UOM canónicas con `core.unit_of_measure.dimension_code='MASS'`.
- Valores decimales/fraccionarios y cero permitidos cuando existe un valor canónico; negativos rechazados.
- Prevalidación server-side del original: vuelve a cargar `línea + D-1 + variable + UOM` y bloquea stale/manipulación con `ORIGINAL_MASS_VALUE_CHANGED`.
- Si no existe fila canónica para el dato solicitado, se bloquea con `MASS_ORIGINAL_VALUE_NOT_FOUND`; no se convierte ausencia a cero porque esta spec corrige datos existentes y no es KPI Manual.
- Identidad futura de persistencia: `mass:line:<id>:date:<D-1>:variable:<...>:uom:<id>`.
- Justificación Spec 06 y evidencia Spec 07 incluidas.
- `targetStatusIfPersisted=PENDING_APPROVAL` sin afirmar persistencia real.
- Nuevo `GET /api/v1/oee-validation/masses/policy` en App Service y Azure Functions.
- App Service, Functions, OpenAPI y capabilities alineados.
- Sin INSERT/UPDATE/DELETE ni DDL sobre la base canónica.

## Pendiente / requiere definición o dependencia externa

- Confirmar que los códigos de `telemetry.derived_metric` oficiales sean `ELABORATED_MASS` / `DELIVERED_MASS` o sustituirlos por configuración real.
- Confirmar si el fallback `batch_execution -> elaborada` y `finished_product_output -> entregada` coincide con la lógica OEE; mientras tanto queda marcado provisional.
- Regla permitida entre masa elaborada y entregada, especialmente para condimento, conversión, molido/tostado u otras lógicas especiales.
- Precisión decimal y redondeo oficiales; actualmente se preserva el decimal canónico sin redondeo adicional.
- Precedencia cuando el dato automático cambia después de que una propuesta ya fue persistida. Antes de persistir se detecta stale y se bloquea.
- Persistencia de la propuesta, una sola modificación durable, `PENDING_APPROVAL` real, Approval KPI e impacto posterior sobre rendimiento.
- Auditoría persistente/consulta histórica de una validación guardada.

## Decisiones conservadoras

- Ausencia de dato no equivale a cero.
- No se mezclan UOMs.
- No se convierten unidades automáticamente.
- No se impone relación matemática elaborada/entregada sin definición funcional.
- No se presenta el fallback de scheduling como fuente oficial mientras no exista confirmación funcional/OEE.
