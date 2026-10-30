# KPI Manuales — Spec 02: Asignación de planta a superusuario

## Estado
CERRADA del lado del micro sobre el modelo canónico existente, sin cambios de esquema.

## Decisiones cerradas
- Un superusuario puede tener múltiples plantas. No se impone máximo porque `app_security.user_plant_access` no define uno y la especificación no aporta una cifra contractual.
- La autorización se revalida en servidor en cada lectura/escritura. La capability de entrada requiere una `user_role_assignment` vigente y explícitamente asociada a la planta (`plant_id` no global). Si se retira una asignación durante una sesión activa, deja de ser efectiva en la siguiente petición sin esperar logout/token refresh.
- Las líneas visibles son la intersección de: acceso vigente a planta, acceso vigente a línea, asignación vigente línea→planta, línea/planta activas y `KPI_MANUAL_ENTRY_ENABLED` efectivo para la línea.
- La zona horaria se toma de `core.plant.timezone_name`; no se copia ni se configura por usuario.
- Retirar acceso cierra `valid_to`; nunca borra el histórico. Reasignar crea una nueva fila histórica.
- No se creó un flujo de aprobación independiente porque la base canónica no contiene una entidad de aprobación para `user_plant_access`. La acción administrativa se protege mediante capability.

## Implementación
- `GET /api/v1/manual-kpis/scope/me`: plantas y líneas efectivas del actor.
- `GET /api/v1/manual-kpis/scope/policy`: decisiones operativas aplicadas por el micro.
- `GET /api/v1/manual-kpis/scope/admin/assignments?plantId=`: historial de asignaciones de planta.
- `POST /api/v1/manual-kpis/scope/admin/assignments`: alta de alcance específico de KPI Manuales para una planta; no crea `user_plant_access` general.
- `DELETE /api/v1/manual-kpis/scope/admin/assignments`: retiro temporal/histórico mediante `valid_to`.
- Equivalentes Azure Functions incluidos.

## Persistencia canónica utilizada
- `app_security.user_role_assignment`: asignación histórica específica de KPI Manuales por rol + planta.
- `app_security.user_plant_access`: prerrequisito de acceso general a la planta; no se modifica desde este módulo.
- `app_security.user_line_access`: mínimo privilegio por línea.
- `app_security.user_identity`: identidad y estado del usuario.
- `app_security.role`, `role_capability`, `user_role_assignment`, `capability`: autorización del administrador.
- `core.plant`, `core.line`, `core.line_plant_assignment`: catálogo/contexto y zona horaria.
- `configuration.configuration_definition` + `configuration_assignment`: activación de KPI Manuales por línea/planta/default.
- `audit.change_log`: el trigger canónico de `user_role_assignment` registra altas/bajas/cambios con el contexto `app.user_id`, `app.source_system` y `app.correlation_id` establecido por el adapter.

## Configuración requerida
- `GBC_MANUAL_KPI_FEATURE_CODE` (default `KPI_MANUAL_ENTRY_ENABLED`).
- `GBC_MANUAL_KPI_ROLE_CODE` (default `KPI_MANUAL_SUPERUSER`).
- `GBC_MANUAL_KPI_SCOPE_ADMIN_CAPABILITY_CODE` (default `KPI_MANUAL_SCOPE_ADMIN`).

## Datos maestros requeridos para operación
No se requiere cambio de DDL para esta spec. Para habilitar administración en PostgreSQL, Data/Seguridad debe confirmar/cargar en datos maestros:
1. Rol de módulo `KPI_MANUAL_SUPERUSER` (o el código acordado) asociado a la capability `KPI_MANUAL_ENTRY`.
2. `app_security.capability.capability_code = KPI_MANUAL_SCOPE_ADMIN` (o el código acordado).
3. La asociación de la capability administrativa al rol que utilizará el proveedor/administrador mediante `role_capability`.
4. La asignación vigente de dicho rol administrativo a los actores administradores mediante `user_role_assignment`.
5. El superusuario objetivo debe contar previamente con `user_plant_access` general a la planta; este micro no lo concede para evitar ampliar permisos de otros módulos.

La pregunta abierta de visto bueno no bloquea esta spec: mientras negocio no defina un flujo formal adicional, el control vigente es la capability administrativa y la trazabilidad canónica. Si posteriormente se define aprobación formal, sería una funcionalidad adicional y no cambia el contrato actual de alcance de KPI Manuales.

## Relación con Spec 01
La captura de KPI Manuales ya cuenta con persistencia PostgreSQL canónica mediante `kpi.kpi_result`, conforme a la decisión de Data documentada en la Spec 01. La selección/autorización de planta y línea de esta Spec 02 continúa resuelta contra PostgreSQL canónico.

