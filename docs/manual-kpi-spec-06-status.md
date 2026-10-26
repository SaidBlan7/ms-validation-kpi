# KPI Manuales — Spec 06: Subclasificaciones de desperdicios (compendio global)

## Estado
Implementada en domain, application, adapter-memory, adapter-postgres, App Service y Azure Functions. Reutiliza el mismo registro manual diario y el mismo bloqueo de persistencia canónica documentado desde la Spec 01.

## Reglas cerradas
- La captura de desperdicio pertenece al registro `plantId + lineId + businessDate`.
- Cada valor manual de desperdicio se registra en **kg** y debe apuntar a una `waste_subclassification` canónica; ya no se acepta una clasificación sin subclasificación.
- Se conservan ambos identificadores (`wasteClassificationId` y `wasteSubclassificationId`) para validar que la subclasificación pertenece a su clasificación.
- El catálogo mostrado por el micro se obtiene dinámicamente de `waste.waste_classification` + `waste.waste_subclassification`.
- Para la planta seleccionada se resuelve su organización vigente y se incluyen subclasificaciones globales (`organization_id IS NULL`) y las de esa organización.
- Si existe una fila global y otra de organización con el mismo código de clasificación/subclasificación, se prefiere la fila de organización.
- Solo entradas activas se muestran para nuevas capturas.
- Los kg aceptan cero y decimales; negativos o nulos se rechazan. No se impone precisión máxima artificial.
- No se permiten subclasificaciones duplicadas dentro de la misma captura.
- `producedTonnes` permanece separado de los kg por subclasificación y, desde la Spec 07, su unidad contractual queda explícitamente fijada en toneladas.
- Si una subclasificación se inactiva durante la ventana de 8 horas, no puede agregarse como nueva; si ya formaba parte del registro existente, puede conservarse/corregirse durante esa ventana siempre que la referencia canónica siga existiendo.
- Las actualizaciones mantienen optimistic locking mediante `expectedVersion`.
- Los históricos conservan el ID canónico estable; el nombre visual se consulta desde catálogo y no sustituye la referencia.

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
El bloque de desperdicio deja de admitir una referencia opcional a subclasificación: cada kg manual debe quedar vinculado a una subclasificación canónica estable. Se agrega un endpoint de catálogo para que el cliente no invente IDs/nombres.

### Spec 02
Sin cambio de autorización. El catálogo se filtra dentro del alcance ya autorizado de planta/línea.

### Spec 03
Sin cambio temporal. Nueva captura: 96 horas; edición existente: ventana independiente de 8 horas.

### Spec 04
Sin cambio en tiempos OEE. Se reutiliza el mismo control de concurrencia (`expectedVersion`) para desperdicios.

### Spec 05
Sin cambio en masas elaboradas/entregadas. El bloque OEE y el bloque de desperdicios continúan siendo partes separadas del mismo registro manual.

## Pendiente externo real
1. Sigue pendiente la entidad canónica de persistencia de KPI Manuales solicitada desde la Spec 01. Esa estructura debe poder guardar por detalle de desperdicio, al menos, la referencia estable a la subclasificación canónica y los kg capturados, además de usuario/timestamps/versionado/auditoría del registro.
2. El esquema actual no contiene una entidad/version de “compendio global” que permita congelar explícitamente qué versión del compendio estaba vigente al guardar. El micro puede conservar IDs estables, pero no puede persistir una versión de compendio inexistente. Data debe confirmar si ya existe una fuente/versionado oficial fuera de este esquema o si se incorporará al modelo canónico.
3. La precisión decimal máxima contractual de kg no está definida por la spec ni por el esquema de captura manual; el micro conserva `BigDecimal` sin redondeo hasta recibir el contrato definitivo.
4. Los reportes/indicadores de desperdicio son consumidores externos a este micro y deberán leer la persistencia canónica de KPI Manuales cuando exista.
