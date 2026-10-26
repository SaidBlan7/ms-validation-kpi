# KPI Manuales — Spec 08 + ajustes Specs 01–07

Este paquete contiene únicamente archivos nuevos/modificados.

## Implementación Spec 08
- `ManualKpiEditWindowPolicy`: ventana exacta de edición `[createdAt, createdAt+8h)`.
- `ManualKpiTraceView` / `ManualKpiAuditEventView`: creación, actualización, expiración y eventos en UTC + hora local de planta.
- `ActorIdentityPort.findByCanonicalUserId`: resuelve nombre descriptivo manteniendo `user_id` como identidad histórica estable.
- PostgreSQL y memory implementan el lookup por `user_id` usando la misma infraestructura existente.
- `GET /api/v1/manual-kpis/trace` en App Service y Azure Functions.
- El timezone de la planta se conserva como snapshot del registro para no reinterpretar históricos si cambia la configuración futura.

## Ajustes 01–07
- Spec 01: se formaliza el final exclusivo de la ventana de 8h y el timezone snapshot.
- Spec 03: queda cerrada la inclusividad exacta de edición y su independencia de la ventana de 96h.
- Specs 04–07: sus cambios quedan cubiertos por el mismo versionado/auditoría; no requirieron cambio funcional adicional.
- Specs 02, 04, 05 y 06 no requieren archivos modificados por esta spec.

## Validación realizada
- `domain/src/main/java`: compilación completa con Java 21.
- `application/src/main/java`: compilación completa con Java 21.
- `InMemoryActorIdentityAdapter` y `JdbcActorIdentityAdapter`: compilación aislada con Java 21.
- Maven no está instalado en el entorno, por lo que no se ejecutó la build Maven completa de los runtimes.

## Base canónica
No se requiere un cambio nuevo de esquema por Spec 08. Sigue pendiente la entidad canónica general de KPI Manuales solicitada desde Spec 01. La futura persistencia debe soportar actor/timestamps, timezone snapshot, versionado y auditoría.
