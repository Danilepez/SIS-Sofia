#!/usr/bin/env python3
"""Genera SQL con 90 días de operación SIMULADA para la base sofia_demo.

Los datos son ficticios y se marcan DEMO (SKU, documentos, motivos). La simulación respeta las
mismas reglas que la API: lote retenido hasta liberación, despacho FEFO con vida mínima,
merma nunca mayor al saldo, conteo con el saldo esperado del momento. Nunca usar en sofia.
Uso: lo ejecuta el servicio demo-seed de compose; manual: python3 -I demo/demo_seed.py > demo.sql   (semilla fija: siempre el mismo conjunto)
"""
import random, uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

rng = random.Random(20261104)
BO = timezone(timedelta(hours=-4))
NOW = datetime.now(BO)
TODAY = NOW.date()
DAYS = 90

def uid(): return str(uuid.UUID(int=rng.getrandbits(128), version=4))
def kg(x): return Decimal(x).quantize(Decimal('0.001'), ROUND_HALF_UP)
def q(s): return "'" + str(s).replace("'", "''") + "'"
def ts(d, h, m=0): return datetime(d.year, d.month, d.day, h, m, tzinfo=BO)

# (sku, nombre, familia, vida útil días, vida mínima de despacho, demanda kg/día base)
PRODUCTS = [
 ('DEMO-PF-001', 'Pollo entero fresco', 'Fresco', 12, 3, 420),
 ('DEMO-PF-002', 'Pechuga de pollo', 'Fresco', 10, 3, 260),
 ('DEMO-PF-003', 'Alitas de pollo', 'Fresco', 10, 2, 140),
 ('DEMO-PF-004', 'Muslo de pollo', 'Fresco', 10, 2, 180),
 ('DEMO-CF-001', 'Chuleta de cerdo', 'Fresco', 8, 2, 90),
 ('DEMO-PR-001', 'Nuggets de pollo', 'Procesado', 90, 20, 110),
 ('DEMO-PR-002', 'Hamburguesa de pollo', 'Procesado', 90, 20, 80),
 ('DEMO-EM-001', 'Salchicha Viena', 'Embutido', 45, 10, 120),
 ('DEMO-EM-002', 'Mortadela', 'Embutido', 40, 10, 70),
 ('DEMO-EM-003', 'Jamón de cerdo', 'Embutido', 35, 10, 60),
]
# (nombre, escala de demanda, productos que no maneja, sesgo de merma)
CENTERS = [
 ('DEMO CD El Alto', 1.0, set(), 1.0),
 ('DEMO CD Santa Cruz', 1.3, {'DEMO-EM-003'}, 1.45),  # clima cálido: más pérdidas de frío
 ('DEMO CD Cochabamba', 0.7, {'DEMO-CF-001', 'DEMO-PR-002'}, 0.8),
]
LOSS = [('Ruptura de cadena de frío', 0.34), ('Daño de empaque', 0.26), ('Error de manipulación', 0.16),
        ('Devolución de cliente', 0.14), ('Contaminación cruzada detectada', 0.10)]

rows = {k: [] for k in ('producto', 'centro', 'lote', 'movimiento', 'decision', 'conteo', 'ajuste', 'resolucion')}
seq = {'gr': 0, 'od': 0, 'mm': 0}
def doc(kind):
    seq[kind] += 1
    return f"DEMO-{kind.upper()}-{seq[kind]:05d}"
def move(lot, tipo, amount, motivo, documento, fefo, when, user):
    mid = uid()
    rows['movimiento'].append((mid, lot['id'], tipo, kg(amount), motivo, documento, fefo, when, user))
    return mid

products = []
for sku, name, fam, life, minlife, demand in PRODUCTS:
    p = {'id': uid(), 'sku': sku, 'life': life, 'minlife': minlife, 'demand': demand}
    products.append(p); rows['producto'].append((p['id'], sku, name, fam))
centers = []
for name, scale, skip, lossbias in CENTERS:
    c = {'id': uid(), 'scale': scale, 'skip': skip, 'loss': lossbias}
    centers.append(c); rows['centro'].append((c['id'], name))

