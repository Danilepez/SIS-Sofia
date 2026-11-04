CREATE SCHEMA catalogo;
CREATE SCHEMA inventario;
CREATE SCHEMA calidad;
CREATE SCHEMA soporte;
CREATE SCHEMA analitica;
CREATE TABLE catalogo.producto (
 id uuid PRIMARY KEY, sku varchar(40) UNIQUE NOT NULL, nombre varchar(120) NOT NULL,
 familia varchar(60) NOT NULL, unidad varchar(2) NOT NULL DEFAULT 'kg' CHECK(unidad='kg')
);
CREATE TABLE catalogo.centro (id uuid PRIMARY KEY, nombre varchar(100) UNIQUE NOT NULL);
CREATE TABLE inventario.lote (
 id uuid PRIMARY KEY, producto_id uuid NOT NULL REFERENCES catalogo.producto,
 centro_id uuid NOT NULL REFERENCES catalogo.centro, codigo varchar(60) NOT NULL,
 vence date NOT NULL, estado varchar(20) NOT NULL DEFAULT 'RETENIDO' CHECK(estado IN ('RETENIDO','LIBERADO')),
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), UNIQUE(producto_id,centro_id,codigo)
);
CREATE TABLE inventario.movimiento (
 id uuid PRIMARY KEY, lote_id uuid NOT NULL REFERENCES inventario.lote,
 tipo varchar(12) NOT NULL CHECK(tipo IN ('RECEPCION','DESPACHO','MERMA')),
 kg numeric(14,3) NOT NULL CHECK(kg>0), motivo varchar(240) NOT NULL,
 documento varchar(80) NOT NULL, fefo boolean,
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), usuario varchar(80) NOT NULL,
 CHECK((tipo='DESPACHO' AND fefo IS NOT NULL) OR (tipo<>'DESPACHO' AND fefo IS NULL))
);
CREATE TABLE inventario.conteo (
 id uuid PRIMARY KEY, lote_id uuid NOT NULL REFERENCES inventario.lote,
 esperado numeric(14,3) NOT NULL CHECK(esperado>=0), contado numeric(14,3) NOT NULL CHECK(contado>=0),
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), usuario varchar(80) NOT NULL
);
CREATE TABLE calidad.decision (
 id uuid PRIMARY KEY, lote_id uuid NOT NULL REFERENCES inventario.lote,
 estado varchar(20) NOT NULL, motivo varchar(240) NOT NULL,
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), usuario varchar(80) NOT NULL
);
CREATE TABLE soporte.comando (clave uuid PRIMARY KEY, huella text NOT NULL, respuesta text NOT NULL, creado timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE INDEX movimiento_lote_fecha ON inventario.movimiento(lote_id,creado);
CREATE INDEX movimiento_fecha ON inventario.movimiento(creado);
CREATE INDEX conteo_fecha ON inventario.conteo(creado,lote_id);
CREATE INDEX lote_filtro ON inventario.lote(centro_id,producto_id,vence);
CREATE VIEW inventario.saldo AS
 SELECT l.id, coalesce(sum(CASE WHEN m.tipo='RECEPCION' THEN m.kg ELSE -m.kg END),0)::numeric(14,3) AS kg
 FROM inventario.lote l LEFT JOIN inventario.movimiento m ON m.lote_id=l.id GROUP BY l.id;
CREATE VIEW analitica.hecho_movimiento AS
 SELECT m.*, l.producto_id, l.centro_id, l.codigo AS lote, (m.creado AT TIME ZONE 'America/La_Paz')::date AS fecha
 FROM inventario.movimiento m JOIN inventario.lote l ON l.id=m.lote_id;
CREATE VIEW analitica.hecho_conteo AS
 SELECT c.*, l.producto_id, l.centro_id, (c.creado AT TIME ZONE 'America/La_Paz')::date AS fecha,
 abs(c.contado-c.esperado)<=abs(c.esperado)*0.005 AS exacto
 FROM inventario.conteo c JOIN inventario.lote l ON l.id=c.lote_id;
CREATE FUNCTION soporte.inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
 BEGIN RAISE EXCEPTION 'Registro inmutable: utilice un proceso de correccion auditado'; END $$;
CREATE TRIGGER movimiento_inmutable BEFORE UPDATE OR DELETE ON inventario.movimiento FOR EACH ROW EXECUTE FUNCTION soporte.inmutable();
CREATE TRIGGER conteo_inmutable BEFORE UPDATE OR DELETE ON inventario.conteo FOR EACH ROW EXECUTE FUNCTION soporte.inmutable();
CREATE TRIGGER decision_inmutable BEFORE UPDATE OR DELETE ON calidad.decision FOR EACH ROW EXECUTE FUNCTION soporte.inmutable();
