# KPI Manuales — Spec 05: Masas elaboradas y entregadas (rendimiento)

## Estado
Implementada en dominio, application, App Service, Azure Functions y adapter memory. No agrega una nueva dependencia de esquema distinta al bloqueo de persistencia canónica ya documentado en la Spec 01.

## Reglas cerradas
- La captura pertenece al mismo registro OEE manual de `plantId + lineId + businessDate`.
- El bloque de rendimiento tiene exactamente dos magnitudes funcionales: `ELABORATED_MASS` y `DELIVERED_MASS`.
- El contrato HTTP usa `oee.massPerformance.elaboratedMass` y `oee.massPerformance.deliveredMass`.
- Si se envía el bloque `massPerformance`, ambos valores son obligatorios; si no se desea capturar rendimiento, el bloque puede omitirse.
- Se aceptan cero y valores decimales/fraccionarios.
- Se rechazan valores negativos y entradas no numéricas antes de persistir.
- No se aplica redondeo ni límite artificial de decimales en el micro; se conserva el `BigDecimal` recibido hasta que exista el contrato canónico final de persistencia.
- No se impone `deliveredMass <= elaboratedMass`. La spec reconoce desfases operativos y casos válidos donde entregadas puede ser mayor que elaboradas.
- La ventana de edición continúa siendo 8 horas absolutas desde el primer guardado y las actualizaciones mantienen optimistic locking mediante `expectedVersion`.
- La precedencia frente a dato automático permanece como en Spec 01: el automático será efectivo downstream y el manual se conserva para trazabilidad.
- Este micro captura las entradas manuales; no inventa una fórmula/redondeo de rendimiento. El cálculo efectivo debe reutilizar el modelo corporativo/reporting cuando esté definido por sus consumidores.

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
El rendimiento deja de modelarse como una lista genérica elegida por cliente (`metricDefinitionId + value + uomId`) y queda como las dos entradas semánticas explícitas exigidas por KPI Manuales.

### Spec 02
Sin cambio. Se reutiliza el mismo alcance de planta/línea y autorización de KPI Manuales.

### Spec 03
Sin cambio. Una nueva captura debe cumplir la selección de 96 horas; una edición válida continúa gobernada por su ventana independiente de 8 horas.

### Spec 04
Sin cambio en tiempos. El bloque `stopTimes` convive con `massPerformance` dentro de la misma sección OEE manual.

## Pendiente externo real
Sigue siendo necesario que Data defina la entidad canónica de KPI Manuales solicitada desde la Spec 01. Esa estructura deberá poder conservar también `ELABORATED_MASS` y `DELIVERED_MASS` como entradas diferenciadas del mismo registro manual y hacerlas trazables hacia OEE/reporting.

La fórmula/redondeo final de rendimiento no está definida por esta spec y corresponde al modelo corporativo consumidor; no se inventa en este micro.
