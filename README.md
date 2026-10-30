# KPI Manuales — cierre Spec 05

Este paquete contiene únicamente archivos nuevos/modificados para cerrar la Spec 05 sobre el micro recibido.

Cambios incluidos:
- `docs/manual-kpi-spec-05-status.md`: actualiza el estado a cerrado y documenta la persistencia real de masas elaboradas/entregadas en `kpi.kpi_result` con `source_mode=MANUAL` y `source_snapshot`.

No se modificó código Java porque el ZIP recibido ya contiene la implementación funcional completa de Spec 05, incluida la serialización/deserialización PostgreSQL de `elaboratedMass` y `deliveredMass`, validaciones y contratos HTTP.
