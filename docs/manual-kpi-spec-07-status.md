# KPI Manuales — Spec 07: Toneladas producidas por día y línea

## Estado
Implementada sobre el mismo registro manual diario usado por Specs 01–06. No requiere un catálogo nuevo ni un adapter adicional; formaliza `producedTonnes` como dato independiente dentro del bloque manual de desperdicios.

## Reglas cerradas
- La captura pertenece al mismo `plantId + lineId + businessDate` del registro KPI Manual.
- `producedTonnes` representa exclusivamente producción manual en **toneladas métricas** (`TONNE`).
- Los desperdicios por subclasificación permanecen en **kg** (`KG`). Las dos magnitudes no comparten unidad ni deben convertirse implícitamente entre sí.
- Se aceptan cero y valores decimales no negativos.
- No se impone precisión decimal máxima ni redondeo en aplicación porque la especificación no define uno; se conserva `BigDecimal`.
- No se impone máximo por línea/planta porque la especificación no define un límite canónico.
- `0` toneladas con kg de desperdicio > 0 es válido; no existe una regla contractual que lo prohíba.
- La edición continúa gobernada por las 8 horas desde el primer guardado y por optimistic locking mediante `expectedVersion`.
- Para nuevas capturas sigue aplicando la selección histórica de 96 horas; una edición válida puede continuar hasta terminar su ventana de 8 horas aunque la fecha ya haya salido de esas 96 horas.
- La precedencia downstream se mantiene como en Spec 01: automático prevalece cuando exista una fuente automática aplicable, mientras el registro manual permanece disponible para trazabilidad. Esta política debe ser respetada por los consumidores/reportes.

## Contrato
```json
{
  "plantId": 1,
  "lineId": 10,
  "businessDate": "2026-10-22",
  "expectedVersion": 3,
  "waste": {
    "entries": [
      {
        "wasteClassificationId": 1,
        "wasteSubclassificationId": 101,
        "kilograms": 12.750
      }
    ],
    "producedTonnes": 25.400
  }
}
```

## Ajustes a Specs 01–06
### Spec 01
Se formaliza dentro del bloque manual de desperdicios la magnitud de producción diaria como `producedTonnes`, separada de los kg de desperdicio.

### Spec 02
Sin cambios. Se reutiliza el mismo alcance/autorización por planta y línea.

### Spec 03
Sin cambios. La fecha sigue las 96 horas para alta y las 8 horas independientes para edición.

### Spec 04
Sin cambios en tiempos OEE. La concurrencia por `expectedVersion` también protege cambios de toneladas producidas.

### Spec 05
Sin cambios. Masas elaboradas/entregadas siguen siendo el bloque de rendimiento OEE y no se confunden con toneladas producidas del bloque de desperdicios.

### Spec 06
Se hace explícita la unidad de `producedTonnes` como toneladas y se conserva `kilograms` exclusivamente para cada subclasificación de desperdicio.

## Pendiente externo real
1. Sigue pendiente la entidad canónica de persistencia de KPI Manuales solicitada desde la Spec 01. Debe poder conservar también las toneladas producidas del registro manual, además de los kg por subclasificación, usuario, timestamps, versionado y trazabilidad.
2. La precisión decimal máxima y cualquier límite operativo máximo por línea/planta no están definidos por la spec ni por la base canónica actual; no se inventa una restricción.
3. La propagación a reportes/indicadores PT/desperdicio es responsabilidad de los consumidores externos una vez exista la persistencia canónica.

## Ajuste incorporado por Spec 08
Los cambios de `producedTonnes` quedan trazados con actor, timestamp, versión y correlación del mismo registro manual. La vista de trazabilidad conserva creación y actualizaciones sin reemplazar el autor/timestamp inicial.
