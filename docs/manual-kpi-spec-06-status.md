# KPI Manuales — Spec 06: Subclasificaciones de desperdicios (compendio global)

## Estado
Cerrada para este microservicio. Implementada en domain, application, adapter-memory y adapter-postgres. Los kg manuales se persisten como source facts en `waste.daily_manual_quantity`; `kpi.kpi_result` conserva la versión consolidada con `source_mode = MANUAL` y referencias a las filas fuente en `source_snapshot` V2.

## Reglas cerradas
- La captura de desperdicio pertenece al registro `plantId + lineId + businessDate`.
- Cada valor manual de desperdicio se registra en **kg** y debe apuntar a una `waste_subclassification` canónica; no se acepta una clasificación sin subclasificación.
- Se conservan ambos identificadores (`wasteClassificationId` y `wasteSubclassificationId`) para validar que la subclasificación pertenece a su clasificación y para mantener una referencia estable al compendio.
- El catálogo mostrado por el micro se obtiene dinámicamente de `waste.waste_classification` + `waste.waste_subclassification`.
- Para la planta seleccionada se resuelve su organización vigente y se incluyen subclasificaciones globales (`organization_id IS NULL`) y las de esa organización.
- Si existe una fila global y otra de organización con el mismo código de clasificación/subclasificación, se prefiere la fila de organización.
- Solo entradas activas se muestran para nuevas capturas.
- Los kg aceptan cero y decimales; negativos o nulos se rechazan. No se impone precisión máxima artificial ni redondeo en aplicación mientras el contrato no la defina.
- No se permiten subclasificaciones duplicadas dentro de la misma captura.
- `producedTonnes` permanece separado de los kg por subclasificación.
- Si una subclasificación se inactiva durante la ventana de 8 horas, no puede agregarse como nueva; si ya formaba parte del registro existente, puede conservarse/corregirse durante esa ventana siempre que la referencia canónica siga existiendo.
- Las actualizaciones mantienen optimistic locking mediante `expectedVersion`.
- Los históricos conservan los IDs canónicos estables; los nombres visuales se obtienen del catálogo y no sustituyen la referencia.
- La lectura PostgreSQL reconstruye los kg desde `waste.daily_manual_quantity` para snapshots V2; los snapshots V1 históricos siguen siendo legibles como compatibilidad.

## Endpoint de catálogo
`GET /api/v1/manual-kpis/waste-catalog?plantId={plantId}&lineId={lineId}`

Devuelve por entrada:
- `wasteClassificationId`
- `classificationCode`
- `classificationName`
- `wasteSubclassificationId`
- `subclassificationCode`
- `subclassificationName`
- `active`

El endpoint exige el mismo acceso de planta/línea, autorización KPI Manuales y feature activo que el resto del módulo.

## Contrato de captura
```json
{
  "plantId": 1,
  "lineId": 10,
  "businessDate": "2026-10-22",
  "expectedVersion": 2,
  "waste": {
    "entries": [
      {
        "wasteClassificationId": 1,
        "wasteSubclassificationId": 101,
        "kilograms": 12.750
      }
    ],
    "producedTonnes": 25.4
  }
}
```

## Ajustes a Specs 01–05
### Spec 01
El bloque de desperdicio requiere referencia canónica estable. Los kg se persisten como source facts en `waste.daily_manual_quantity`; `kpi_result.source_snapshot` conserva el resultado/versionado y la referencia a esas filas.

### Spec 02
Sin cambio de autorización. El catálogo se consulta dentro del alcance ya autorizado de planta/línea.

### Spec 03
Sin cambio temporal. Nueva captura: 96 horas; edición existente: ventana independiente de 8 horas.

### Spec 04
Sin cambio en tiempos OEE. Se reutiliza el mismo control de concurrencia (`expectedVersion`) para desperdicios.

### Spec 05
Sin cambio en masas elaboradas/entregadas. El bloque OEE y el bloque de desperdicios continúan separados dentro del mismo registro manual.


## Persistencia canónica vigente
- `waste.daily_manual_quantity` es la fuente de verdad para los kg manuales por planta/línea/fecha/subclasificación.
- `uom_id` se resuelve contra la UOM activa con `uom_code = KG`; si no existe, el guardado falla de forma explícita.
- Crear/editar sincroniza altas, cambios y eliminaciones de filas source-fact dentro de la misma transacción que crea la versión de `kpi_result`.
- El trigger `trg_audit_daily_manual_quantity` conserva actor/cambio mediante `audit.change_log`.
- `kpi_result.source_snapshot` V2 incluye los IDs de `daily_manual_quantity` usados por la versión.

## Dependencias externas que no bloquean el cierre del micro
1. El esquema canónico actual no modela una entidad/version explícita del “compendio global” para congelar su versión histórica. El micro conserva IDs canónicos estables; si negocio/Data requiere reconstrucción por versión formal del compendio, esa capacidad debe definirse externamente.
2. La precisión decimal máxima contractual de kg continúa abierta en la especificación. El micro conserva `BigDecimal` sin redondeo ni límite artificial.
3. La propagación efectiva a reportes/indicadores de desperdicio depende de los consumidores externos. El micro deja los kg en `waste.daily_manual_quantity` y el resultado consolidado/versionado en `kpi_result`.
