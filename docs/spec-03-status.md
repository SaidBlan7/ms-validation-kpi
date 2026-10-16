# Spec 03 — Selección de fecha (máximo 24 hrs, fines de semana y festivos)

## Implementado

- Única fecha elegible: día calendario inmediatamente anterior (`D-1`) en la zona configurada de la planta.
- No se salta sábado, domingo ni festivo; no se usa calendario de días hábiles.
- Validación de fecha en servidor para `eligibility` y nuevamente en cada `prevalidate`.
- Una URL/pantalla vieja no conserva elegibilidad: D-1 se recalcula con la hora actual de la planta en cada solicitud.
- `GET /api/v1/oee-validation/temporal-context` devuelve por separado:
  - `plantTimezone`,
  - `currentOperationDate`,
  - `currentOperationDateTime`,
  - `serverEvaluatedAtUtc`,
  - `eligibleBusinessDate`,
  - `eligibilityWindowStartsAt`,
  - `eligibilityWindowEndsAtExclusive`,
  - `revalidateOnSubmit=true`,
  - `crossMidnightBehavior=SERVER_RECALCULATES_D_MINUS_1_AND_REJECTS_STALE_DATE`.
- La ventana publicada corresponde al día calendario local en el que D-1 es editable. El front puede refrescar al llegar a `eligibilityWindowEndsAtExclusive`, pero el backend no depende de ese refresh y vuelve a validar al guardar.
- Diferencia explícita entre semántica implementada (`LOCAL_CALENDAR_OPERATION_DAY`) y la interpretación de “24 horas transcurridas”, que sigue marcada como `PENDING_FUNCTIONAL_DEFINITION`.
- Alcance de planta/línea, asignación KPI y acceso genérico se validan antes de devolver contexto temporal.
- Política actual basada en `core.plant.timezone_name` y etiquetada `PROVISIONAL_PLANT_TIMEZONE` porque la propia especificación aún pide confirmar la autoridad/corte OEE.
- `timezone_name` vacío o inválido produce blocker controlado (`PLANT_TIMEZONE_NOT_CONFIGURED` / `INVALID_PLANT_TIMEZONE_CONFIGURATION`) y no un error interno.
- El cálculo usa `ZoneId`, por lo que cambios de offset/horario estacional se resuelven por las reglas IANA de la zona de la planta; no se usa el timezone del navegador/usuario.
- Logging con correlation ID para consulta de contexto temporal y rechazos.
- App Service, Azure Functions y OpenAPI alineados.
- Sin tablas, columnas ni escrituras nuevas en la base canónica.

## Pendiente / definición externa

- Confirmar si “máximo 24 hrs” significa horas transcurridas o, como se implementa actualmente a partir del texto principal de la spec, día calendario anterior.
- Confirmar el corte oficial del día OEE si no coincide con medianoche local.
- Confirmar formalmente que `core.plant.timezone_name` es la zona horaria autoritativa para Validación/Aprobación KPI.

## Frontend ya habilitado por este contrato

El front puede mostrar sólo `eligibleBusinessDate`, presentar claramente `currentOperationDateTime` por separado y usar `eligibilityWindowEndsAtExclusive` para refrescar una pantalla abierta. Aunque no refresque, un submit con la fecha anterior después del cambio de día será rechazado por servidor.
