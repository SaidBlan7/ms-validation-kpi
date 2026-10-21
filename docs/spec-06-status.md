# Spec 06 - Motivo, causa, plan de acción e incidente

## Implementado

- Sólo se aceptan `OPERATIVE` y `SYSTEM`.
- `OPERATIVE` exige causa y plan de acción no vacíos.
- `SYSTEM` exige número de incidente no vacío.
- Se normalizan espacios/line endings y se rechazan caracteres de control no soportados.
- Al cambiar de motivo se descartan campos que ya no aplican, evitando evidencia obsoleta.
- Las prevalidaciones OEE, tiempos y masas devuelven la justificación normalizada.
- OEE general y tiempos de paro persisten motivo/causa/plan/incidente atómicamente en las columnas existentes de `kpi.kpi_validation`.
- `GET /api/v1/oee-validation/justification/policy` expone motivos, campos requeridos y política técnica.
- Longitudes máximas y regex de incidente son opcionales/configurables; no se inventa un límite ni patrón de negocio.
- Los logs no escriben causa/plan/incidente completos.
- No se agregan tablas ni columnas.

## Limitación heredada de Spec 05

La justificación de masas se valida y normaliza, pero no puede persistirse como una propuesta de masa mientras el modelo canónico no pueda identificar durablemente `ELABORATED_MASS`/`DELIVERED_MASS`.

## Pendiente / dependencia externa

- Mostrar la misma evidencia desde Aprobación KPI y reportes.
- Valores oficiales de longitud UX, si se desean límites canónicos.
- Patrón/formato oficial del incidente.
- Validación online contra GB Support (la Spec confirma captura, no la integración).
- Política oficial de datos sensibles en texto libre.
- Comentarios adicionales al aprobar/rechazar pertenecen a Aprobación KPI.
