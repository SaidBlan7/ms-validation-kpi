# Spec 05 - Validación de masas elaboradas y entregadas

## Implementado con la base canónica actual

- Seguridad y alcance por planta/línea reutilizando Specs 01-03.
- Sólo D-1, con revalidación server-side.
- Referencia por línea + fecha + variable + UOM para `ELABORATED_MASS` y `DELIVERED_MASS`.
- Fuente preferida `telemetry.derived_metric`; fallback de scheduling permanece explícitamente provisional.
- Sólo UOM con `dimension_code=MASS`; se permiten cero y fracciones y se rechazan negativos/no numéricos.
- El original se vuelve a cargar en servidor y una referencia stale/manipulada se rechaza.
- `proposedValue` debe diferir del original.
- Motivo/evidencia de Spec 06 y auditoría previa al guardado de Spec 07 se aplican también a este flujo.

## Pendiente por modelo de datos (sin crear tablas nuevas)

La base canónica actual puede guardar `original_value`, `proposed_value` y `uom_id` en `kpi.kpi_validation_detail`, pero no identifica durablemente si ese detalle corresponde a `ELABORATED_MASS` o `DELIVERED_MASS`, ni conserva la identidad del agregado fuente. Por esa razón el micro NO asume `kpi_validation_mass_detail` ni ninguna otra extensión. La prevalidación queda disponible y `readyForSubmission=false` hasta que exista una solución canónica aprobada.

## Pendiente por definición funcional/externa

- Confirmar códigos oficiales de `telemetry.derived_metric` para masa elaborada/entregada.
- Confirmar si los fallbacks de `batch_execution` y `finished_product_output` coinciden con la lógica OEE oficial.
- Definir relación permitida entre masa elaborada y entregada para lógicas especiales.
- Definir precisión/redondeo oficiales.
- Definir precedencia si llegan datos automáticos después de una futura propuesta persistida.
- Aplicación del valor aprobado pertenece al módulo Aprobación KPI.
