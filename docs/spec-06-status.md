# Spec 06 - Motivo, causa, plan de acción e incidente

## Implementado

- Catálogo funcional cerrado a `OPERATIVE` y `SYSTEM` mediante `ValidationReasonType`.
- `OPERATIVE` exige `operationalCause` y `actionPlan` no vacíos.
- `SYSTEM` exige `incidentNumber` no vacío.
- Normalización en servidor: `strip()` de espacios exteriores, normalización CR/LF en texto libre y rechazo de caracteres de control no soportados.
- Al cambiar de motivo, los campos que ya no corresponden se descartan para evitar evidencia obsoleta:
  - `SYSTEM` conserva sólo `incidentNumber`.
  - `OPERATIVE` conserva sólo `operationalCause` + `actionPlan`.
- Las tres prevalidaciones devuelven `justification` normalizada junto con valor original/propuesto.
- Se informa con warning cuando llegaron campos obsoletos del motivo anterior y fueron descartados.
- `GET /api/v1/oee-validation/justification/policy` publica motivos, campos requeridos y la política técnica vigente.
- Longitudes máximas opcionales y configurables, medidas en Unicode code points:
  - `GBC_INCIDENT_NUMBER_MAX_LENGTH`
  - `GBC_OPERATIONAL_CAUSE_MAX_LENGTH`
  - `GBC_ACTION_PLAN_MAX_LENGTH`
  - valor ausente o `0` significa que la longitud de negocio/UX sigue sin definición y no se impone un máximo arbitrario.
- Formato de incidente opcional mediante `GBC_INCIDENT_NUMBER_REGEX`; si está configurado se valida localmente y un regex inválido impide arrancar el runtime.
- Los logs incluyen `reasonType`, actor, alcance, resultado y correlation ID, pero no registran causa/plan/incidente completos.
- App Service, Azure Functions, OpenAPI y capabilities actualizados.
- Sin INSERT/UPDATE/DELETE ni DDL sobre la base canónica.

## Pendiente / dependencia externa

- Para OEE general, la justificación ya se persiste atómicamente en `kpi.kpi_validation`; Stop Time y Mass Performance continúan sólo en prevalidación hasta definir su persistencia canónica.
- Mostrar la justificación desde Aprobación KPI e histórico/auditoría persistente.
- Valores oficiales de longitud UX si se desea que la configuración sea canónica y no sólo técnica.
- Patrón/formato oficial del número de incidente si no se acepta configuración local.
- Validación online del incidente contra GB Support.
- Política oficial para datos sensibles en campos de texto libre.
- Comentarios adicionales de aprobación/rechazo, que pertenecen al módulo de Aprobación KPI.

## Decisiones conservadoras

- No se inventa un máximo de negocio: los límites sólo se aplican cuando están explícitamente configurados.
- No se inventa prefijo `INC-`, UUID u otro patrón; el regex es opcional y administrable.
- La validación regex no se presenta como validación contra GB Support.
- No se intenta detectar/redactar automáticamente datos sensibles sin una política aprobada.
- Los consumidores deben escapar texto libre al renderizarlo; el backend normaliza y valida, no transforma HTML en contenido distinto.
