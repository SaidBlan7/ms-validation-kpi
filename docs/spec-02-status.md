# Spec 02 — Asignación de planta a superusuario

## Implementado

- Permiso KPI OEE específico por planta mediante `app_security.user_role_assignment`; no se confunde con `user_plant_access` genérico.
- El usuario objetivo debe estar activo y conservar acceso genérico vigente a la planta.
- Rol/capability del validador configurables: `KPI_OEE_VALIDATOR` / `KPI_OEE_VALIDATION`.
- Administración protegida por capability configurable independiente: `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` (`GBC_KPI_VALIDATOR_ADMIN_CAPABILITY_CODE`). Puede provenir de un rol global o de planta.
- `GET /assignments/me`: visibilidad únicamente de plantas KPI explícitamente asignadas.
- `GET /assignments?plantId=...`: consulta administrativa del historial de asignaciones de la planta.
- `POST /assignments`: alta canónica. Requiere `externalApprovalReference` porque el visto bueno de Dirección Global sigue ocurriendo fuera de GBC.
- `PATCH /assignments/{targetUserId}/plants/{plantId}`: modificación de vigencia/evidencia administrativa.
- `POST /assignments/{targetUserId}/plants/{plantId}/revoke`: revocación inmediata cerrando `valid_to`; exige motivo.
- Máximo de dos validadores efectivos por planta aplicado en el write path y nuevamente al usar el módulo.
- Concurrencia de administradores serializada por planta con `pg_advisory_xact_lock`; el tercer alta concurrente se rechaza con conflicto.
- Alta/modificación/revocación alimentan `audit.change_log` mediante el trigger canónico de `user_role_assignment`, fijando `app.user_id`, `app.source_system` y `app.correlation_id` en la transacción.
- `valid_to = NULL` significa vigencia indefinida; una fecha informada debe ser futura.
- Un superusuario puede estar asignado a múltiples plantas; no existe límite global en la Spec.
- Revocar la asignación bloquea nuevas aperturas/consultas/modificaciones porque toda operación funcional revalida el entitlement en servidor, incluyendo la creación y consulta de propuestas de tiempos de Spec 04.
- Spec 03 reutiliza la misma autorización en `temporal-context` y `eligibility`; una URL o pantalla conservada no permite consultar ni validar una planta después de revocar la asignación.

## Dependencias / decisiones abiertas conservadas

- La aprobación de Dirección Global no se digitaliza: el API sólo exige y conserva una referencia declarada por el administrador.
- El comportamiento de validaciones `PENDING` existentes al revocar un superusuario sigue sin definición funcional. Esta implementación **no las cancela ni las modifica**; únicamente revoca acceso futuro.
- El código oficial de la capability administrativa debe ser sembrado/confirmado por Seguridad/Data; el default es semántico y configurable.
- La base no impone unicidad física de una asignación vigente por `(user_id, role_id, plant_id)`. Este micro evita duplicados con lock + recheck, pero un escritor externo podría romper la regla.
