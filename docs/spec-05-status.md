# Spec 05 - Validación de masas elaboradas y entregadas

## Cerrada con la base canónica actual

- Seguridad y alcance por planta/línea reutilizando Specs 01-03.
- Sólo D-1, calculado y revalidado con la zona horaria autoritativa de la planta.
- `ELABORATED_MASS` se deriva de `scheduling.batch_execution.quantity/uom_id` para ejecuciones de manufactura con masa (`manufacturing_definition.dough_id`).
- `DELIVERED_MASS` se deriva de `scheduling.finished_product_output`; cuando la salida no está ya en una UOM de dimensión `MASS`, se utiliza una `manufacturing.packaging_conversion` vigente desde la UOM capturada hacia una UOM `MASS`.
- No se mezclan UOM diferentes. La corrección se identifica por línea + fecha + variable/métrica + UOM.
- La identidad durable de la variable usa `kpi.kpi_validation_detail.metric_definition_id` contra `kpi.metric_definition`.
- Los códigos de catálogo son configurables mediante `GBC_ELABORATED_MASS_METRIC_CODE` y `GBC_DELIVERED_MASS_METRIC_CODE`, con defaults `ELABORATED_MASS` y `DELIVERED_MASS`.
- El original se vuelve a cargar en servidor inmediatamente antes de guardar; si cambió, la propuesta se rechaza y el usuario debe refrescar.
- Ausencia de dato fuente no se interpreta como cero ni como captura manual.
- Se aceptan cero y fracciones; se rechazan negativos y valores sin UOM canónica de dimensión `MASS`. Los valores se conservan con la precisión canónica sin redondeo adicional del micro.
- Cada línea/fecha/métrica/UOM sólo puede generar una propuesta dentro de Validación KPI.
- La propuesta se persiste atómicamente en `kpi.kpi_validation` + `kpi.kpi_validation_detail` con estado `PENDING`, original/propuesto, UOM, `metric_definition_id`, motivo y auditoría.
- Si el dato automático cambia después de persistir, el original guardado permanece como evidencia inmutable de la propuesta; la resolución del cambio corresponde al flujo de Aprobación/recalculo, no a una edición silenciosa desde Validación.
- Las masas elaboradas y entregadas se corrigen de forma independiente. Las fórmulas especiales de rendimiento no se infieren ni se validan en este módulo; pertenecen al cálculo OEE y a la aprobación.

## Dependencias operativas

- Deben existir `kpi.metric_definition` activos para los dos códigos configurados. Si la definición fija `uom_id`, éste debe coincidir con la UOM de la propuesta.
- Para producto terminado capturado en una UOM no-masa debe existir una `manufacturing.packaging_conversion` vigente y determinística hacia una UOM `MASS`; de lo contrario ese valor no aparece como masa entregada corregible.
- La aplicación del valor aprobado sigue perteneciendo al módulo Aprobación KPI.
