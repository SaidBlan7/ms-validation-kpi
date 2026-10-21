# Spec 07 - Visualización de fecha, hora y usuario de la validación

## Implementado

- Las prevalidaciones OEE, tiempos y masas exponen `auditEvidence` con actor y `eventTimestamp` como `Instant` UTC.
- La identidad inmutable de la petición usa `tenantId/objectId` de Entra.
- PostgreSQL resuelve `app_security.user_identity.user_id` y exige identidad canónica activa para persistir.
- OEE general y tiempos persisten `proposed_by_user_id` y `proposed_at` en `kpi.kpi_validation`.
- Al consultar una validación OEE o de tiempos, el adapter une `proposed_by_user_id` con `app_security.user_identity` y devuelve `canonicalUserId`, `entra_tenant_id`, `entra_object_id`, identificador inmutable, nombre/correo actuales, estado de cuenta y el `proposed_at` persistido.
- Una cuenta deshabilitada después de validar no elimina la evidencia: la FK sigue apuntando al usuario y los ids de Entra permanecen consultables.
- `GET /api/v1/oee-validation/audit/policy` publica la semántica de identidad/timestamp.
- App Service y Azure Functions extraen claims de identidad sin incluir nombre/correo en logs operativos.
- No se agregan tablas ni columnas.

## Límite del modelo canónico actual

La base conserva de forma histórica el `user_id` proponente y el instante `proposed_at`, y `user_identity` conserva los identificadores Entra. Sin embargo, no existe un snapshot histórico del `display_name`/`corporate_email` dentro de la validación. Si nombre o correo cambian, una consulta posterior mostrará el perfil actual asociado al mismo identificador inmutable. Preservar el nombre/correo exactos del momento requeriría una futura decisión/modelo de auditoría; no se inventa en este micro.

## Pendiente / dependencia externa

- Reconciliar la misma evidencia en Aprobación KPI y reportes.
- Política de retención de auditoría.
- Zona horaria/formato regional de presentación; backend preserva el instante absoluto.
- Confirmar qué combinación debe mostrar UI: nombre, correo, id interno e identificador Entra.
- Spec 05 sólo expone evidencia de prevalidación mientras su persistencia siga bloqueada por el modelo canónico.
