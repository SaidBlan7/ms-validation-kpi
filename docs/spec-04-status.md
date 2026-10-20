# Spec 04 - Validación de tiempos D-1 por clasificación de paro

## Implementado

- Seguridad, alcance planta/línea y D-1 reutilizan las garantías de Specs 01-03.
- Adapter PostgreSQL migrado al DDL canónico v1.2: `stops.stop_event`, `stops.stop_fragment`, `stops.classification_history` y `stops.stop_classification`.
- Ya no se usan entidades legacy inexistentes en v1.2 (`core.stop_classification`, `stops.v_stop_current_classification`, `stop_event.plant_id`, `fragment_started_at`).
- Los fragmentos se recortan al solapamiento con la ventana local D-1 antes de agregarlos por clasificación.
- La clasificación efectiva de cada evento se obtiene de la última fila de `stops.classification_history` al momento de consulta.
- `GET /stop-times/reference` devuelve el catálogo activo global/organización de la planta, incluyendo valores `0`, y además conserva clasificaciones actualmente inactivas cuando fueron observadas en D-1. Cada fila incluye código/nombre, activo actual, segundos, fragmentos/eventos, `programmingType` cuando está configurado y `sourceAggregateKey`.
- `sourceAggregateKey` usa la identidad semántica `line + businessDate + stopClassificationId`, preparada para futura unicidad de workflow.
- Separación PROGRAMMED/UNPROGRAMMED configurable explícitamente con `GBC_STOP_PROGRAMMING_TYPE_BY_CODE`, sintaxis `classificationCode=PROGRAMMED|UNPROGRAMMED`. No se infiere por nombre/código.
- `GET /stop-times/policy` expone la política actual de fuente, mapping, clasificación inactiva, reconciliación de original, total diario, multi-corrección y persistencia.
- `POST /stop-times/prevalidate` re-lee el agregado canónico en servidor y bloquea `originalSeconds` obsoleto/manipulado con `ORIGINAL_STOP_TIME_CHANGED`.
- Como v1.2 no modela aplicabilidad línea-clasificación, se usa explícitamente como fallback el catálogo activo global/organización de la planta; una clasificación activa no observada aparece con `0` y puede prevalidarse. Una clasificación inactiva no observada no se ofrece.
- Clasificaciones observadas en D-1 permanecen visibles/prevalidatables aunque actualmente estén inactivas; se informa warning.
- Se rechazan tiempos faltantes/negativos; cero es válido.
- Se conserva valor original/propuesto, justificación Specs 06, evidencia Specs 07 y `targetStatusIfPersisted=PENDING`.
- App Service, Azure Functions, OpenAPI y monitoreo/correlation ID actualizados.
- Sin INSERT/UPDATE/DELETE ni DDL.

## Pendiente por definición/contrato externo

- La relación canónica PROGRAMMED/UNPROGRAMMED no existe físicamente en `stops.stop_classification`; mientras no exista, debe configurarse el mapping por código.
- No existe relación línea-clasificación. La implementación usa provisionalmente el catálogo activo global/organización de la planta como aplicabilidad; si negocio requiere catálogos distintos por línea, Data/arquitectura debe modelar esa relación o entregar un API/configuración autoritativa.
- La regla matemática exacta de suma/máximo entre clasificaciones está abierta en la Spec 04; no se hereda una regla de 24 horas de otro módulo.
- La Spec no define si varias clasificaciones corregidas pertenecen a una sola validación persistida o a registros separados; la prevalidación actual opera una clasificación por request.
- La persistencia canónica de Spec 01 ya existe para correcciones basadas en `kpi_result`; falta definir cómo una corrección agregada de tiempos/clasificaciones de Spec 04 se representa en `kpi_validation_detail` o en otra estructura antes de habilitar `PENDING` durable para este flujo. Approval KPI y aplicación definitiva siguen siendo dependencias separadas.
- El contrato exacto que demuestra que este agregado de `stops` es idéntico al insumo final del cálculo de efectividad/OEE sigue siendo externo al DDL; por eso se conserva trazabilidad de fuente y no se modifica `kpi_result`.
