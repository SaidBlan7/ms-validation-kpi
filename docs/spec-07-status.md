# Spec 07 - Visualización de fecha, hora y usuario de la validación

## Implementado

- Las prevalidaciones OEE general, tiempos y masas devuelven `auditEvidence`.
- Identificador inmutable del actor: combinación `Entra tenant id (tid) + object id (oid)`.
- Se conservan por separado `actorTenantId` y `actorObjectId`.
- Se capturan `displayNameSnapshot` y `principalNameSnapshot` cuando el proveedor de identidad los entrega; no son obligatorios ni se usan para autorización.
- `eventTimestamp` se genera con el `Clock` del servicio como `Instant` y se serializa ISO-8601/UTC, preservando el instante absoluto.
- App Service obtiene snapshots opcionales desde claims `name`, `preferred_username`, `email` o `upn`.
- Azure Functions obtiene los equivalentes desde `X-MS-CLIENT-PRINCIPAL`/`userDetails`.
- Modo mock permite `DEV_USER_DISPLAY_NAME` y `DEV_USER_EMAIL`.
- Nuevo endpoint `GET /api/v1/oee-validation/audit/policy` en App Service y Azure Functions.
- Capabilities explicitan el estado parcial de Spec 07.
- No se registran nombre/correo en los logs operativos añadidos; los logs siguen usando los identificadores técnicos del actor.

## Pendiente por dependencia/definición

- Persistir atómicamente usuario/timestamp junto con la validación.
- Garantizar evidencia histórica después de deshabilitar o modificar la cuenta: requiere guardar el snapshot en la entidad/evento de validación.
- Consultar una validación guardada y mostrar exactamente el actor/timestamp originales.
- Reconciliar la misma evidencia entre Validación KPI, Aprobación KPI y reportes.
- Política de retención de auditoría.
- Zona horaria/formato regional de **presentación**. El backend conserva ya el instante absoluto UTC.
- Definir oficialmente si UI muestra nombre, correo/principal, identificador interno o combinación.

## Decisión de implementación

La Spec 07 habla de una validación ya registrada, pero el micro aún no dispone del contrato de persistencia KPI. Por ello el bloque `auditEvidence` se marca con `evidencePersistenceState=PREVALIDATION_EVIDENCE_ONLY_PENDING_PERSISTENCE`. No se presenta el timestamp de prevalidación como si fuera un evento guardado. Cuando exista persistencia, el mismo bloque puede almacenarse como snapshot inmutable y reutilizarse en Aprobación/Reportes.

## Base de datos

No se crean tablas/columnas y no se realizan INSERT/UPDATE/DELETE.
