# Aprobación KPI — Spec 02: Asignación de planta a jefatura

## Estado
CERRADA sobre la base canónica vigente.

## Modelo canónico
- `app_security.user_role_assignment` representa el único rol vigente de aplicación del usuario.
- El rol default del micro para jefatura es `productionSupervisor` (`GBC_KPI_APPROVER_ROLE_CODE`) y debe poseer `KPI_APPROVAL`.
- El alcance por planta se representa con `app_security.user_plant_access`; no se crean roles vigentes por planta.
- Si el rol vigente tiene `plant_id`, sólo aplica a esa planta; para múltiples plantas debe ser global.

## Reglas
- El máximo/mínimo de aprobadores por planta continúa sin definición funcional; el micro no impone un límite artificial mientras permanezca abierto en la especificación.
- Revocar cierra `user_plant_access.valid_to` y surte efecto en la siguiente petición.
- Las validaciones pendientes pertenecen a la planta y no se alteran por el cambio de jefatura.
- La administración requiere `KPI_APPROVAL_SCOPE_ADMIN` **y** `user_plant_access` vigente del actor administrador para la planta administrada, aplicando mínimo privilegio en servidor.
- El visto bueno externo permanece fuera de GBC.

La API permanece igual. El scope se persiste en `user_plant_access` y tanto la operación funcional como la administración se restringen server-side por la planta correspondiente.
