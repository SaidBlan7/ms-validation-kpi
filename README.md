# Revisión integral - fixes

Archivos modificados tras revisar Validación KPI, KPI Manuales y Aprobación KPI.

## Corrección
En modo `memory`, las administraciones de alcance de KPI Manuales y Aprobación KPI mantenían un estado separado del adapter de autorización. Por ello una revocación podía reflejarse en el endpoint administrativo pero no en el permiso funcional.

Los adapters memory ahora comparten el estado de autorización con la administración y `MicroserviceRuntime` cablea las mismas instancias.

PostgreSQL no requirió cambios.
