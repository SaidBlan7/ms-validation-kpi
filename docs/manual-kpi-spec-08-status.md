# KPI Manuales — Spec 08: Fecha, hora y usuario de registro

## Estado
Implementada para `memory` y PostgreSQL. La trazabilidad del registro consolidado se conserva en `kpi_result.source_snapshot`; adicionalmente, los nuevos source facts de tiempos y desperdicio tienen `recorded_by/recorded_at`, `updated_by/updated_at` y triggers de auditoría canónicos.

## Reglas cerradas
- Se conserva siempre el `user_id` canónico que creó el registro.
- Se conserva `createdAt` del primer guardado y nunca se reemplaza durante una actualización.
- Cada actualización conserva actor, timestamp, versión, correlación y resultado en el histórico del registro.
- La expiración de edición se calcula una sola vez como `createdAt + 8 horas`.
- La ventana de edición es **[createdAt, createdAt + 8h)**: exactamente en el instante de expiración el registro ya está bloqueado.
- Se conserva `plantTimezone` como snapshot del registro. Los timestamps históricos se muestran usando ese timezone snapshot, por lo que un cambio posterior en `core.plant.timezone_name` no reinterpreta capturas anteriores.
- La identidad histórica se basa en `user_id`. El nombre visible se resuelve desde `app_security.user_identity` cuando está disponible; un cambio de nombre o desactivación posterior no altera el identificador histórico.
- La vista de trazabilidad muestra creación, última actualización, expiración y todos los eventos CREATE/UPDATE en hora local de la planta.

## API
- `GET /api/v1/manual-kpis/trace?plantId={id}&lineId={id}&businessDate=YYYY-MM-DD`
  - devuelve IDs de usuario estables;
  - nombres visibles cuando pueden resolverse;
  - timestamps UTC originales;
  - timestamps convertidos al timezone snapshot de la planta;
  - `editableUntil`, estado editable actual y semántica de ventana;
  - histórico de eventos de creación/actualización.
- Azure Functions expone la ruta equivalente `v1/manual-kpis/trace`.

## Ajustes a Specs 01–07
### Spec 01
Se centraliza la ventana de 8 horas en `ManualKpiEditWindowPolicy` y se formaliza que el final es exclusivo. La captura mantiene timezone snapshot para representación histórica estable.

### Spec 02
Sin cambio de autorización. La consulta de trazabilidad usa el mismo alcance planta/línea y no concede permisos adicionales.

### Spec 03
Se mantiene la independencia entre 96 horas para alta y 8 horas para edición. La expiración exacta de edición queda cerrada como exclusiva.

### Specs 04–07
Sin cambios en las reglas numéricas/catálogos. Todos los cambios realizados sobre tiempos OEE, masas, desperdicios y toneladas quedan cubiertos por el mismo versionado y eventos de auditoría del registro manual.

## Base canónica
`app_security.user_identity` ya permite resolver `user_id`, `display_name` y estado del usuario. `core.plant.timezone_name` sigue siendo fuente autoritativa al crear el registro.

La persistencia canónica vigente usa `stops.daily_classification_time` y `waste.daily_manual_quantity` como source facts para tiempos/kg, ambos auditados mediante `audit.capture_change`, y `kpi.kpi_result` como resultado consolidado/versionado. El snapshot conserva `createdByUserId`, `createdAt`, `updatedByUserId`, `updatedAt`, `editableUntil`, `plantTimezone`, correlación y eventos CREATE/UPDATE. Esta combinación cubre la trazabilidad requerida por la Spec 08.
