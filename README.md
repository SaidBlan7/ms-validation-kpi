# Cierre Aprobación KPI – Spec 01 / Waste

Archivos nuevos/modificados únicamente.

## Ajuste
- Aprobación KPI reutiliza `kpi.kpi_validation` / `kpi.kpi_validation_detail` también para validaciones de desperdicio.
- Una validación se clasifica como `WASTE` cuando sus detalles referencian métricas canónicas cuyo `kpi.metric_definition.source_domain = 'WASTE'` y no mezcla detalles de paro OEE.
- No se crea ni se requiere una FK directa a `waste.waste_event` o `waste.waste_subclassification` para el flujo de aprobación.
- El ciclo de decisión sigue siendo `PENDING -> APPROVED/REJECTED/EXPIRED` con la auditoría existente.
- `supportsWasteValidations()` queda habilitado.

## Validación
Los módulos `application` y `adapter-postgres` compilan con Java 21 después del ajuste.
