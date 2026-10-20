# Spec 04 - Validación de tiempos D-1 por clasificación de paro

## Implementado

- Seguridad, alcance planta/línea y D-1 reutilizan las garantías de Specs 01-03.
- Fuente canónica: `stops.stop_event`, `stops.stop_fragment`, última `stops.classification_history` y `stops.stop_classification`.
- Los fragmentos se recortan al solapamiento con la ventana local D-1 antes de agregarlos por clasificación.
- `GET /stop-times/reference` devuelve por clasificación el valor original en segundos, código/nombre, estado actual y trazabilidad del agregado.
- PROGRAMMED/UNPROGRAMMED se lee directamente de `stops.stop_classification.oee_stop_type_code`; ya no existe mapping externo por environment.
- El catálogo usa clasificaciones globales o de la organización de la planta. Las activas aparecen aunque tengan valor `0`; las inactivas se conservan cuando fueron observadas en D-1.
- `POST /stop-times/prevalidate` vuelve a leer el agregado canónico, rechaza original obsoleto/manipulado, valores negativos, valores sin cambio y justificación inválida.
- La propuesta sólo puede realizarse una vez por `lineId + businessDate + stopClassificationId` dentro de este micro.
- `POST /stop-times/validations` revalida todo en servidor y persiste una propuesta `PENDING` en `kpi.kpi_validation` + `kpi.kpi_validation_detail`, usando `stop_classification_id`, `original_value` y `proposed_value`.
- La concurrencia para el mismo dato se serializa con advisory lock PostgreSQL y se vuelve a revisar existencia dentro de la transacción.
- `GET /stop-times/validations/{validationId}` expone línea, fecha, clasificación, tipo OEE, valor original/propuesto, motivo, actor y timestamps para reconciliación/Aprobación KPI.
- App Service, Azure Functions, OpenAPI y auditoría/correlation ID quedan conectados al mismo flujo.
- No se modifica el histórico de `stops`; la corrección queda como propuesta pendiente, como exige el flujo de dos etapas.

## Pendiente por definición funcional o modelo de datos

- La regla matemática exacta para validar suma máxima/consistencia entre clasificaciones sigue abierta; no se inventa una regla de 24 h.
- La Spec no confirma si múltiples clasificaciones deben compartir una sola cabecera de validación. La implementación persiste una clasificación por validación para respetar la regla de una sola oportunidad sin inventar agrupación.
- El modelo canónico no tiene una relación línea-clasificación; se usa el catálogo global/organización de la planta más clasificaciones históricamente observadas. Si negocio requiere aplicabilidad distinta por línea, la base o un catálogo autoritativo deberá modelarlo.
- `oee_stop_type_code` permanece extensible en BD; Spec 04 sólo reconoce funcionalmente PROGRAMMED y UNPROGRAMMED para este flujo. Valores distintos se exponen como dato canónico pero requieren decisión funcional.
- Approval KPI sigue siendo responsable de aprobar/rechazar y de aplicar el valor efectivo al cálculo OEE.
- La base no impone un UNIQUE físico para `(line_id, oee_date, stop_classification_id)` a través de cabecera/detalle. El micro lo protege transaccionalmente, pero un escritor externo podría saltarse esa regla.
