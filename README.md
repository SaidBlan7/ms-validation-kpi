# KPI Manuales — cierre Spec 04

Este paquete contiene únicamente archivos nuevos/modificados para cerrar la Spec 04 sobre el micro recibido.

## Cambios
- `docs/manual-kpi-spec-04-status.md`: actualiza el estado a persistencia canónica mediante `kpi.kpi_result`, documenta el filtro `PROGRAMMED`/`UNPROGRAMMED` y elimina el pendiente de storage ya resuelto por Data.
- `docs/openapi/kpi-validation-service.yaml`: alinea la descripción del catálogo con el comportamiento real: solo se devuelven clasificaciones activas elegibles con `oeeStopTypeCode` `PROGRAMMED` o `UNPROGRAMMED`.

## Código
No se requieren cambios funcionales adicionales. El ZIP recibido ya contiene:
- horas decimales con `BigDecimal`;
- sumatoria exacta `<= 24`;
- rechazo atómico de valores inválidos;
- catálogo canónico por organización;
- filtro `PROGRAMMED`/`UNPROGRAMMED` en PostgreSQL y validación server-side;
- edición durante 8 horas;
- optimistic locking con `version` / `expectedVersion`;
- persistencia versionada en `kpi.kpi_result` con `source_mode=MANUAL` y `source_snapshot`.

Specs 01–03 no requieren ajustes adicionales por esta especificación.
