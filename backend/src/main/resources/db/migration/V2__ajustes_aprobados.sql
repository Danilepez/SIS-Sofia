-- Contramovimientos con doble aprobación: la solicitud y su resolución son hechos separados e inmutables.
-- El movimiento original nunca se modifica; las vistas exponen su cantidad efectiva.
CREATE TABLE inventario.ajuste (
 id uuid PRIMARY KEY, movimiento_id uuid NOT NULL REFERENCES inventario.movimiento,
 kg_original numeric(14,3) NOT NULL CHECK(kg_original>0), kg_correcto numeric(14,3) NOT NULL CHECK(kg_correcto>=0),
 motivo varchar(240) NOT NULL, documento varchar(80) NOT NULL,
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), usuario varchar(80) NOT NULL,
 CHECK(kg_correcto<>kg_original)
);
CREATE TABLE inventario.ajuste_resolucion (
 ajuste_id uuid PRIMARY KEY REFERENCES inventario.ajuste, aprobado boolean NOT NULL, comentario varchar(240) NOT NULL,
 creado timestamptz NOT NULL DEFAULT clock_timestamp(), usuario varchar(80) NOT NULL
);
CREATE INDEX ajuste_movimiento ON inventario.ajuste(movimiento_id);
CREATE TRIGGER ajuste_inmutable BEFORE UPDATE OR DELETE ON inventario.ajuste FOR EACH ROW EXECUTE FUNCTION soporte.inmutable();
CREATE TRIGGER ajuste_resolucion_inmutable BEFORE UPDATE OR DELETE ON inventario.ajuste_resolucion FOR EACH ROW EXECUTE FUNCTION soporte.inmutable();
-- Cantidad efectiva: la última corrección aprobada reemplaza el valor original; sin aprobación rige el original.
CREATE VIEW inventario.movimiento_efectivo AS
 SELECT m.*, coalesce(a.kg_correcto,m.kg)::numeric(14,3) AS kg_efectivo, a.id AS ajuste_id
 FROM inventario.movimiento m LEFT JOIN LATERAL (
  SELECT x.id,x.kg_correcto FROM inventario.ajuste x JOIN inventario.ajuste_resolucion r ON r.ajuste_id=x.id AND r.aprobado
  WHERE x.movimiento_id=m.id ORDER BY r.creado DESC,x.id DESC LIMIT 1) a ON true;
CREATE OR REPLACE VIEW inventario.saldo AS
 SELECT l.id, coalesce(sum(CASE WHEN m.tipo='RECEPCION' THEN m.kg_efectivo ELSE -m.kg_efectivo END),0)::numeric(14,3) AS kg
 FROM inventario.lote l LEFT JOIN inventario.movimiento_efectivo m ON m.lote_id=l.id GROUP BY l.id;
-- Re-expresa el hecho en su fecha original con la cantidad corregida; una línea anulada (0 kg) deja de contar.
DROP VIEW analitica.hecho_movimiento;
CREATE VIEW analitica.hecho_movimiento AS
 SELECT m.id, m.lote_id, m.tipo, m.kg_efectivo AS kg, m.motivo, m.documento, m.fefo, m.creado, m.usuario,
 m.kg AS kg_registrado, m.ajuste_id, l.producto_id, l.centro_id, l.codigo AS lote, (m.creado AT TIME ZONE 'America/La_Paz')::date AS fecha
 FROM inventario.movimiento_efectivo m JOIN inventario.lote l ON l.id=m.lote_id WHERE m.kg_efectivo>0;
