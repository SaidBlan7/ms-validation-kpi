# Spec 04 - Validación de tiempos D-1 por clasificación de paro

## Cerrado / implementado

- Seguridad, alcance planta/línea y D-1 reutilizan las garantías cerradas de Specs 01-03.
- La fuente canónica confirmada por Data es `stops.stop_event` + `stops.stop_fragment` + la clasificación/reclasificación aplicable en `stops.classification_history` + `stops.stop_classification`.
- No existe ni se asume una tabla de consolidado diario: el micro deriva el consolidado por línea, clasificación y periodo desde esas estructuras.
- Los fragmentos se recortan al solapamiento con la ventana D-1 calculada con `core.plant.timezone_name` antes de agregarlos por clasificación.
- PROGRAMMED/UNPROGRAMMED se obtiene exclusivamente de `stops.stop_classification.oee_stop_type_code`.
- Una clasificación cuyo `oee_stop_type_code` sea distinto de `PROGRAMMED`/`UNPROGRAMMED` o sea NULL puede permanecer visible para diagnóstico, pero queda fuera del alcance editable de Spec 04 y el servidor bloquea su corrección.
- `GET /stop-times/reference` expone por clasificación el valor original en segundos, código/nombre, vigencia, tipo OEE y trazabilidad del agregado.
- Clasificaciones activas del ámbito global/organización se muestran aunque su valor sea cero; una clasificación actualmente inactiva se conserva cuando fue observada en D-1 para no perder reconciliación histórica.
- `POST /stop-times/prevalidate` vuelve a leer el agregado canónico y rechaza original obsoleto/manipulado, valores negativos, valores sin cambio, clasificación fuera del scope OEE y justificación inválida.
- La propuesta sólo puede realizarse una vez por `lineId + businessDate + stopClassificationId` dentro de este micro.
- `POST /stop-times/validations` revalida todo en servidor y persiste una propuesta `PENDING` en `kpi.kpi_validation` + `kpi.kpi_validation_detail`, usando `stop_classification_id`, `original_value` y `proposed_value`.
- La concurrencia para el mismo dato se serializa con advisory lock PostgreSQL y la existencia se vuelve a verificar dentro de la transacción.
- `GET /stop-times/validations/{validationId}` devuelve línea, fecha, clasificación, tipo OEE, original/propuesto, justificación, actor y timestamps para reconciliación/Aprobación KPI.
- La decisión de cierre para múltiples correcciones es **una clasificación por propuesta de validación**. Si un usuario corrige varias clasificaciones, se generan propuestas independientes. Esto conserva una oportunidad por dato, decisión de aprobación independiente y auditoría inequívoca.
- Una clasificación observada en D-1 puede corregirse aunque después haya quedado inactiva; el estado actual no reescribe la historia del día objetivo.
- No se modifica el histórico de `stops`; la corrección permanece como propuesta pendiente hasta Aprobación KPI.

## Único punto que permanece abierto por la propia Spec

- La regla matemática de consistencia/suma máxima entre clasificaciones no está definida. No se aplica una regla artificial de 24 horas ni otra relación entre categorías. El micro sí valida formato, signo, original vigente y unicidad; una regla agregada adicional deberá incorporarse cuando negocio publique la fórmula OEE correspondiente.

## Limitaciones de integridad/modelo ya conocidas

- El modelo canónico no tiene una relación explícita línea-clasificación. Para referencia se usa el catálogo global/organización y las clasificaciones efectivamente observadas en D-1. Si negocio necesitara un catálogo habilitado distinto por línea, requerirá una fuente autoritativa adicional.
- La BD no impone un UNIQUE físico transversal para `(line_id, oee_date, stop_classification_id)` entre cabecera y detalle. El micro lo protege transaccionalmente; un escritor externo directo a BD podría omitir esa regla.
- Aprobación KPI continúa siendo responsable de aprobar/rechazar y de hacer efectivo el valor propuesto en el cálculo.
