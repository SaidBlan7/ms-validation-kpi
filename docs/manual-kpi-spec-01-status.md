# KPI Manuales — Especificación 01 — Estado de implementación

## Alcance cerrado en el microservicio

Se implementa un flujo independiente de Validación KPI/Aprobación KPI para captura manual diaria de OEE y desperdicios por `plantId + lineId + businessDate`.

Reglas implementadas:

- Feature flag canónico por planta/línea mediante `configuration.configuration_definition` + `configuration.configuration_assignment` (`KPI_MANUAL_ENTRY_ENABLED` por defecto).
- Autenticación Entra y autorización canónica por capability (`KPI_MANUAL_ENTRY` por defecto), además de `user_plant_access` y `user_line_access` vigentes.
- Validación de pertenencia línea-planta y uso de `core.plant.timezone_name` como zona horaria autoritativa.
- Ventana histórica de 96 horas exactas e inclusivas calculada desde el inicio local de `businessDate` contra el instante actual; esta regla aplica a nuevas capturas/selección.
- Primera captura y posteriores modificaciones modeladas como un único registro diario versionado.
- Ventana de edición de 8 horas absolutas desde el primer guardado; no se reinicia al editar, no cambia por medianoche/DST y puede continuar aunque `businessDate` cruce fuera de las 96 horas después de una creación válida.
- Al vencer las 8 horas, el registro queda bloqueado para edición.
- Desactivar el feature bloquea nuevas altas/ediciones, pero no elimina ni oculta históricos existentes.
- Precedencia resuelta con criterio operacional: **AUTOMATIC_WINS**. Si posteriormente existe dato automático para el mismo alcance/fecha, el automático debe ser el valor efectivo de reporte y el manual debe conservarse como evidencia histórica.
- OEE manual acepta tiempos por `stopClassificationId + hours` (horas decimales) y rendimiento explícito mediante `ELABORATED_MASS` + `DELIVERED_MASS` (`oee.massPerformance.elaboratedMass/deliveredMass`).
- Los tiempos OEE son no negativos, sin duplicados por clasificación y con máximo total diario exacto de 24 horas; la suma se valida con `BigDecimal`.
- Las actualizaciones usan `expectedVersion` como optimistic lock para evitar pérdida silenciosa de cambios concurrentes.
- Desperdicio manual acepta kg por subclasificación canónica y toneladas producidas por separado; no se permiten negativos ni subclasificaciones duplicadas. Desde Spec 06, cada kg requiere `wasteClassificationId + wasteSubclassificationId`.
- Referencias se validan contra catálogos canónicos cuando aplican: `stops.stop_classification`, `waste.waste_classification` y `waste.waste_subclassification`. El bloque manual de masas usa variables semánticas explícitas y no obliga al cliente a escoger IDs de métrica/UOM.
- El contrato de persistencia conserva actor canónico, creación, actualización, `editableUntil`, versión, correlación y eventos de auditoría CREATE/UPDATE.
- Adapter `memory` queda funcional para pruebas locales end-to-end.
- App Service y Azure Functions exponen política, evaluación de selección de fecha, consulta y guardado del módulo.

## Endpoints

- `GET /api/v1/manual-kpis/policy`
- `GET /api/v1/manual-kpis/date-selection?plantId={id}&lineId={id}&businessDate=YYYY-MM-DD`
- `GET /api/v1/manual-kpis/stop-classifications?plantId={id}&lineId={id}`
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

## Persistencia canónica confirmada por Data

La base canónica vigente separa los **source facts manuales** del resultado consolidado:

- `stops.daily_classification_time` es la fuente canónica para tiempos OEE manuales diarios por línea/clasificación.
- `waste.daily_manual_quantity` es la fuente canónica para kg manuales diarios por planta/línea/subclasificación.
- `kpi.kpi_result` continúa siendo la salida consolidada/versionada de KPI Manuales con `source_mode = MANUAL`.

Criterio implementado:

- Source facts + nueva versión de `kpi_result` se escriben en **una sola transacción PostgreSQL**.
- Una fila append-only de `kpi.kpi_result` por versión del registro manual.
- Nuevos snapshots usan `schema = MANUAL_KPI_V2` e incluyen los IDs de `daily_classification_time` y `daily_manual_quantity` usados por la versión.
- Los snapshots `MANUAL_KPI_V1` existentes siguen siendo legibles para compatibilidad histórica.
- Tiempos y kg se leen de las nuevas tablas canónicas en V2; masas elaboradas/entregadas y toneladas producidas permanecen en `source_snapshot` porque la base vigente no define otra entidad source-fact para ellas.
- `source_snapshot` conserva además timezone, versión, actor, timestamps, correlación y eventos CREATE/UPDATE.
- El primer `kpi_result_id` se conserva como `recordId` estable del registro manual.
- Las actualizaciones no sobrescriben versiones anteriores de `kpi_result`; las tablas source-fact conservan auditoría por trigger.
- Concurrencia por `plantId + lineId + businessDate` se serializa en PostgreSQL y sigue exigiendo `expectedVersion`.
- `period_start` parte del inicio local de `businessDate`; `period_end` se fija a +24 horas conforme a la regla funcional cerrada.

### Configuración requerida

`GBC_MANUAL_KPI_CAPTURE_KPI_CODE` debe apuntar al `kpi.kpi_definition.kpi_code` activo que Data haya definido para representar la captura manual compuesta en `kpi_result`. El micro no inventa ese código ni crea datos maestros.

## Dependencia externa para propagación a reportes

La spec exige consumo en OEE 1, OEE 2, OEE completo, indicadores y reportes de desperdicio. Esos consumidores no están dentro de este microservicio. El criterio ya queda cerrado como `AUTOMATIC_WINS_MANUAL_RETAINED_FOR_TRACEABILITY`, pero el pipeline/reporting debe consumir los registros `kpi.kpi_result` con `source_mode = MANUAL` conforme al contrato aprobado por Data y aplicar esa precedencia. La integración de esos consumidores sigue fuera de este repositorio.

## Ajuste incorporado por Spec 08
- La ventana de edición se centraliza en dominio y queda formalmente definida como `[createdAt, createdAt + 8h)`: el instante exacto `+8h` ya no es editable.
- El registro conserva `plantTimezone` como snapshot para mostrar creación, actualizaciones y expiración de forma estable aun si la configuración futura de la planta cambia.
- Se expone `GET /api/v1/manual-kpis/trace` para recuperar actor/timestamps y eventos CREATE/UPDATE sin alterar el contrato de lectura principal.
