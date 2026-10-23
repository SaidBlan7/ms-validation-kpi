# KPI Manuales — Especificación 01 — Estado de implementación

## Alcance cerrado en el microservicio

Se implementa un flujo independiente de Validación KPI/Aprobación KPI para captura manual diaria de OEE y desperdicios por `plantId + lineId + businessDate`.

Reglas implementadas:

- Feature flag canónico por planta/línea mediante `configuration.configuration_definition` + `configuration.configuration_assignment` (`KPI_MANUAL_ENTRY_ENABLED` por defecto).
- Autenticación Entra y autorización canónica por capability (`KPI_MANUAL_ENTRY` por defecto), además de `user_plant_access` y `user_line_access` vigentes.
- Validación de pertenencia línea-planta y uso de `core.plant.timezone_name` como zona horaria autoritativa.
- Ventana histórica de 96 horas calculada desde el inicio local de `businessDate` contra el instante actual.
- Primera captura y posteriores modificaciones modeladas como un único registro diario versionado.
- Ventana de edición de 8 horas absolutas desde el primer guardado; no se reinicia al editar y no cambia por medianoche/DST.
- Al vencer las 8 horas, el registro queda bloqueado para edición.
- Desactivar el feature bloquea nuevas altas/ediciones, pero no elimina ni oculta históricos existentes.
- Precedencia resuelta con criterio operacional: **AUTOMATIC_WINS**. Si posteriormente existe dato automático para el mismo alcance/fecha, el automático debe ser el valor efectivo de reporte y el manual debe conservarse como evidencia histórica.
- OEE manual acepta tiempos por `stopClassificationId` y rendimiento por `metricDefinitionId + value + uomId`.
- Los tiempos OEE son no negativos, sin duplicados por clasificación y con máximo total diario de 86,400 segundos.
- Desperdicio manual acepta kg por clasificación/subclasificación y toneladas producidas; no se permiten negativos ni duplicados de clasificación/subclasificación.
- Referencias se validan contra catálogos canónicos: `stops.stop_classification`, `kpi.metric_definition`, `core.unit_of_measure`, `waste.waste_classification` y `waste.waste_subclassification`.
- El contrato de persistencia conserva actor canónico, creación, actualización, `editableUntil`, versión, correlación y eventos de auditoría CREATE/UPDATE.
- Adapter `memory` queda funcional para pruebas locales end-to-end.
- App Service y Azure Functions exponen política, consulta y guardado del módulo.

## Endpoints

- `GET /api/v1/manual-kpis/policy`
- `GET /api/v1/manual-kpis?plantId={id}&lineId={id}&businessDate=YYYY-MM-DD`
- `PUT /api/v1/manual-kpis`

Azure Functions usa las rutas equivalentes bajo `v1/manual-kpis`.

## Configuración requerida existente en la base canónica

No requiere cambiar el modelo para feature/security, pero sí requiere datos de configuración vigentes:

- `configuration.configuration_definition.configuration_code = KPI_MANUAL_ENTRY_ENABLED`, tipo booleano, habilitable al menos en scope PLANT/LINE.
- `configuration.configuration_assignment` para las plantas/líneas que tendrán el módulo activo.
- `app_security.capability.capability_code = KPI_MANUAL_ENTRY`.
- Rol(es) correspondientes ligados a la capability mediante `app_security.role_capability`.
- `app_security.user_role_assignment`, `user_plant_access` y `user_line_access` vigentes para los usuarios autorizados.
- Catálogos y UOM usados por la captura deben existir y estar activos.

Los códigos pueden sobrescribirse con `GBC_MANUAL_KPI_FEATURE_CODE` y `GBC_MANUAL_KPI_CAPABILITY_CODE`.

## Bloqueo real de base canónica — requiere Data

La base canónica entregada **no contiene una entidad de negocio para almacenar KPI Manuales**. No existe una tabla que pueda conservar correctamente, sin cambiar semántica de otros dominios:

- cabecera diaria manual por planta/línea/fecha;
- sección OEE de tiempos por clasificación;
- sección OEE de métricas de rendimiento;
- sección de desperdicio por clasificación/subclasificación;
- toneladas producidas;
- primera creación, versión/revisiones y ventana de edición de 8 horas;
- trazabilidad del valor manual aun cuando posteriormente llegue el automático.

Por este motivo el adapter PostgreSQL/Lakebase **no escribe** KPI Manuales y devuelve `503 Service Unavailable` en guardado. Esto es intencional: no se reutilizan `kpi.kpi_result`, `configuration.configuration_assignment`, `integration.message`, `audit.change_log`, `waste.waste_event` ni otras tablas como almacenamiento genérico porque alteraría su semántica canónica.

### Única solicitud necesaria a Data para cerrar persistencia al 100%

Data debe incorporar al modelo canónico una entidad/estructura oficial de persistencia para KPI Manuales que cubra los puntos anteriores y entregue su contrato final (tablas/columnas/FK/índices/auditoría). El micro ya queda aislado mediante `ManualKpiPersistencePort`; al recibir ese contrato solo se sustituye el adapter PostgreSQL sin cambiar API ni reglas de aplicación.

## Dependencia externa para propagación a reportes

La spec exige consumo en OEE 1, OEE 2, OEE completo, indicadores y reportes de desperdicio. Esos consumidores no están dentro de este microservicio. El criterio ya queda cerrado como `AUTOMATIC_WINS_MANUAL_RETAINED_FOR_TRACEABILITY`, pero el pipeline/reporting debe leer la entidad canónica de KPI Manuales que Data incorpore y aplicar esa precedencia.

Hasta que exista dicha entidad y los consumidores la integren, la propagación productiva a reportes no puede quedar implementada desde este repositorio.
