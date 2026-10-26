# KPI Manuales Spec 06 — archivos nuevos/modificados

Este paquete contiene únicamente archivos nuevos/modificados para implementar la Spec 06 y los ajustes necesarios sobre Specs 01–05.

No reemplazar módulos completos: copiar respetando las rutas del repositorio.

Cambios principales:
- catálogo canónico de desperdicios por planta/organización;
- `GET /api/v1/manual-kpis/waste-catalog`;
- cada kg requiere `wasteClassificationId + wasteSubclassificationId`;
- regla de kg no negativos en domain;
- adapter-memory y adapter-postgres actualizados;
- conservación/corrección de una subclasificación previamente capturada si luego queda inactiva dentro de las 8 h;
- documentación Spec 06 y OpenAPI actualizados.

Persistencia PostgreSQL de la captura Manual KPI sigue fail-closed hasta que Data entregue la entidad canónica solicitada desde Spec 01.
