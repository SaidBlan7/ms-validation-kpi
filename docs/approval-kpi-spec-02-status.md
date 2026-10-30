# Aprobación KPI — Spec 02: Asignación de planta a jefatura

## Estado
Cerrada del lado de `ms-kpi-validation` con el modelo canónico actual.

## Modelo canónico utilizado
- La asignación específica del módulo se representa con `app_security.user_role_assignment` scoped por `plant_id`.
- El rol configurado por defecto es `KPI_APPROVER` y debe tener la capability `KPI_APPROVAL`.
- `app_security.user_plant_access` permanece como prerrequisito de acceso general; Aprobación KPI no crea ni elimina ese acceso.
- Altas/revocaciones de `user_role_assignment` quedan auditadas por el trigger canónico existente.

## Reglas cerradas
- No se impone máximo artificial de aprobadores por planta; el esquema canónico permite múltiples asignaciones vigentes.
- La autorización de decisión exige una asignación de Aprobación vigente **específica para la planta**; un role assignment global no habilita decisiones de este módulo.
- Revocar una asignación surte efecto en la siguiente petición porque cada consulta/decisión vuelve a autorizar server-side.
- Las validaciones pendientes pertenecen a la planta, no al aprobador. Si cambia la jefatura, permanecen pendientes y pasan a ser visibles/decidibles por los aprobadores actualmente vigentes.
- Una suplencia/temporal se representa como otra asignación vigente con su propia `valid_from/valid_to`; no se crea una entidad especial.
- El visto bueno de Dirección Global continúa externo a GBC, tal como indica la especificación; el micro no inventa un workflow adicional.

## API
- `GET /api/v1/kpi-approval/scope/policy`
- `GET /api/v1/kpi-approval/scope/admin/assignments?plantId=...`
- `POST /api/v1/kpi-approval/scope/admin/assignments`
- `DELETE /api/v1/kpi-approval/scope/admin/assignments`

La administración requiere una capability separada, configurable mediante `GBC_KPI_APPROVAL_SCOPE_ADMIN_CAPABILITY_CODE` (default `KPI_APPROVAL_SCOPE_ADMIN`). El rol asignado se configura con `GBC_KPI_APPROVER_ROLE_CODE` (default `KPI_APPROVER`).

## Seguridad
La operación de aprobación sigue verificando además feature activation, `user_plant_access`, pertenencia línea/planta cuando aplique y capability de Aprobación. La revocación no altera los históricos ni el actor de decisiones anteriores.
