# Approval KPI — Spec 04: Aprobar/Rechazar OEE

Estado de implementación en este micro: decisión sobre `kpi.kpi_validation` con bloqueo de fila PostgreSQL, comprobación transaccional de permisos, reloj del servidor, decisión final inmutable y reintento idéntico del mismo aprobador. `APPROVED` expone `proposedValue` como `effectiveValue`; rechazo/expiración conservan `originalValue`, sin sobrescribir `kpi.kpi_result` (evidencia canónica). El detalle muestra `kpiValidationDetailId` para reconciliación y auditoría.

**Fuera de este micro:** invalidación de cachés de otros sistemas, recálculo o sincronización de informes externos, así como compensación ante fallos de esos consumidores. No se declara que esos componentes se ejecuten desde Approval. No se implementa reversión administrativa porque la especificación no define ese flujo.

**Verificación pendiente:** pruebas de integración PostgreSQL con concurrencia real, 23:59:59 de la planta, retries concurrentes y validación de consumo en el motor/reporting. La existencia de métodos no sustituye esas pruebas.
