# Spec 02 — Asignación de planta a superusuario (alineado a baseline física v1.2)

## Implementado

- Autenticación del actor mediante identidad Entra (`tid` + `oid`).
- Permiso específico de Validación KPI OEE separado del acceso genérico a planta.
- En PostgreSQL/Lakebase la asignación canónica se resuelve desde:
  - `app_security.user_identity`,
  - `app_security.user_role_assignment`,
  - `app_security.role`,
  - `app_security.role_capability`,
  - `app_security.capability`,
  - y adicionalmente exige `app_security.user_plant_access` vigente.
- Códigos semánticos configurables mientras no existan códigos oficiales:
  - role: `KPI_OEE_VALIDATOR` (`GBC_KPI_VALIDATOR_ROLE_CODE`),
  - capability: `KPI_OEE_VALIDATION` (`GBC_KPI_VALIDATOR_CAPABILITY_CODE`).
- La asignación KPI debe ser específica de planta (`user_role_assignment.plant_id`). Una autorización global sin esa asignación no habilita Validación KPI OEE.
- La visibilidad y todas las operaciones plant-scoped validan la asignación en servidor antes de devolver contexto operativo.
- `GET /api/v1/oee-validation/assignments/me` sólo devuelve plantas con asignación KPI vigente y acceso genérico vigente; incluye `effectiveValidatorCount`, `maxValidatorsPerPlant`, `remainingSlots` y `overLimit`.
- Máximo de dos validadores efectivos por planta: el conteo requiere usuario activo + rol/capability KPI vigentes + `user_plant_access` vigente. Si el dato canónico queda con más de dos, el backend bloquea el uso de esa planta con `MAX_KPI_VALIDATORS_PER_PLANT_EXCEEDED`.
- Revocación efectiva mediante cierre de `user_role_assignment.valid_to`; la siguiente solicitud deja de estar autorizada sin requerir redeploy.
- `valid_to = NULL` se interpreta como vigencia abierta; si se informa, la asignación expira automáticamente en ese instante.
- Se permiten múltiples plantas por usuario sin límite global mientras cada asignación sea explícita por planta.
- `GET /api/v1/oee-validation/assignments/policy` publica la política efectiva y deja explícitos los límites de ownership.
- En `memory` se conserva `GBC_KPI_VALIDATOR_ASSIGNMENTS` únicamente como fixture local; no es el mecanismo productivo v1.2.

## Pendiente / fuera del write path de este micro

- Alta/modificación/revocación administrativa desde UI/API de este micro: el flujo de visto bueno y la ejecución administrativa siguen siendo externos según la Spec 02.
- Prevención transaccional de una tercera asignación en el momento del write administrativo. El backend funcional ya detecta y bloquea una planta que termine con más de dos validadores efectivos.
- Auditoría completa de revocación: `user_role_assignment` conserva `assigned_by_user_id`, `reason`, `valid_from` y `valid_to`, pero el DDL v1.2 no contiene `revoked_by_user_id`/`revocation_reason` dedicados ni trigger de auditoría para esa tabla.
- Tratamiento de validaciones pendientes al revocar/sustituir un validador: la especificación lo deja por definir y además depende de que exista persistencia de Validación KPI.
- Digitalización del visto bueno de Dirección Global: la especificación confirma que actualmente es un proceso externo.

## Propuesta de dato opcional para cerrar auditoría administrativa

Si Data acepta ampliar `app_security.user_role_assignment`, los campos mínimos sugeridos son `revoked_by_user_id` y `revocation_reason`. No son necesarios para autorizar ni para aplicar el máximo de dos; sólo completan la evidencia administrativa de la revocación.
