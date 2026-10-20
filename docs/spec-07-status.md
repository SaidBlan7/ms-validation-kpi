# Spec 07 - Visualización de fecha, hora y usuario de la validación

## Implementado

- Las prevalidaciones OEE general, tiempos y masas devuelven `auditEvidence`.
- Identificador inmutable externo: combinación `Entra tenant id (tid) + object id (oid)`.
- En PostgreSQL/Lakebase también se resuelve `app_security.user_identity.user_id` como `canonicalUserId`, y se usa como FK canónico en las validaciones OEE persistidas y en la auditoría de administración Spec 02.
- El snapshot canónico consulta `display_name`, `corporate_email` e `is_active` desde `app_security.user_identity`; nombre/principal del token se usan como fallback si el catálogo no trae esos datos.
- Se conservan `actorTenantId`, `actorObjectId`, `immutableActorId`, `canonicalUserId`, `displayNameSnapshot`, `principalNameSnapshot`, `accountActiveSnapshot` e `identitySource`.
- `eventTimestamp` se genera con el `Clock` del servicio como `Instant` y se serializa ISO-8601/UTC, preservando el instante absoluto.
- App Service obtiene claims opcionales `name`, `preferred_username`, `email` o `upn`.
- Azure Functions obtiene equivalentes desde `X-MS-CLIENT-PRINCIPAL`/`userDetails`.
- Modo mock conserva snapshots mediante `DEV_USER_DISPLAY_NAME` y `DEV_USER_EMAIL`; no existe `canonicalUserId` en el adapter local porque no hay `app_security` real.
- `GET /api/v1/oee-validation/audit/policy` expone fuente de identidad, precedencia de snapshots, timestamp y puntos pendientes.
- Los logs operativos no incluyen nombre/correo ni texto libre de justificación; siguen usando identificadores técnicos y correlation ID.

## Pendiente por dependencia/definición

- Persistir atómicamente el snapshot de actor/timestamp junto con la validación.
- Garantizar evidencia histórica después de deshabilitar o modificar la cuenta: el payload ya está preparado, pero debe guardarse en la entidad/evento de validación.
- Consultar una validación guardada y mostrar exactamente actor/timestamp originales.
- Reconciliar la misma evidencia entre Validación KPI, Aprobación KPI y reportes.
- Política de retención de auditoría.
- Zona horaria/formato regional de presentación. El backend conserva el instante absoluto UTC.
- Definir oficialmente si UI muestra nombre, correo/principal, identificador interno o una combinación.

## Decisión de implementación

La prevalidación sigue exponiendo evidencia previa al guardado, pero la creación de una validación OEE ya persiste `proposed_by_user_id` y `proposed_at` en `kpi.kpi_validation`, y la consulta de detalle recupera esa evidencia canónica. La administración Spec 02 también deja actor/correlación en `audit.change_log`.

## Base de datos

No se crean tablas/columnas y no se realizan INSERT/UPDATE/DELETE. La única ampliación es una lectura de `app_security.user_identity` para enlazar el actor de Entra con el `user_id` canónico existente.
