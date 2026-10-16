# Spec 06 - Motivo, causa, plan de acción e incidente

## Implementado

- Catálogo funcional cerrado a `OPERATIVE` y `SYSTEM` mediante `ValidationReasonType`.
- `OPERATIVE` exige `operationalCause` y `actionPlan` no vacíos.
- `SYSTEM` exige `incidentNumber` no vacío.
- Normalización básica en servidor: `strip()` de espacios exteriores, normalización CR/LF en texto libre y rechazo de caracteres de control no soportados.
- Al cambiar de motivo, los campos que ya no corresponden se descartan de la justificación normalizada para evitar arrastrar evidencia obsoleta:
  - `SYSTEM` conserva sólo `incidentNumber`.
  - `OPERATIVE` conserva sólo `operationalCause` + `actionPlan`.
- Las prevalidaciones generales, de tiempos y de masas devuelven `justification` normalizada junto con valor original/propuesto.
- Se informa con warning cuando llegaron campos obsoletos del motivo anterior y fueron descartados.
- Nuevo `GET /api/v1/oee-validation/justification/policy` para que frontend/consumidores conozcan motivos y campos requeridos sin duplicar la regla.
- `incidentNumber` se acepta como texto no vacío sin inventar regex o prefijo.
- Logs operacionales incluyen `reasonType`, actor, alcance, resultado y correlation ID, pero no registran causa/plan/incidente completos para reducir exposición de texto libre.
- App Service, Azure Functions, OpenAPI y capabilities actualizados.
- Sin INSERT/UPDATE/DELETE ni DDL sobre la base canónica.

## Pendiente / no implementable todavía

- Persistir la justificación de forma atómica con la propuesta de validación.
- Mostrar la justificación desde Aprobación KPI e histórico/auditoría persistente.
- Longitud máxima oficial para causa y plan de acción; la Spec 06 la deja pendiente de UX.
- Patrón/formato oficial del número de incidente.
- Validación del incidente contra GB Support.
- Política oficial para datos sensibles en campos de texto libre.
- Comentarios adicionales de aprobación/rechazo, que pertenecen al módulo de Aprobación KPI y siguen sin definición.

## Decisiones conservadoras

- No se impone una longitud máxima arbitraria mientras UX no la defina.
- No se valida prefijo `INC-`, número, UUID ni ningún formato de ticket porque la especificación confirma únicamente la captura.
- No se intenta detectar o redactar automáticamente datos sensibles: se expone el estado `PENDING_DEFINITION` hasta contar con una política aprobada.
- La sanitización no transforma HTML en contenido distinto; los consumidores deben escapar texto al renderizarlo. El backend sólo normaliza espacios/saltos y rechaza caracteres de control incompatibles.
