# Spec 02 — Asignación de planta a validador

## Estado: CERRADA sobre la base canónica vigente

## Modelo canónico
- `app_security.user_role_assignment` mantiene **un solo rol vigente de aplicación por usuario**. Para Validación el default del micro es `validator` (`GBC_KPI_VALIDATOR_ROLE_CODE`).
- El rol vigente debe poseer la capability `KPI_OEE_VALIDATION` (configurable).
- El alcance multi-planta se representa con `app_security.user_plant_access`; ya no se crean roles vigentes repetidos por planta.
- Si `user_role_assignment.plant_id` viene informado, el rol sólo es efectivo en esa planta; para alcance multi-planta el rol vigente debe ser global (`plant_id IS NULL`).

## Reglas conservadas
- Máximo dos validadores efectivos por planta.
- Alta indefinida hasta revocación explícita.
- Se exige `externalApprovalReference`.
- La administración requiere `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` (configurable).
- Las altas concurrentes se serializan por planta.
- Las validaciones `PENDING` existentes no se alteran al revocar alcance.

## Persistencia
- Alta: crea `app_security.user_plant_access` después de verificar usuario activo + rol vigente `validator` + capability.
- Modificación: actualiza evidencia/motivo sobre el acceso vigente.
- Revocación: cierra `user_plant_access.valid_to`.
- El trigger canónico de `user_plant_access` conserva auditoría.

La API y los casos de uso no cambian; únicamente cambia la representación persistente del alcance para respetar `uq_user_role_assignment_current`.