lots = []  # todos los lotes, con saldo simulado
for c in centers:
    for p in products:
        if p['sku'] in c['skip']: continue
        demand = p['demand'] * c['scale']
        fresh = p['life'] <= 12
        mine = []
        next_receipt = TODAY - timedelta(days=DAYS + rng.randint(0, 2))
        for offset in range(DAYS, -1, -1):
            day = TODAY - timedelta(days=offset)
            if day >= next_receipt:
                # Recepción: cubre los días hasta la próxima entrega con un pequeño margen.
                gap = rng.randint(2, 3) if fresh else rng.randint(6, 9)
                amount = demand * gap * rng.uniform(0.9, 1.0)
                when = ts(day, rng.randint(6, 8), rng.randint(0, 59))
                if when > NOW: break
                lot = {'id': uid(), 'code': f"L{day:%y%m%d}-{p['sku'][-6:]}-{rng.randint(10, 99)}", 'vence': day + timedelta(days=p['life']),
                       'saldo': kg(amount), 'creado': when, 'estado': 'RETENIDO', 'p': p, 'c': c}
                move(lot, 'RECEPCION', amount, 'Recepción retenida para Calidad', doc('gr'), None, when, 'operador')
                # Calidad libera en horas; algunos lotes recientes siguen retenidos.
                hold = offset <= 1 and rng.random() < 0.5
                if not hold:
                    released = when + timedelta(hours=rng.randint(2, 6))
                    if released <= NOW:
                        lot['estado'] = 'LIBERADO'; lot['liberado'] = released
                        rows['decision'].append((uid(), lot['id'], 'LIBERADO', 'DEMO: análisis microbiológico y temperatura conformes', released, 'calidad'))
                mine.append(lot); lots.append(lot)
                next_receipt = day + timedelta(days=gap)
            # Despacho FEFO: lotes liberados, con vida mínima, orden por vencimiento y fecha de ingreso.
            want = kg(demand * rng.uniform(0.75, 1.2) * (0.6 if day.weekday() == 6 else 1))
            when = ts(day, rng.randint(10, 16), rng.randint(0, 59))
            if when <= NOW:
                eligible = sorted([l for l in mine if l['estado'] == 'LIBERADO' and l.get('liberado', when) <= when and l['vence'] >= day + timedelta(days=p['minlife']) and l['saldo'] > 0],
                                  key=lambda l: (l['vence'], l['creado'], l['id']))
                available = sum((l['saldo'] for l in eligible), Decimal(0))
                if available >= want and want > 0:  # la API rechaza el despacho completo si no alcanza
                    document = doc('od')
                    for l in eligible:
                        if want == 0: break
                        take = min(want, l['saldo'])
                        move(l, 'DESPACHO', take, f"FEFO automático; vida mínima {p['minlife']} días", document, True, when + timedelta(seconds=len(rows['movimiento'])), 'operador')
                        l['saldo'] -= take; want -= take
            # Merma: pérdidas puntuales con motivo; Santa Cruz pierde más por cadena de frío.
            when = ts(day, rng.randint(15, 18), rng.randint(0, 59))
            if when <= NOW and rng.random() < (0.22 if fresh else 0.08) * c['loss']:
                stocked = [l for l in mine if l['saldo'] > 5 and l['creado'] < when]
                if stocked:
                    l = rng.choice(stocked)
                    amount = min(l['saldo'], kg(float(l['saldo']) * rng.uniform(0.02, 0.07) * c['loss']))
                    reason = rng.choices([r for r, _ in LOSS], [w for _, w in LOSS])[0]
                    move(l, 'MERMA', amount, f"DEMO: {reason}", doc('mm'), None, when, 'calidad'); l['saldo'] -= amount
            # Vencidos: la mayoría se da de baja el día de vencimiento; algunos quedan para la tabla de riesgos.
            for l in mine:
                if l['vence'] == day and l['saldo'] > 0 and offset > 3 and rng.random() < 0.96:
                    when = ts(day, 19, rng.randint(0, 59))
                    if when <= NOW:
                        move(l, 'MERMA', l['saldo'], 'DEMO: Vencimiento sin despacho', doc('mm'), None, when, 'calidad'); l['saldo'] = Decimal(0)
            # Conteo físico semanal (y un último conteo reciente); la mayoría coincide dentro de ±0,5 %.
            if day.weekday() == 4 or offset == 1:
                when = ts(day, 20, rng.randint(0, 59))
                if when <= NOW:
                    for l in [l for l in mine if l['saldo'] > 0 and l['creado'] < when]:
                        exact = rng.random() < (0.96 if c['loss'] <= 1 else 0.88)
                        counted = l['saldo'] if exact else kg(float(l['saldo']) * rng.choice([rng.uniform(0.95, 0.99), rng.uniform(1.01, 1.04)]))
                        rows['conteo'].append((uid(), l['id'], l['saldo'], counted, when, 'operador'))

