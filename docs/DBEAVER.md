# Acceso a PostgreSQL con DBeaver

La conexión normal es localhost:5433 / base sofia / usuario sofia. Obtenga la contraseña de DB_PASSWORD en el archivo .env local. El puerto interno del contenedor es 5432; el del host es 5433. DBeaver puede descargar el driver PostgreSQL al crear la conexión si aún no está instalado.

1. Inicie Docker Desktop y ejecute `docker compose up -d` en la raíz del proyecto.
2. En DBeaver elija PostgreSQL y complete los campos anteriores. Use “Test Connection”.
3. Expanda Schemas: catalogo, inventario, calidad, soporte y analitica. Active “Show all schemas” si es necesario.
4. Inspeccione tablas y vistas con consultas de lectura. El usuario local posee privilegios de administración; no es el perfil que se debe dar a analistas en producción.
5. Cree los movimientos desde la aplicación. Insertarlos manualmente puede eludir FEFO, idempotencia y controles de saldo.

```sql
SELECT * FROM catalogo.producto ORDER BY sku;
SELECT l.codigo, l.estado, l.vence, s.kg
FROM inventario.lote l JOIN inventario.saldo s ON s.id=l.id;
SELECT fecha,tipo,sum(kg) kg
FROM analitica.hecho_movimiento GROUP BY fecha,tipo ORDER BY fecha,tipo;
SELECT lote_id,esperado,contado,exacto
FROM analitica.hecho_conteo ORDER BY creado DESC;
SELECT * FROM public.flyway_schema_history ORDER BY installed_rank;
```

Para conciliar EST-03, aplique exactamente el mismo rango, centro y producto del dashboard, y sume los kg de MERMA y RECEPCION por separado. Calcule `100 * merma / NULLIF(recepcion,0)`; no promedie tasas de centros. La fecha de negocio usa America/La_Paz.

La base `sofia_test` solo contiene evidencia técnica. Las muestras QA no son registros empresariales. Crear un usuario analítico de solo lectura, restringir vistas por centro y configurar TLS son tareas previas al despliegue en red.
