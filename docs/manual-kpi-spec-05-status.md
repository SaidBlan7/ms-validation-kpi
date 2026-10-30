# KPI Manuales — Spec 05: Masas elaboradas y entregadas (rendimiento)

## Estado
Cerrada en el microservicio. Implementada en dominio, application, adapter-memory, adapter-postgres, App Service y Azure Functions. La persistencia productiva reutiliza `kpi.kpi_result`, conforme a la definición de Data para KPI Manuales.

## Reglas cerradas
- La captura pertenece al mismo registro OEE manual de `plantId + lineId + businessDate`.
- El bloque de rendimiento tiene exactamente dos magnitudes funcionales: `ELABORATED_MASS` y `DELIVERED_MASS`.
- El contrato HTTP usa `oee.massPerformance.elaboratedMass` y `oee.massPerformance.deliveredMass`.
- Si se envía el bloque `massPerformance`, ambos valores son obligatorios; si no se desea capturar rendimiento, el bloque puede omitirse.
- Se aceptan cero y valores decimales/fraccionarios.
- Se rechazan valores negativos y entradas no numéricas antes de persistir.
- No se aplica redondeo ni límite artificial de decimales en el micro; se conserva el `BigDecimal` recibido.
- No se impone `deliveredMass <= elaboratedMass`. La spec reconoce desfases operativos y casos válidos donde entregadas puede ser mayor que elaboradas.
- La ventana de edición continúa siendo 8 horas absolutas desde el primer guardado y las actualizaciones mantienen optimistic locking mediante `expectedVersion`.
- Este micro captura las entradas manuales; no inventa una fórmula/redondeo de rendimiento. El cálculo efectivo debe reutilizar el modelo corporativo/reporting cuando esté definido por sus consumidores.

## Persistencia canónica
- PostgreSQL persiste el registro manual en `kpi.kpi_result` con `source_mode = 'MANUAL'`.
- `plant_id`, `line_id`, `period_start` y `period_end` conservan el contexto canónico del registro.
- `source_snapshot` usa el esquema `MANUAL_KPI_V1` y conserva el bloque OEE completo.
- Dentro de `source_snapshot.oee.massPerformance` se guardan de forma diferenciada `elaboratedMass` y `deliveredMass`.
- Cada modificación genera una nueva versión del registro manual; el `recordId` lógico permanece estable y el snapshot conserva `version`, creador, timestamps y trazabilidad de actualización.
- La lectura PostgreSQL reconstruye nuevamente `ManualMassPerformanceInput` desde esos dos valores, por lo que la captura manual conserva ambas magnitudes de ida y vuelta.

## Ejemplo de captura
```json
{
  "plantId": 1,
  "lineId": 10,
  "businessDate": "2026-10-22",
  "expectedVersion": 2,
  "oee": {
    "massPerformance": {
      "elaboratedMass": 10.5,
      "deliveredMass": 9.25
    }
  }
}
```

En creación se omite `expectedVersion`. En modificación se envía la versión leída del registro.

## Ajustes a Specs 01–04
### Spec 01
Sin cambio contractual. Se confirma que las dos magnitudes forman parte del `source_snapshot` del mismo `kpi_result` manual.

### Spec 02
Sin cambio. Se reutiliza el mismo alcance de planta/línea y autorización de KPI Manuales.

### Spec 03
Sin cambio. Una nueva captura debe cumplir la selección de 96 horas; una edición válida continúa gobernada por su ventana independiente de 8 horas.

### Spec 04
Sin cambio en tiempos. El bloque `stopTimes` convive con `massPerformance` dentro de la misma sección OEE manual y ambos se persisten en el mismo snapshot.

## Pendientes externos que no bloquean esta spec
- La fórmula y el redondeo del rendimiento derivado pertenecen al modelo corporativo/consumidor; esta especificación no los define.
- La propagación visual a OEE 1, OEE 2 y OEE completo depende de que esos consumidores lean el `kpi_result` manual, pero la captura y persistencia de las dos entradas queda cerrada en este micro.
