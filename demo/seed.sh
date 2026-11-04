#!/bin/sh
# Espera a que el backend demo aplique las migraciones Flyway y carga los datos solo si la base está vacía.
set -eu
export PGHOST=db PGUSER=sofia PGDATABASE=sofia_demo PGPASSWORD="$DB_PASSWORD"
i=0
until [ "$(psql -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='inventario' AND tablename='ajuste_resolucion'" 2>/dev/null)" = "1" ]; do
  i=$((i+1)); [ "$i" -gt 90 ] && { echo "El backend demo no aplicó las migraciones a tiempo"; exit 1; }; sleep 2
done
if [ "$(psql -tAc 'SELECT count(*) FROM inventario.lote')" = "0" ]; then
  python3 -I /demo/demo_seed.py | psql -q -v ON_ERROR_STOP=1
  echo "Datos de demostración cargados en sofia_demo."
else
  echo "sofia_demo ya tiene datos; se conservan."
fi
