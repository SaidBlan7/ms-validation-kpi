# KPI Manuales — cierre Spec 01 con persistencia en kpi.kpi_result

Este paquete contiene únicamente archivos nuevos/modificados.

## Cambio principal
- PostgreSQL/Lakebase usa `JdbcManualKpiPersistenceAdapter` sobre `kpi.kpi_result`.
- `source_mode = MANUAL`.
- Una fila append-only por versión del registro.
- `source_snapshot` conserva OEE, masas, desperdicio, toneladas, timezone snapshot, versión y auditoría.
- `expectedVersion` sigue protegiendo concurrencia; además se serializa el business key en PostgreSQL.
- Los contratos HTTP existentes no cambian.

## Configuración necesaria
`GBC_MANUAL_KPI_CAPTURE_KPI_CODE` debe configurarse con el `kpi.kpi_definition.kpi_code` activo aprobado por Data para representar la captura manual compuesta.

No se incluye DDL ni se inventa un código de KPI; el dato maestro debe existir en la base canónica.
