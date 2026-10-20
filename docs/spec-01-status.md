# Spec 01 — Estado de implementación contra base canónica actual

## Implementado en esta adaptación
- Autenticación/identidad Entra y resolución canónica mediante `app_security.user_identity`.
- Autorización por planta y validación servidor de relación línea/planta.
- Rol/capability KPI y asignación explícita por planta; se bloquea el uso si existen más de dos validadores efectivos.
- Activación del módulo por planta desde `configuration.configuration_definition` + `configuration.configuration_assignment` usando `KPI_OEE_VALIDATION_ENABLED` (código sustituible por `GBC_KPI_VALIDATION_FEATURE_CODE`).
- Regla D-1 recalculada en servidor usando `core.plant.timezone_name`.
- Fuente original OEE desde `kpi.kpi_definition` + `kpi.kpi_result`, conservando `kpi_result_id` como identidad del dato.
- Visualización/contrato de valor original y propuesto mediante reference/prevalidate/detail.
- Motivo SYSTEM con incidente obligatorio y OPERATIVE con causa + plan de acción obligatorios.
- Persistencia transaccional de propuestas en `kpi.kpi_validation` + `kpi.kpi_validation_detail` con estado canónico `PENDING`; Spec 04 reutiliza la misma cabecera y persiste `stop_classification_id` para correcciones de tiempos.
- Persistencia del usuario proponente y timestamp canónicos.
- Contexto de auditoría (`app.user_id`, `app.source_system`, `app.correlation_id`) establecido en la transacción para alimentar los triggers canónicos de auditoría.
- Una sola oportunidad por `kpi_result_id` dentro de este micro: pre-check + `pg_advisory_xact_lock` + re-check transaccional.
- Consulta de detalle de una validación persistida con actor, timestamp, motivo, original y propuesto.
- Endpoint de creación y endpoint de detalle tanto en App Service como en Azure Functions.
- Rechazo de propuesta igual al valor original antes de llegar al CHECK físico de `kpi_validation_detail`.

## Dependencias / definiciones que siguen fuera de Spec 01 o abiertas en la especificación
- Aprobación/rechazo y aplicación definitiva del valor propuesto pertenecen al módulo de Aprobación KPI.
- Validación en línea del número de incidente contra GB Support no está definida por Spec 01.
- Los códigos oficiales concretos de OEE 1, OEE 2 y OEE completo deben existir como `kpi.kpi_definition.kpi_code`; el micro no inventa códigos.
- Spec 03 confirma **D-1 calendario** e incluye fines de semana/festivos sin saltarlos. Continúan abiertos únicamente el corte oficial del día OEE, la interpretación de “24 horas” si el corte no es medianoche y la autoridad final de timezone.
- La política cuando el `kpi_result` fuente cambia/recalcula después de crear la propuesta y antes de aprobarla sigue abierta.

## Observaciones de base de datos
- Debe existir/sembrarse la definición booleana `KPI_OEE_VALIDATION_ENABLED` y una asignación efectiva por planta (o default global); sin ese dato maestro el micro bloquea el módulo de forma segura.
- `kpi.kpi_validation_detail.kpi_result_id` no tiene UNIQUE. El micro serializa concurrentes con advisory lock, pero se recomienda una restricción/índice UNIQUE físico si la regla “una sola validación por dato” debe quedar garantizada contra cualquier escritor externo.
- La base comenta que `expires_at` es 23:59:59 del `oee_date`, pero Spec 01 sólo permite proponer D-1; esa combinación dejaría `expires_at < proposed_at` y chocaría con `ck_kpi_validation_expiration`. Esta adaptación usa el fin del día local actual como deadline provisional hasta que se corrija/aclare esa semántica.
- El máximo de dos superusuarios se valida al usar el módulo y Spec 02 ahora también lo previene en el write path de este micro con lock transaccional por planta. La base por sí sola todavía no impone esa cardinalidad contra escritores externos.
## Alineación posterior

- Spec 05 now persists mass corrections through the same PENDING approval workflow without applying the proposed value immediately.
