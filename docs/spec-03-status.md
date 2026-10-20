# Spec 03 — Selección de fecha (máximo 24 hrs, fines de semana y festivos)

## Implementado

- La única fecha editable es **D-1 calendario** respecto a la fecha local calculada por el servidor para la planta.
- El cálculo usa provisionalmente `core.plant.timezone_name`; una zona vacía o inválida bloquea la operación de forma controlada.
- No se aplica calendario laboral: sábado, domingo o festivo continúan siendo D-1 y por tanto son elegibles si corresponden al día inmediatamente anterior.
- `GET /api/v1/oee-validation/temporal-context` devuelve por separado la fecha/hora actual de operación y la fecha KPI elegible, evitando confundir fecha de registro con fecha del KPI.
- `GET /api/v1/oee-validation/eligibility` vuelve a calcular D-1 en servidor y rechaza cualquier fecha distinta, incluyendo D, D-2, fechas futuras y URLs antiguas.
- Todos los flujos que leen/prevalidan datos OEE, paros o masas reutilizan `check(...)`; por tanto no pueden eludir la regla temporal enviando `businessDate` manipulado.
- `POST /api/v1/oee-validation/validations` y `POST /api/v1/oee-validation/stop-times/validations` ejecutan nuevamente la prevalidación antes de persistir. Si la pantalla cruzó medianoche, el D-1 anterior deja de ser elegible y la propuesta se rechaza.
- La ventana devuelta por `temporal-context` finaliza en la siguiente medianoche local de planta para que el cliente pueda refrescar el contexto; esto no sustituye la revalidación obligatoria de servidor.
- Se conserva autenticación, asignación KPI específica de Spec 02, acceso genérico de planta y relación línea/planta antes de exponer contexto o permitir modificación.

## Decisiones que Spec 03 sí aclara

- “Contemplando fines de semana y festivos” **no** significa último día hábil: es siempre el día calendario inmediatamente anterior.
- No se permite navegar a semanas/meses anteriores para editar.
- La fecha debe revalidarse en servidor al guardar; no se confía en el valor que conserva el cliente.

## Abierto / fuera de definición funcional

- La especificación todavía no define si “máximo 24 hrs” debe interpretarse como 24 horas transcurridas o como día calendario D-1 cuando el día OEE tenga un corte distinto de medianoche.
- Falta confirmar que `core.plant.timezone_name` sea la autoridad funcional definitiva para D-1; se usa porque es el dato canónico disponible por planta.
- Falta definir un eventual corte OEE de línea/planta diferente a 00:00–00:00. La implementación actual declara explícitamente `PREVIOUS_LOCAL_CALENDAR_DAY` para no fingir esa definición.

## Impacto sobre Specs anteriores

- **Spec 01:** queda confirmado que la regla temporal actual es D-1 calendario y que fines de semana/festivos no se saltan. Permanece abierta únicamente la semántica de 24 horas/corte OEE y la autoridad final de timezone.
- **Spec 02:** la asignación específica de planta se aplica también a `temporal-context`, `eligibility` y cualquier operación con fecha; una revocación impide nuevas consultas/modificaciones aunque el cliente conserve una URL previa.