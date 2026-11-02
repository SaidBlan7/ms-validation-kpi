# Cierre Aprobación KPI — Spec 01

Cambios incluidos:
- `ApprovalValueChangeView` ahora expone `effectiveValue` y `effectiveValueSource`.
- Regla canónica: `APPROVED -> proposedValue`; `PENDING/REJECTED/EXPIRED -> originalValue`.
- PostgreSQL y memory resuelven la regla server-side en las respuestas de Approval.
- No se sobrescribe `kpi.kpi_result.result_value`: la BD canónica declara que el resultado original permanece como evidencia y que el propuesto aprobado se hace efectivo en la lógica OEE.
- OpenAPI y estado de Spec 01 actualizados.

Validación estática ejecutada:
- domain + application: javac OK
- adapter-memory completo: javac OK
- JdbcKpiApprovalPersistenceAdapter: javac OK

Premisa ya acordada:
- las capabilities configuradas por el micro existen en la BD y están asociadas a los roles correspondientes.
