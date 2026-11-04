# Retomar SIS Sofía

## Punto de partida

Lea README.md, ARQUITECTURA.md, VALIDACION.md y el informe Proyecto3_Dashboard_SIS_Sofia.docx. No reemplace el stack por otro: React/TypeScript, Java 21/Spring Boot y PostgreSQL 16. La v0.1 funciona con base vacía y operaciones registradas. Los documentos de los parciales describen más alcance del que esta primera versión implementa.

## Orden propuesto del trabajo

| Prioridad | Entrega | Criterio de aceptación |
|---|---|---|
| P0 académico | Cargar un conjunto autorizado y documentar su procedencia | Conciliar cantidades contra respaldo y presentar sin cifras inventadas como empresariales |
| P0 académico | Medir interacción en equipo de exposición y probar usuarios | p95 menor de 2 s, filtros y teclado; registrar máquina, volumen y concurrencia |
| ~~P0 operativo~~ | ~~Ajustes/contramovimientos con doble aprobación~~ **Implementado en V2** | Error corregible sin borrar hechos ni generar merma falsa. Pendiente: corrección de atributos de lote (código/vencimiento) y separación de personas reales (hoy por cuenta) |
| P0 producción | Keycloak OIDC/PKCE, MFA, HTTPS y cuentas nominales | Sesión expirada/revocada bloqueada; Operador nunca libera; no secretos en Git |
| P0 producción | Roles DB, backups y restauración ensayada | App sin DDL; BI solo lectura; recuperación con evidencia, no RTO/RPO supuestos |
| P1 | Extraer calidad, ventas y compras a módulos; Spring Modulith | Prueba automática impide importar internal de otro módulo |
| P1 | Ubicaciones, transferencias y reservas | Lote-ubicación como unidad; sin doble conteo de recepciones internas |
| P1 | Pedidos/entregas y OTIF | Pedidos vencidos no entregados incluidos como fallo; cantidades parciales y fechas auditadas |
| P1 | IoT, cámara/sensor y conformidad térmica | Deduplicación; intervalos por cámara; huecos de telemetría visibles; cuarentena no automática al liberar |
| P1 | Outbox/RabbitMQ y ERP real | Commit único + evento; reintentos y DLQ; consumidor idempotente |
| P1 | OpenAPI y cliente tipado; validación homogénea | 400/401/403/404/409 documentados y verificados |
| P1 | Bloqueos más finos, índices y carga | Concurrencia sin sobreventa; EXPLAIN; p95 y p99 con volumen representativo |
| P2 | GS1, genealogía y retiro | Trazabilidad origen/destino completa; simulacro cronometrado |
| P2 | Accesibilidad y UX con usuarios reales | Lectura de KPIs en menos de 5 s; navegación por teclado y lector; contraste WCAG AA verificado |
| P2 | CI ampliada y despliegue remoto | Testcontainers, Playwright reproducible, análisis de dependencias y rollback |

## Especificación de los KPIs pendientes

- TAC-03a: cámara-horas en rango / cámara-horas monitoreadas. Integrar intervalos, no contar lecturas como si siempre tuvieran idéntica frecuencia. Definir límite de hueco, no interpolar desconexiones como conformes. Metas 98,5/99/99,5 %. Calidad valida rangos por familia.
- TEC-02: tiempo de telemetría recibida / tiempo esperado. Publicar junto a TAC-03a; una conformidad alta con cobertura baja no es una mejora demostrada.
- TAC-01a: pedidos completos y entregados hasta fecha prometida / pedidos con compromiso vencido en el período, aplicando cancelaciones auditadas. Metas 89/93/95 %. Especificar cortes y parcialidades con Comercial.
- TAC-09: tiempo total de simulacro desde activación hasta informe completo, no tiempo SQL de consulta. Metas ≤8/4/2 horas.
- Valoración y beneficios: integrar costos reales con ERP. No multiplicar automáticamente stock por USD 2,50/kg ni recalcular VAN a partir de pruebas.

## Datos por solicitar

Catálogo SKU y unidades; CD/ubicaciones y permisos; saldos iniciales conciliados; lotes y vencimientos; motivos estandarizados; calendario de conteos; vida mínima por cliente; políticas de calidad; pedidos/entregas; sensores y tasas de muestreo; contratos API del ERP. Los rangos térmicos del caso son hipótesis de diseño pendientes de Calidad, no prescripciones sanitarias confirmadas.

## Prompt de continuidad

“Continúa el repositorio SIS Sofía en esta carpeta. Lee primero docs/CONTINUAR.md, docs/VALIDACION.md y el informe. Mantén React, Spring Boot y PostgreSQL y no alteres V1 aplicada: crea V2 para cambios. Implementa la siguiente tarea prioritaria elegida, conserva los datos, agrega pruebas de negocio sobre sofia_test y actualiza el informe sin presentar funciones pendientes como realizadas. No publiques credenciales ni subas el repositorio sin autorización.”
