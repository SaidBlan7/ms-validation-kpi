# Aprobación KPI — Spec 02

Este paquete contiene únicamente archivos nuevos/modificados para cerrar la Spec 02 sobre el micro vigente.

Implementa asignación/revocación de jefaturas por planta usando `app_security.user_role_assignment`, con `user_plant_access` como prerrequisito y auditoría canónica. La autorización de decisión queda restringida a asignación específica de planta.

Configuración nueva:
- `GBC_KPI_APPROVER_ROLE_CODE` (default `KPI_APPROVER`)
- `GBC_KPI_APPROVAL_SCOPE_ADMIN_CAPABILITY_CODE` (default `KPI_APPROVAL_SCOPE_ADMIN`)

La capability de decisión continúa en `GBC_KPI_APPROVER_CAPABILITY_CODE` (default `KPI_APPROVAL`).
