# Cierre KPI Manuales — Spec 08

Archivos nuevos/modificados únicamente.

## Resultado
- Spec 08 cerrada sobre la persistencia canónica actual en `kpi.kpi_result`.
- No fue necesario cambiar Java: el micro ya conserva creador/creación, actualizador/actualización, `editableUntil`, timezone snapshot y eventos CREATE/UPDATE dentro de `source_snapshot`.
- La ventana de edición permanece semiabierta: `[createdAt, createdAt + 8h)`; exactamente a +8h el registro ya está bloqueado.
- `GET /api/v1/manual-kpis/trace` ya expone IDs de usuario estables y timestamps UTC/locales de planta.
- No se requieren ajustes funcionales a Specs 01–07.

## Cambio incluido
- `docs/manual-kpi-spec-08-status.md`: elimina el pendiente obsoleto de Data y documenta `kpi.kpi_result` + `source_mode=MANUAL` como persistencia canónica confirmada.