# Ajustes: uno aprobado (merma digitada de más) y uno pendiente (recepción con error) para mostrar el flujo.
mermas = [m for m in rows['movimiento'] if m[2] == 'MERMA' and m[3] > 20 and not m[4].endswith('Vencimiento sin despacho')]
m = mermas[len(mermas) // 2]
aid = uid(); at = m[7] + timedelta(days=1)
rows['ajuste'].append((aid, m[0], m[3], kg(m[3] - 10), 'DEMO: se digitó 10 kg de más', 'DEMO-AJ-00001', at, 'calidad'))
rows['resolucion'].append((aid, True, 'DEMO: verificado contra acta de baja', at + timedelta(hours=3), 'supervisor'))
recent = [l for l in lots if l['saldo'] > 30 and l['creado'] > NOW - timedelta(days=3)]
if recent:
    l = recent[0]; r = next(x for x in rows['movimiento'] if x[1] == l['id'] and x[2] == 'RECEPCION')
    rows['ajuste'].append((uid(), r[0], r[3], kg(r[3] - 15), 'DEMO: guía de remisión indica 15 kg menos', 'DEMO-AJ-00002', NOW - timedelta(hours=2), 'operador'))

def emit(table, cols, data, fmt):
    for i in range(0, len(data), 500):
        print(f"INSERT INTO {table}({cols}) VALUES")
        print(",\n".join("(" + ",".join(fmt(r)) + ")" for r in data[i:i + 500]) + ";")
def lit(v):
    if v is None: return 'NULL'
    if isinstance(v, bool): return 'true' if v else 'false'
    if isinstance(v, Decimal): return str(v)
    if isinstance(v, datetime): return q(v.isoformat())
    if isinstance(v, date): return q(v.isoformat())
    return q(v)

print("-- SIS Sofía · conjunto DEMO generado por scripts/demo_seed.py. Datos simulados, no empresariales.")
print("BEGIN;")
print("DO $$ BEGIN IF current_database() NOT LIKE '%\\_demo' THEN RAISE EXCEPTION 'Solo para una base terminada en _demo'; END IF;")
print(" IF EXISTS (SELECT 1 FROM inventario.lote) THEN RAISE EXCEPTION 'La base demo ya tiene datos: use --reset'; END IF; END $$;")
emit('catalogo.producto', 'id,sku,nombre,familia', rows['producto'], lambda r: map(lit, r))
emit('catalogo.centro', 'id,nombre', rows['centro'], lambda r: map(lit, r))
emit('inventario.lote', 'id,producto_id,centro_id,codigo,vence,estado,creado', lots,
     lambda l: map(lit, (l['id'], l['p']['id'], l['c']['id'], l['code'], l['vence'], l['estado'], l['creado'])))
emit('inventario.movimiento', 'id,lote_id,tipo,kg,motivo,documento,fefo,creado,usuario', rows['movimiento'], lambda r: map(lit, r))
emit('calidad.decision', 'id,lote_id,estado,motivo,creado,usuario', rows['decision'], lambda r: map(lit, r))
emit('inventario.conteo', 'id,lote_id,esperado,contado,creado,usuario', rows['conteo'], lambda r: map(lit, r))
emit('inventario.ajuste', 'id,movimiento_id,kg_original,kg_correcto,motivo,documento,creado,usuario', rows['ajuste'], lambda r: map(lit, r))
emit('inventario.ajuste_resolucion', 'ajuste_id,aprobado,comentario,creado,usuario', rows['resolucion'], lambda r: map(lit, r))
print("COMMIT;")
