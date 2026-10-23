# Spec 06 - Motivo, causa, plan de acción e incidente

## Cierre

Spec 06 queda cerrada para este microservicio.

- Sólo se aceptan `OPERATIVE` y `SYSTEM`.
- `OPERATIVE` exige `operationalCause` y `actionPlan` no vacíos.
- `SYSTEM` exige `incidentNumber` no vacío.
- Al cambiar de motivo, los campos que ya no aplican se descartan para evitar evidencia obsoleta.
- `incidentNumber` se normaliza como texto de una línea y se limita a **200 caracteres Unicode**, alineado con `kpi.kpi_validation.incident_reference VARCHAR(200)`.
- `operationalCause` y `actionPlan` se normalizan y se limitan a **2,000 caracteres Unicode** cada uno.
- Se rechazan caracteres de control no soportados. Causa/plan permiten salto de línea y tabulación.
- No se aplica regex al incidente y no se consulta GB Support: la Spec confirma la captura del número, mientras que formato e integración no tienen contrato aprobado.
- La política de seguridad de este servicio es minimizar texto libre y **no registrar causa, plan ni incidente completos en logs**. La clasificación corporativa de contenido sensible sigue siendo una política organizacional externa.
- OEE, tiempos y masas usan la misma validación y persisten la evidencia atómicamente con la propuesta `PENDING` en `kpi.kpi_validation`.
- `GET /api/v1/oee-validation/justification/policy` expone esta política cerrada.
- No se agregan tablas ni columnas.

## Fuera del alcance de este microservicio

- Validación online del incidente contra GB Support, hasta que exista un contrato/integración aprobado.
- Comentarios adicionales de aprobación/rechazo, que pertenecen a Aprobación KPI.
- La vista de Aprobación y los reportes deben consumir la evidencia persistida; este servicio deja disponibles los datos canónicos, pero no implementa esos módulos.
