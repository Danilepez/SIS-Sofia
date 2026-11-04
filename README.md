# SIS Sofía — Proyecto 3

MVP funcional del dashboard de inventarios: React 18 + TypeScript + Vite + React Query; Java 21 + Spring Boot 3 + Spring Data JPA/JDBC; PostgreSQL 16 + Flyway. Los registros se guardan en PostgreSQL. **La base de uso normal comienza vacía**, sin indicadores prefabricados.

## Arranque en macOS / Linux

Con Docker Desktop iniciado, en una terminal en esta carpeta:

```bash
./scripts/init.sh
docker compose up -d --build
```

Si ya tenía un `.env` de la v0.1, vuelva a ejecutar `init.sh` (o `init.ps1`): conserva las claves existentes y agrega `SUPERVISOR_PASSWORD`.

## Arranque en Windows

Requisito: Docker Desktop iniciado con contenedores Linux. Abra PowerShell en esta carpeta:

```powershell
.\scripts\init.ps1
docker compose up -d --build
docker compose ps
```

Abra http://localhost:8088. La primera compilación descarga dependencias y puede tardar varios minutos. Si la pantalla abre antes que el backend, espere a que http://localhost:8091/actuator/health devuelva `UP` y vuelva a ingresar.

Las contraseñas aleatorias se generan una vez en `.env`, no se incluyen en Git ni en el ZIP. Consulte ese archivo localmente. Usuarios:

| Usuario | Clave en .env | Permisos |
|---|---|---|
| operador | OPERATOR_PASSWORD | Catálogos, recepción, despacho y conteos |
| calidad | QUALITY_PASSWORD | Liberación, retención y mermas |
| consulta | READER_PASSWORD | Dashboard, saldos y trazabilidad |
| supervisor | SUPERVISOR_PASSWORD | Aprobar o rechazar ajustes solicitados por otro usuario |

Los puertos publicados están limitados a 127.0.0.1. Esta autenticación local es transitoria; para uso en red se requiere HTTPS e IdP corporativo. No copie `.env` a capturas o repositorios.

## Primer flujo con registros propios

1. Ingrese como operador. En **Operaciones**, cree un centro y un producto; toda cantidad se expresa en kg.
2. Registre una recepción con lote único por producto/centro, fecha futura y documento de respaldo. El lote queda RETENIDO.
3. Salga e ingrese como calidad. Revise el respaldo y registre la liberación del lote con motivo.
4. Ingrese como operador. Registre un despacho con vida útil mínima. El servidor elige y distribuye las cantidades según FEFO, sin permitir sobreventa.
5. Registre un conteo físico. El conteo conserva el saldo esperado; no ajusta existencias automáticamente.
6. Si corresponde, calidad registra la merma real y su motivo. La cantidad se descuenta del inventario.
7. Si un registro tiene un error de cantidad, en **Operaciones → Ajuste de movimiento** solicite la cantidad correcta con motivo y respaldo. Ingrese como supervisor para aprobarla o rechazarla. El registro original no se borra.
8. Consulte el dashboard. Filtre por fecha, centro y producto. Las barras de producto y de fecha cambian los filtros globales. En Trazabilidad puede descargar los registros de un lote como JSON.

Un denominador cero se muestra como **Sin datos**, no como 0 % ni 100 %. Los datos de las pruebas están en `sofia_test`, separados de `sofia`.

## Demostración con datos simulados

Para exponer el dashboard con contenido sin tocar la base normal, después de `init`:

```bash
docker compose --profile demo up -d --build
```

Compose crea la base `sofia_demo` (servicio `demo-db`), el backend demo aplica las migraciones Flyway y `demo-seed` carga 90 días de operación **simulada**: 3 centros, 10 productos, recepciones, liberaciones, despachos FEFO, mermas con motivo, conteos semanales, un ajuste aprobado y otro pendiente. Las fechas se calculan desde el día de la carga, y la semilla es fija, así que cada equipo obtiene el mismo conjunto. Volver a ejecutar el comando no duplica datos.

