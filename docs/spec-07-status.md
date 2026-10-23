# Spec 07 - Visualización de fecha, hora y usuario de la validación

## Cerrada

- Cada validación persiste `proposed_by_user_id` y `proposed_at` en `kpi.kpi_validation`.
- La identidad inmutable del actor se reconcilia mediante `app_security.user_identity.user_id` y los identificadores Entra `tenantId/objectId`.
- El nombre visible y correo se obtienen del perfil canónico actual; no se usan como identidad histórica.
- Una cuenta deshabilitada después de validar conserva la evidencia porque la validación mantiene la FK al usuario canónico.
- El instante almacenado se conserva como `TIMESTAMPTZ`/`Instant` absoluto. Para presentación se usa `core.plant.timezone_name`, la misma autoridad temporal cerrada en Spec 03.
- La UI/reporte debe mostrar, como combinación estándar, nombre visible cuando exista, correo corporativo cuando exista y el identificador inmutable/canónico del usuario.
- El micro no elimina evidencia de validación ni auditoría; la retención sigue el ciclo de vida de los registros canónicos de `kpi.kpi_validation` y `audit.change_log`.
- Aprobación KPI y reportes deben reconciliar actor y fecha/hora usando `kpi_validation_id`, `proposed_by_user_id` y `proposed_at`, evitando reconstruir esos datos desde la interfaz.
- OEE, tiempos y masas comparten la misma evidencia de auditoría persistida.
- `GET /api/v1/oee-validation/audit/policy` publica esta política.

## Decisiones de cierre

1. **Zona de presentación:** zona IANA de la planta (`core.plant.timezone_name`). El instante absoluto nunca se modifica.
2. **Identidad mostrada:** nombre + correo cuando estén disponibles, acompañados por identificador canónico/inmutable.
3. **Cuenta deshabilitada:** no invalida ni elimina evidencia histórica.
4. **Cambio posterior de nombre/correo:** la identidad histórica sigue siendo el `user_id`/Entra ID; el nombre/correo mostrado puede corresponder al perfil actual porque la base canónica no guarda snapshot textual. No se agrega esquema nuevo.
5. **Retención:** este micro no ejecuta borrado de validaciones/auditoría. La política corporativa de purga física, si existiera, corresponde al gobierno de datos y no modifica el contrato funcional de Spec 07.
