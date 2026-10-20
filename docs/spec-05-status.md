# Spec 05 - Validación de masas elaboradas y entregadas

## Implementado

- Seguridad y alcance por planta/línea reutilizando Specs 01-03.
- Sólo D-1, con revalidación server-side al consultar, prevalidar y guardar.
- Referencia por `línea + fecha + variable + UOM` para `ELABORATED_MASS` y `DELIVERED_MASS`.
- Fuente preferida `telemetry.derived_metric`; fallback de scheduling permanece explícitamente provisional.
- Sólo UOM con `dimension_code=MASS`; se permiten cero y fracciones, se rechazan negativos/no numéricos.
- El original se vuelve a cargar en servidor y una referencia stale/manipulada se rechaza.
- `proposedValue` debe diferir del original.
- Persistencia durable `PENDING`: cabecera en `kpi.kpi_validation`, valores/UOM en `kpi.kpi_validation_detail` e identidad/linaje en `kpi.kpi_validation_mass_detail`.
- Una sola oportunidad por `línea + fecha + variable + UOM`, protegida transaccionalmente y por `validation_key UNIQUE`.
- Consulta posterior conserva variable, línea, fecha, original, propuesto, usuario, motivo, fuente y timestamps.
- App Service, Azure Functions, OpenAPI y capabilities alineados.

## Extensión mínima de base requerida

Ejecutar `database/12_kpi_mass_validation.sql`. La extensión es necesaria porque la base canónica previa podía guardar valores/UOM pero no distinguir durablemente masa elaborada vs entregada ni conservar su linaje fuente.

## Pendiente por definición funcional/externa

- Confirmar códigos oficiales de `telemetry.derived_metric` para masa elaborada/entregada.
- Confirmar si los fallbacks de `batch_execution` y `finished_product_output` coinciden con la lógica OEE oficial.
- Definir relación permitida entre masa elaborada y entregada para lógicas especiales.
- Definir precisión/redondeo oficiales.
- Definir precedencia si llegan datos automáticos después de creada la propuesta.
- Aplicación del valor aprobado pertenece al módulo Aprobación KPI.