La demo queda en http://localhost:8089 con las credenciales del `.env` local, y la interfaz muestra la etiqueta **Datos de demostración**. Todos los registros llevan el prefijo `DEMO`; son ficticios y no representan resultados de la empresa. Para regenerarla, borre la base y repita el comando: `docker compose exec db dropdb -U sofia sofia_demo`, o use `./scripts/demo.sh --reset` en macOS/Linux. El generador es [demo/demo_seed.py](demo/demo_seed.py) y respeta las mismas reglas que la API: retención y liberación, FEFO con vida mínima, merma no mayor al saldo y conteos con el saldo esperado.

## Conexión con DBeaver

Nueva conexión → PostgreSQL → host `localhost`, puerto `5433`, base `sofia`, usuario `sofia`, contraseña igual a `DB_PASSWORD` del `.env`. JDBC: `jdbc:postgresql://localhost:5433/sofia`. No se necesita SQLite ni una base en memoria. Véase [DBEAVER.md](docs/DBEAVER.md).

## Comprobación y desarrollo

```powershell
docker compose up -d db
docker compose exec -T db createdb -U sofia sofia_test
.\scripts\test.ps1
```

`createdb` solo se ejecuta una vez; si la base ya existe, continúe. Las pruebas requieren un nombre de base terminado en `_test`, usan PostgreSQL real y revierten sus transacciones. Para desarrollo nativo se necesitan Java 21, Maven 3.9 y Node 22:

```powershell
.\scripts\dev-backend.ps1
# En otra terminal:
cd frontend
npm ci
npm run dev
```

Frontend de desarrollo: http://127.0.0.1:5173; backend nativo: 8080. Docker mantiene el backend en 8091. No ejecute tests sobre `sofia`.

## Estado de entrega

Implementado: persistencia, migración, roles locales, tres KPIs con metas y período anterior, filtros cruzados, estados vacíos, FEFO, idempotencia transaccional, conteos, mermas, trazabilidad local, Docker y CI.

Pendiente: Keycloak/OIDC/MFA, Spring Modulith y reglas automáticas de encapsulación, RabbitMQ/outbox, sensores y métricas térmicas, ERP real, órdenes de compra, reservas, pedidos y entregas/OTIF, GS1, múltiples ubicaciones, genealogía, ajustes auditados, observabilidad y carga a escala de producción. El informe describe el diseño completo y distingue estas brechas. No es un ERP productivo ni una certificación de inocuidad.

- [Continuar el proyecto](docs/CONTINUAR.md)
- [Arquitectura y métricas](docs/ARQUITECTURA.md)
- [API](docs/API.md)
- [Validación y rendimiento](docs/VALIDACION.md)

## GitHub

La carpeta es un repositorio local preparado para su primera revisión. No se ha publicado ningún remoto. Revise los cambios y configure su propio repositorio:

```powershell
git status
git add .
git diff --cached --stat
git commit -m "feat: MVP dashboard SIS Sofia"
git remote add origin URL_DE_SU_REPOSITORIO
git push -u origin main
```

Antes de confirmar, compruebe que `.env` no aparece en `git status`. Las contraseñas del workflow CI son exclusivas del contenedor efímero de prueba; no son las claves locales.

## Detener y respaldar

`docker compose down` detiene los servicios y conserva el volumen. **No use `down -v` si desea conservar los registros**. Para respaldo binario sin corrupción por la redirección de PowerShell:

```powershell
New-Item -ItemType Directory -Force backups
docker compose exec -T db pg_dump -U sofia -d sofia -Fc -f /tmp/sofia.dump
docker compose cp db:/tmp/sofia.dump backups/sofia.dump
```

La restauración debe ensayarse en otra base antes de utilizar datos reales. Al cambiar DB_PASSWORD en `.env`, PostgreSQL no cambia automáticamente la clave de un volumen existente: gestione la rotación con SQL y actualice la aplicación de forma coordinada.
