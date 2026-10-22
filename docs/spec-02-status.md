# Spec 02 — Asignación de planta a superusuario

## Estado: CERRADA

## Decisiones de cierre

- El permiso de Validación KPI OEE es específico por planta y se representa con `app_security.user_role_assignment`; no sustituye `user_plant_access` genérico.
- Máximo **dos** superusuarios efectivos por planta.
- La asignación es **indefinida hasta revocación explícita**. El API no permite programar `valid_to`; el campo se cierra al revocar.
- Un superusuario puede estar asignado a múltiples plantas sin límite global, siempre que conserve el acceso genérico vigente a cada planta.
- La administración la ejecuta un actor con la capability configurable `KPI_OEE_VALIDATOR_ASSIGNMENT_ADMIN` (o el código oficial configurado). Puede ser global o de la planta objetivo.
- El visto bueno de Dirección Global permanece fuera de GBC. Para dar de alta o modificar una asignación se exige `externalApprovalReference` como evidencia administrativa.
- Revocar una asignación corta inmediatamente nuevas aperturas, consultas y modificaciones para esa planta.
- Las validaciones `PENDING` creadas antes de la revocación **no se cancelan, no se reasignan y no se modifican**. Permanecen como evidencia histórica y continúan disponibles para el flujo independiente de Aprobación KPI.

## Implementado

- `GET /assignments/me`: devuelve sólo plantas KPI explícitamente asignadas y todavía autorizadas por acceso genérico.
- `GET /assignments?plantId=...`: historial administrativo por planta.
- `POST /assignments`: alta canónica con evidencia del visto bueno externo.
- `PATCH /assignments/{targetUserId}/plants/{plantId}`: actualización de evidencia/motivo administrativo sin alterar la vigencia indefinida.
- `POST /assignments/{targetUserId}/plants/{plantId}/revoke`: revocación inmediata mediante cierre de `valid_to`.
- El usuario objetivo debe estar activo y conservar `user_plant_access` vigente.
- El límite de dos se valida en el write path y también al utilizar el módulo.
- Las altas concurrentes se serializan por planta con `pg_advisory_xact_lock`, evitando que dos administradores ocupen simultáneamente el último cupo.
- Alta/modificación/revocación alimentan `audit.change_log` mediante el trigger canónico de `user_role_assignment`, con actor y correlation id de la transacción.
- Todas las operaciones funcionales vuelven a validar el entitlement en servidor, por lo que manipular `plantId`, reutilizar una URL o conservar una pantalla abierta no evita la revocación.

## Dependencia externa aceptada

- La aprobación de Dirección Global no se digitaliza en este microservicio; se conserva únicamente su referencia administrativa.
- Los códigos definitivos de roles/capabilities son datos de seguridad configurables y no cambian la lógica funcional cerrada de la Spec 02.

## Nota de integridad

La base canónica no impone por sí sola una restricción física de “máximo dos asignaciones vigentes”. Este microservicio garantiza la regla con lock transaccional + recheck. Un escritor externo directo a BD debe respetar la misma regla operacional.
