# API v1

Mismo origen en el frontend. Backend Docker local: http://localhost:8091. Autenticación Basic con usuarios del README. GET /actuator/health es público. POST requiere JSON y `X-Requested-With: SIS-SOFIA`; los comandos requieren `Idempotency-Key: <UUID>`. Catálogos no son idempotentes: la restricción única protege contra duplicados.

| Método y ruta /api/v1 | Rol | Contenido |
|---|---|---|
| GET /me | Todos | Usuario y roles |
| GET /catalogo | Todos | Productos y centros |
| POST /productos | Operador | sku, nombre, familia |
| POST /centros | Operador | nombre |
| GET /lotes | Todos | Saldos y atributos |
| POST /recepciones | Operador | producto UUID, centro UUID, codigo, vence YYYY-MM-DD, kg, documento |
| POST /decisiones | Calidad | lote UUID, estado LIBERADO/RETENIDO, motivo |
| POST /despachos | Operador | producto UUID, centro UUID, kg, vidaMinima entero 1..3650, documento |
| POST /mermas | Calidad | lote UUID, kg, motivo, documento |
| POST /conteos | Operador | lote UUID, kg (acepta cero) |
| GET /lotes/{id}/trazabilidad | Todos | Lote, movimientos, decisiones, conteos |
| GET /dashboard | Todos | desde, hasta, centro?, producto?, anio=1..3 |

Éxito 200; validación 400; no autenticado 401; rol insuficiente 403; conflicto de negocio/idempotencia/unicidad 409. Los errores de validación y dominio usan ProblemDetail. La API todavía devuelve 400 para lote inexistente; homologar a 404 forma parte de P1. Errores inesperados deben revisarse en los logs; no se expone el SQL al navegador.

Respuesta de dashboard: actual/anterior con merma, fefo, exactitud y numeradores/denominadores; metas; tendencia; causas; productos; riesgos; stock; fechas; actualizado; msServidor. null significa no evaluable. Los porcentajes tienen cuatro decimales en API y dos en UI. El campo msServidor mide solo el procesamiento del controlador, no autenticación, red ni React.

No hay endpoints UPDATE/DELETE de hechos. El documento de respaldo es texto referencial; aún no se adjunta ni valida un archivo. OpenAPI tipado y pruebas contractuales generadas son tareas P1; esta tabla es el contrato manual v0.1.
