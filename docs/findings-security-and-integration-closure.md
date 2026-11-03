# Cierre de hallazgos: autorizaciones y contratos KPI

## Reglas ya implementadas en este microservicio

1. `app_security.user_plant_access` es la tabla canónica compartida. Una fila con prefijo `KPI_MANUALES:`, `KPI_APROBACION:` o `KPI_OEE_VALIDATION_SCOPE ` **no constituye un acceso organizacional general**.
2. Los permisos de módulo requieren una asignación organizacional general activa **además** de rol/capacidad y asignación específica. Altas nuevas fallan sin la asignación organizacional preexistente. Las revocaciones de módulo no eliminan la asignación general.
3. Lecturas de Aprobación desde PostgreSQL se limitan por usuario canónico, asignación específica, rol/capacidad y acceso organizacional en la propia consulta; Application aplica además el control de feature flag y línea. Memory conserva la validación en Application.
4. Aprobación y rechazo validan de nuevo los permisos dentro de la transacción PostgreSQL y bloquean filas de usuario, roles, capacidades y asignaciones de planta mientras deciden. La lógica de aprobación no inserta nuevos movimientos de desperdicio.

**Alcance:** este filtro protege las consultas realizadas por este microservicio. Otros servicios que consulten directamente `user_plant_access` deben adoptar el mismo contrato; no podemos modificar su código desde este repositorio. No se modificó la BD canónica.

## Revisión previa al despliegue

- Consultar las asignaciones existentes con marcadores de módulo y verificar que cada usuario/planta tenga además un permiso general vigente. No convertir permisos de módulo a acceso general automáticamente.
- Verificar que la habilitación específica no entregue acceso al resto de módulos; repetir la prueba con las tres familias de permisos.
- Probar dos conexiones: revocación del permiso general/específico y aprobación sobre la misma validación. Debe serializarse por los bloqueos de filas. Repetir para rol y capacidad.
- Ejecutar `mvn clean verify` con Java 21 y pruebas de integración con PostgreSQL que haya ejecutado los scripts de `bd (2).zip`.
- Verificar App Service y Azure Functions mediante el mismo usuario y datos de prueba.

## Contrato requerido con otros equipos (no implementado por este microservicio)

- **APP/tableta:** debe generar una validación canónica con identificador estable, referencia trazable al movimiento origen, planta/línea/fecha, valores original/propuesto, actor y timestamp. No debe inferirse que aprobar revierte una transacción ERP/WMS.
- **Motores y reportes:** las lecturas de cambios deben utilizar original para `PENDING`, `REJECTED` y `EXPIRED`; propuesto solo para `APPROVED`. Contrastar OEE 1, OEE 2, OEE completo y desperdicios con casos controlados.
- **Operación:** la política de precedencia de datos manuales/automáticos y las fórmulas corporativas de rendimiento pertenecen al contrato funcional del consumidor y deben confirmarse antes del certificado de extremo a extremo.

## Límites de la entrega

Los cambios de este paquete se compilan con Java 21 mediante `javac` sobre los módulos afectados. Sin Maven disponible ni servidor PostgreSQL activo, no constituyen evidencia de ejecución integral ni de un despliegue exitoso. Las pruebas de regresión añadidas deben ejecutarse en el pipeline de CI.
