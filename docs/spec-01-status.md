# Spec 01 — Estado de implementación (alineado a baseline física v1.2)

## Implementado
- Autenticación/identidad Entra en ambos runtimes.
- Resolución de identidad canónica v1.2 mediante `app_security.user_identity` (`entra_tenant_id` + `entra_object_id`).
- Validación de acceso vigente a planta mediante `app_security.user_plant_access` (`user_id`, `valid_from`, `valid_to`).
- Validación servidor de relación línea/planta vigente mediante `core.line_plant_assignment`; `core.line` ya no se asume dueño directo de `plant_id`.
- Zona horaria tomada de `core.plant.timezone_name`.
- Regla D-1 reforzada por Spec 03: día calendario inmediatamente anterior; fines de semana/festivos no se saltan. El contexto temporal publica además la siguiente medianoche local en la que la selección expira y el servidor revalida al guardar. La timezone/corte OEE sigue siendo provisional hasta confirmación funcional.
- Autorización funcional PostgreSQL/Lakebase desde `app_security.user_role_assignment` + `role` + `role_capability` + `capability`, con vigencias.
- Códigos semánticos provisionales centralizados: `KPI_OEE_VALIDATOR` y `KPI_OEE_VALIDATION`, sustituibles mediante `GBC_KPI_VALIDATOR_ROLE_CODE` / `GBC_KPI_VALIDATOR_CAPABILITY_CODE` cuando existan nombres oficiales.
- La asignación del validador es explícita por planta (`user_role_assignment.plant_id`); el acceso genérico `user_plant_access` sigue siendo obligatorio además del rol/capability.
- Máximo dos superusuarios: cada operación cuenta validadores activos para la planta y bloquea si existen más de dos. El DDL no impide administrativamente crear el tercero, por lo que el constraint/administración sigue fuera del micro.
- Acceso denegado antes de consultar datos de planta/línea cuando falta rol, asignación KPI o acceso general.
- Fuente OEE original read-only desde `kpi.kpi_definition` + `kpi.kpi_result`.
- `metricCode` se interpreta como `kpi.kpi_definition.kpi_code`; no se inventan códigos OEE.
- Sólo se toma un resultado canónico de nivel línea (sin `shift_id`, `product_id` ni `production_execution_id`) cuyo `period_start/period_end` coincide exactamente con la ventana D-1 local provisional.
- Si existen recálculos para la misma identidad/período se toma el más reciente por `calculated_at`, conservando `kpi_result_id` como `sourceRecordKey`.
- `GET /api/v1/oee-validation/oee/reference` expone `originalValue` + `sourceRecordKey` antes de proponer un cambio.
- `POST /api/v1/oee-validation/prevalidate` vuelve a resolver server-side el valor original; si no existe resultado canónico compatible, bloquea con `OEE_ORIGINAL_VALUE_NOT_FOUND`.
- Contrato semántico de workflow definido como `PENDING_APPROVAL`, `APPROVED`, `REJECTED`; una prevalidación válida declara `targetStatusIfPersisted=PENDING_APPROVAL` sin fingir que ya se guardó.
- `GET /api/v1/oee-validation/workflow/policy` expone estados, identidad fuente (`KPI_RESULT_ID`) y las garantías que siguen dependiendo de persistencia.
- Motivo `SYSTEM`: incidente obligatorio.
- Motivo `OPERATIVE`: causa y plan de acción obligatorios.
- Correlation ID, health check y logging operativo.
- Endpoint de capacidades distingue funcionalidad real de integraciones pendientes.

## Pendiente
- Persistir la propuesta y dejarla en `PENDING_APPROVAL`: el DDL físico v1.2 contiene resultados KPI, pero no una entidad/contrato de Validación KPI/Aprobación.
- Garantizar una sola modificación por dato: ahora existe identidad técnica del origen (`kpi_result_id`), pero falta persistencia/constraint del workflow de validación.
- Concurrencia entre dos superusuarios sobre el mismo dato.
- Confirmar los `kpi_code` oficiales para OEE 1, OEE 2 y OEE completo; el micro no los hardcodea.
- Cargar/administrar en datos maestros el role/capability semántico (o sustituir los códigos por los oficiales). No requiere cambios de código porque ambos son configurables.
- Interpretación exacta de “máximo 24 horas”, corte oficial OEE y timezone autoritativa. Mientras siga abierta, la lectura canónica exige que el período coincida exactamente con el D-1 calendario local provisional.
- Reacción si aparece un nuevo `kpi_result`/recalculo después de creada la validación y antes de aprobarse.
- Aprobación/rechazo y aplicación definitiva; depende del módulo/contrato Aprobación KPI.

## Alcance de esta migración
Esta actualización adapta **Spec 01** a la baseline física v1.2. Los adapters PostgreSQL específicos de Specs 04 y 05 se migrarán cuando revisemos esas especificaciones; no debe interpretarse este paso como compatibilidad v1.2 completa de todo el micro.
