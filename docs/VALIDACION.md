# Validación de la entrega v0.1

Fecha de evidencia: 3 de noviembre de 2026, zona America/La_Paz.

## Resultado observado

- Backend: 13 pruebas JUnit aprobadas, 0 fallos, 0 errores, 0 omitidas, con PostgreSQL 16.13 real. Java 21.0.11 y Maven 3.9.16 en Windows.
- Frontend: TypeScript y Vite compilan; npm audit informa 0 vulnerabilidades en el árbol instalado. No se realizó una auditoría de seguridad integral del backend o de las imágenes.
- Navegador: Microsoft Edge headless mediante Playwright; escritorio 1440 × 1050 y móvil 390 × 844. Acceso, conteos por formulario persistidos, lectura de tarjetas, filtro cruzado, trazabilidad, logout y nuevo login. Sin errores JavaScript de página en ese recorrido.
- Concurrencia HTTP: dos despachos de 8 kg sobre un saldo de 10 kg; exactamente uno fue aceptado y el otro rechazado. No se realizó una prueba de carga multiusuario prolongada.
- Docker: frontend, API y PostgreSQL arrancan. La base normal sofia se comprobó vacía; datos QA solo en sofia_test.
- DOCX: 18 páginas renderizadas mediante Word a PDF y Poppler a PNG, revisadas visualmente. El renderer estándar se intentó; no había LibreOffice en el runtime de Windows, por lo que se utilizó Word instalado para la exportación. Los PDF y PNG de páginas son intermediarios de revisión, no entregables.

## Medición del dashboard

30 solicitudes HTTP secuenciales locales, autenticadas, posteriores al calentamiento y sobre registros QA pequeños. p50 = 109.3 ms; p95 = 144.2 ms; máximo = 157.6 ms. Se utilizó el percentil empírico por rango ordenado. Las muestras incluyen autenticación y HTTP; no incluyen el renderizado React completo ni representan escala empresarial.

El campo msServidor del tablero mide un tramo menor y no debe compararse solo con los dos segundos de la rúbrica. Pendiente: clic a visual disponible, volumen representativo, concurrencia, p95/p99 y hardware descrito para la defensa.

## Reproducir integración

```powershell
docker compose up -d db
docker compose exec -T db createdb -U sofia sofia_test
.\scripts\test.ps1
```

Si la base de pruebas ya existe, omita createdb. La suite exige sufijo _test y revierte sus escrituras. Las muestras de navegador persisten en sofia_test para inspección.

## Reproducir navegador en Windows con Edge

1. Ejecute frontend/npm ci si no están instaladas las dependencias.
2. Cierre cualquier backend nativo anterior del proyecto que ocupe 8080.
3. En una terminal ejecute `.\scripts\dev-test-backend.ps1` (usa sofia_test y puerto 8080).
4. En otra terminal ejecute `cd frontend` y `npm run dev` (puerto 5173).
5. Desde la raíz: `node scripts/e2e.cjs`.

El script comprueba el indicador entornoPruebas de /me antes de escribir y aborta si el backend no utiliza una base terminada en _test. Crea un nuevo centro y productos identificados QA, por lo que repetirlo agrega registros de prueba; no limpia ni borra datos existentes. Produce capturas y JSON en work/, excluido de Git. Requiere Microsoft Edge instalado; adaptar channel si se utiliza otro navegador.

## Casos JUnit

Flujo y fórmulas; retención; idempotencia y conflicto de payload; insuficiencia de stock; tolerancia y último conteo; denominadores cero; roles; validación de cantidad y vencimiento; vida mínima; merma excesiva; filtros y rango; función de tasa; autenticación Basic real correcta e incorrecta. Fuente: backend/src/test/java/bo/sofia/sis/InventoryIntegrationTest.java.

## Pendientes expresos

Testcontainers; ejecución E2E en CI; lecturas térmicas; OTIF; pruebas de caída/recuperación; restauración de backup; carga de producción; accesibilidad con lector de pantalla; comprensión en menos de 5 segundos. La prueba de exactitud usa lotes-centro como ubicación lógica y no acredita conteos multiubicación.

## Repetición final del script entregado

El script portable scripts/e2e.cjs también se ejecutó correctamente tras verificar entornoPruebas. La segunda corrida registró p50 de 100,6 ms, p95 de 111,5 ms y máximo de 111,8 ms; se conserva en evidencias/browser-repeat.json. La medición del informe corresponde a la primera corrida, no a una mezcla de muestras. El frontend Docker en localhost:8088 se verificó por separado con la base normal vacía: tres tarjetas Sin datos y ningún error JavaScript.
