# KPI Manuales — Spec 02: Asignación de planta a superusuario

## Estado
CERRADA sobre la base canónica vigente.

## Modelo canónico
- Un usuario posee un solo `user_role_assignment` vigente. El rol default para esta spec es `SUPERUSUARIO` (`GBC_MANUAL_KPI_ROLE_CODE`).
- Ese rol debe tener la capability `KPI_MANUAL_ENTRY`.
- Las plantas del superusuario se representan con `app_security.user_plant_access`.
- Las líneas siguen siendo la intersección de `user_line_access`, línea→planta vigente, estado activo y feature `KPI_MANUAL_ENTRY_ENABLED`.

## Administración
- `GET /api/v1/manual-kpis/scope/admin/assignments`: historial de `user_plant_access` aplicable a superusuarios.
- `POST`: concede acceso a planta, previa validación del rol/capability vigentes.
- `DELETE`: cierra `user_plant_access.valid_to`; no elimina histórico.
- La acción administrativa continúa protegida por `KPI_MANUAL_SCOPE_ADMIN`.

La API no cambia. Se elimina la antigua suposición de crear un `user_role_assignment` por cada planta, incompatible con la unicidad canónica de rol vigente.
