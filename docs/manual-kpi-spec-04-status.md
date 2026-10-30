# KPI Manuales — Spec 04: Registro de tiempos por clasificación (efectividad)

## Estado
Implementada y cerrada en dominio, application, adapters de referencia/persistencia, App Service y Azure Functions. No requiere DDL nuevo para el catálogo de clasificaciones. La persistencia PostgreSQL ya está resuelta mediante `kpi.kpi_result` con `source_mode = MANUAL` y `source_snapshot`, conforme a la decisión de Data adoptada en la Spec 01.

## Decisiones cerradas
- La unidad visible/contractual de captura es **hora decimal** (`hours`), no segundos enteros.
- La suma se evalúa con `BigDecimal`, sin redondear antes de validar.
- Se permiten valores `0`, una sola clasificación con `24`, y cualquier combinación decimal cuya suma sea `<= 24`.
- `24` horas exactas son válidas; cualquier suma mayor a `24` se rechaza de forma atómica.
- No se impone un número máximo artificial de decimales porque la especificación no lo define. El contrato conserva el decimal recibido y compara exactamente contra 24; la persistencia en `source_snapshot` conserva su representación decimal.
- Las clasificaciones NO se hardcodean en producción. Se leen de `stops.stop_classification` y se exponen con `classificationCode`, `classificationName`, `displayOrder` y `oeeStopTypeCode`.
- La separación programado/no programado proviene de `oee_stop_type_code`. Para esta captura se aceptan únicamente las clasificaciones activas con `PROGRAMMED` o `UNPROGRAMMED`; cualquier otro código extensible del catálogo queda fuera de KPI Manuales hasta que una especificación futura lo incorpore explícitamente.
- Para una planta se consideran las clasificaciones globales (`organization_id IS NULL`) y las de su organización vigente. Si el mismo `classification_code` existe global y específico de organización, prevalece el específico de organización.
- No existe en la base canónica una relación clasificación-línea que marque “no aplicable”. Por deber-ser, el micro muestra el catálogo canónico aplicable al contexto organizacional; una categoría que la operación no use puede enviarse en cero u omitirse. No se inventa ocultamiento por línea.
- La edición sigue limitada por las 8 horas definidas en Specs 01/03.
- La concurrencia se resuelve con **optimistic locking** usando `version`: en creación se omite `expectedVersion`; en actualización es obligatorio y debe coincidir con la versión leída. Si otro usuario guardó antes, se responde conflicto y el cliente debe recargar.

## API
### Catálogo
`GET /api/v1/manual-kpis/stop-classifications?plantId={id}&lineId={id}`

Devuelve solo clasificaciones activas y respeta autenticación, acceso planta/línea, autorización KPI Manuales y feature activo.

### Guardado
`PUT /api/v1/manual-kpis`

Ejemplo de sección OEE:

```json
{
  "plantId": 1,
  "lineId": 10,
  "businessDate": "2026-10-22",
  "expectedVersion": 2,
  "oee": {
    "stopTimes": [
      { "stopClassificationId": 101, "hours": 1.25 },
      { "stopClassificationId": 102, "hours": 0.5 }
    ]
  }
}
```

Para alta nueva se omite `expectedVersion`. Para edición debe enviarse la versión retornada por el `GET`/guardado previo.

## Validaciones
- `stopClassificationId > 0`.
- Clasificación activa y dentro del catálogo global/organizacional efectivo de la planta.
- Sin clasificaciones duplicadas dentro de la misma sección OEE.
- `hours` obligatorio y `>= 0`.
- Sumatoria exacta `<= 24`.
- Si falla cualquier validación, no se persiste parcialmente.

## Ajustes a Specs 01–03
### Spec 01
- Se reemplaza el contrato previo de tiempo en segundos por **horas decimales**, que es lo que exige la Spec 04.
- El registro manual conserva `version`; ahora esa versión también funciona como token de concurrencia para actualizaciones.
- El adapter memory aplica el mismo control de concurrencia para pruebas end-to-end.

### Spec 02
- Sin cambio de modelo de seguridad. El endpoint de catálogo reutiliza exactamente el alcance de planta/línea y capability ya definidos.

### Spec 03
- Sin cambio de la regla de 96 h/8 h. La validación de tiempos se ejecuta al guardar después de resolver alcance y antes de persistir.

## Base canónica usada
- `stops.stop_classification.organization_id`
- `stops.stop_classification.classification_code`
- `stops.stop_classification.classification_name`
- `stops.stop_classification.display_order`
- `stops.stop_classification.oee_stop_type_code`
- `stops.stop_classification.is_active`
- `core.plant_context_assignment` para resolver la organización vigente de la planta.

## Persistencia canónica
Los tiempos quedan dentro del `source_snapshot` de la versión correspondiente en `kpi.kpi_result`, conservando `stopClassificationId`, horas decimales, contexto planta/línea/fecha, versión y auditoría funcional. Cada actualización genera una nueva versión del resultado manual y la lectura recupera la versión más reciente.

## Pendientes externos
No existe un pendiente de modelo de datos exclusivo de esta Spec 04. La propagación efectiva a OEE 1/OEE 2/OEE completo depende de los consumidores/reporting externos a este repositorio; el micro deja los datos manuales persistidos y trazables para dicho consumo.
