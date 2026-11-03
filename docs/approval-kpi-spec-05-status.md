# Approval KPI — Spec 05: Aprobar/Rechazar Desperdicio

Estado de implementación en este micro: decisión sobre validaciones preexistentes `kpi.kpi_validation` cuyo detalle contiene exclusivamente `metric_definition.source_domain = WASTE`; bloquea mezclas WASTE/OEE, maneja expiración, rechazos, auditoría, concurrencia e idempotencia sin insertar `waste.waste_event`, `waste.waste_adjustment` o movimientos ERP/WMS. `effectiveValue` distingue propuesto aprobado de valor original.

**Trazabilidad interna disponible:** `validationId` identifica cabecera, y `kpiValidationDetailId` identifica exactamente cada diferencia persistida, incluso varias diferencias de la misma validación. Los IDs `metricDefinitionId` indican la métrica, **no** identifican un `waste_event`. La BD v2 actual no tiene FK `kpi.kpi_validation_detail -> waste.waste_event`. Por tanto esta implementación **no garantiza la trazabilidad al evento de APP/tableta**; requiere que el flujo productor publique/persista un correlativo de origen canónico y un contrato de lectura, o que la arquitectura apruebe una relación nueva de datos. No se inventa `waste_event_id` ni se infiere por planta/línea/fecha.

**Fuera de este micro:** reversión de transacciones ERP/WMS ya emitidas, duplicación/reversión de eventos de desperdicio y actualización de reportes que pertenezcan a otros componentes. Las preguntas de negocio sobre transfer/retorno y ERP/WMS permanecen abiertas por especificación.

**Verificación pendiente:** pruebas PostgreSQL y contractuales con el servicio emisor de APP/tableta y el consumidor de reportes. No debe declararse 100% del flujo extremo a extremo hasta demostrar la referencia de origen.
