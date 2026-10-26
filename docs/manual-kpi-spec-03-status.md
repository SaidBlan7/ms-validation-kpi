# KPI Manuales — Spec 03: Selección de fecha (máximo 96 horas)

## Estado
Implementada en lógica de dominio, App Service y Azure Functions. No requiere cambio de esquema.

## Decisiones cerradas
- Las **96 horas son una ventana móvil exacta**, no “cuatro fechas calendario”.
- El instante de referencia de una `businessDate` es el **inicio de esa fecha en `core.plant.timezone_name`**.
- El límite es **inclusivo**: exactamente `96:00:00` horas de antigüedad se permite; `96:00:00 + 1 segundo` se rechaza.
- Fechas futuras se evalúan contra la **fecha local de la planta**, nunca contra la zona del usuario.
- Sábados, domingos y festivos **no se filtran**. La regla es puramente temporal + autorización. No se requiere catálogo de festivos para permitir captura.
- Para OEE, el periodo asociado es **fijo de 24 horas** desde el inicio local resuelto de `businessDate`: `[periodStart, periodStart + 24h)`. En un cambio DST se conserva esa duración física de 24 horas aunque el reloj local del extremo final no coincida con la siguiente medianoche.
- La ventana de 96 horas controla **selección y creación** de una captura nueva.
- Una captura creada válidamente mantiene su ventana independiente de **8 horas desde el primer guardado**. Si durante esas 8 horas la fecha objetivo cruza fuera de las 96 horas, la edición sigue permitida hasta `editableUntil`.
- Una pantalla que permanezca abierta no “congela” elegibilidad: el servidor reevalúa la fecha al intentar crear/guardar una captura nueva.

## Implementación
### Dominio
- `domain.manual.ManualKpiDatePolicy`: política única de selección temporal.
- `domain.manual.ManualKpiDateSelection`: decisión explícita con:
  - `selectable`
  - `rejectionCode`
  - `oeePeriodStart`
  - `oeePeriodEnd`
  - `oldestAllowedStart`
  - `latestAllowedStart`
  - `historicalAgeSeconds`
  - indicador de fin de semana
  - confirmación de que no se aplica filtrado por calendario/festivo.

### API
- `GET /api/v1/manual-kpis/date-selection?plantId={id}&lineId={id}&businessDate=YYYY-MM-DD`
  - valida autenticación, acceso de planta/línea, autorización KPI Manuales y feature activo;
  - usa zona horaria canónica de planta;
  - devuelve la decisión sin requerir que exista todavía persistencia PostgreSQL para la captura manual.
- `PUT /api/v1/manual-kpis`
  - creación: aplica la política de 96 horas;
  - edición de registro existente: aplica únicamente la ventana de 8 horas desde primer guardado.
- Azure Functions expone ruta equivalente `v1/manual-kpis/date-selection`.

## Ajustes a Specs 01 y 02
### Spec 01
Se corrige la interacción entre 96 h y 8 h. Antes el `save` validaba 96 h antes de saber si era alta o edición. Ahora:
1. se busca el registro por planta/línea/fecha;
2. si no existe, se exige que la fecha sea seleccionable dentro de 96 h;
3. si existe, se respeta `editableUntil` aunque la fecha ya haya salido de la ventana histórica.

También se expone la evaluación de fecha como contrato explícito para que UI pueda mostrar la fecha objetivo y su periodo antes de guardar.

### Spec 02
No cambia el modelo de autorización. La evaluación temporal reutiliza exactamente el alcance efectivo de planta/línea y la zona horaria canónica de la planta. Se aclara en documentación que la administración de alcance KPI Manuales no concede `user_plant_access` general.

## Base canónica
No se necesita pedir DDL ni catálogo de festivos a Data para esta spec:
- `core.plant.timezone_name` ya proporciona la zona horaria autoritativa.
- `core.line_plant_assignment` y seguridad existente determinan alcance.
- El calendario/festivo solo sería necesario si producto quiere **mostrar visualmente** que una fecha es festiva; no es necesario para permitirla.

## Pendientes reales
Ninguno para la regla de selección de fecha.

Sigue vigente el bloqueo externo de Spec 01: falta la entidad canónica de persistencia de KPI Manuales para guardar capturas en PostgreSQL/Lakebase y posteriormente integrarlas a reporting.


## Ajuste incorporado por Spec 04
El guardado mantiene intactas las reglas 96 h/8 h y agrega validación exacta de tiempos en horas decimales más optimistic locking por `version`. No cambia la semántica temporal de esta spec.

## Ajuste incorporado por Spec 08
La ventana independiente de edición queda cerrada como intervalo semiabierto `[createdAt, createdAt + 8h)`. Exactamente en `editableUntil` el registro está bloqueado. La zona horaria usada para mostrar un histórico ya creado es el snapshot almacenado con el registro, no una reinterpretación con una configuración futura de planta.
