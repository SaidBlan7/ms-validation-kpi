# KPI Manuales — Spec 07

Paquete con **solo archivos nuevos/modificados** para implementar la Spec 07 y ajustar Specs 01–06 donde fue necesario.

## Cambios
- `domain`: formaliza unidades `KG` y `TONNE` y valida toneladas producidas no negativas; permite cero y decimales.
- `application`: mueve la validación de `producedTonnes` a policy de dominio y expone en la policy del módulo que kg y toneladas son magnitudes/unidades distintas.
- `docs`: agrega estado de Spec 07, ajusta Spec 06 y actualiza OpenAPI.

## Sin cambios necesarios
- `adapter-memory`: ya persiste el `ManualWasteSection` completo, incluido `producedTonnes`.
- `adapter-postgres`: sigue bloqueado para escritura de KPI Manuales hasta que Data defina la entidad canónica; la Spec 07 no agrega un catálogo/lookup nuevo.
- `bootstrap`: no hay puertos/adapters nuevos.
- `runtime-app-service` y `runtime-functions`: ya aceptaban y mapeaban `waste.producedTonnes`; no requieren cambio de código para la nueva regla.

## Pendiente externo
Sigue vigente el pendiente de Data de la Spec 01: definir la persistencia canónica de KPI Manuales, ahora incluyendo el valor de toneladas producidas y su trazabilidad/origen manual.
