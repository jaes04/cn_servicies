#!/usr/bin/env bash
#
# Las migraciones, en orden y con el rol que toca.
#
#     bash scripts/migrar.sh
#
# Funciona igual con la base en Docker (el servidor) que instalada en la maquina
# (desarrollo): lo decide solo. Ver `pg` en comun.sh.
#
# VA DESPUES DE QUE LA APLICACION HAYA ARRANCADO AL MENOS UNA VEZ. Todas tocan
# tablas, y las tablas las crea Hibernate al arrancar. Antes, fallan con
# "relation does not exist" — y el script lo comprueba antes de empezar.
#
# SE PASA DESPUES DE CADA DESPLIEGUE, no solo el primero. Son idempotentes, y
# una version que anade una entidad raiz trae su migracion de RLS: sin pasarla,
# esa tabla nace fuera del aislamiento entre clubes.
#
# EL ORDEN IMPORTA: la 0.6 crea `app_club_visible(uuid)`, de la que dependen las
# policies de todas las demas.
#
# EL ROL IMPORTA MAS: la 0.6 crea el rol de la aplicacion, asi que necesita al
# superusuario. El resto las puede `cn_app`, que es dueno de sus tablas.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${MIGRATION_USER:?falta MIGRATION_USER en el .env}"
: "${MIGRATION_PASSWORD:?falta MIGRATION_PASSWORD en el .env}"

CLAVE_APP="$PGPASSWORD"

COMO_SUPERUSUARIO=(
    "0.6-row-level-security.sql"
)

# Las de RLS primero y las de limpieza al final: las segundas borran
# restricciones que Hibernate genero de mas.
COMO_APLICACION=(
    "1.1-seasons-rls.sql"
    "1.2-training-groups-rls.sql"
    "2.2-training-sessions-rls.sql"
    "2.2-club-closures-rls.sql"
    "S.1-guardians-rls.sql"
    "S.1-consents-rls.sql"
    "S.1-medical-certificates-rls.sql"
    "S.1-document-deliveries-rls.sql"
    "2.2-limpiar-uniques-duplicados.sql"
    "2.6-limpiar-fks-generadas.sql"
)

pasar() {
    local archivo="$1" usuario="$2" clave="$3"
    local ruta="$RAIZ/migrations/$archivo"
    [ -f "$ruta" ] || morir "no encuentro $ruta"
    printf '  %-40s (como %s)\n' "$archivo" "$usuario"
    # Sin los NOTICE de "policy ... does not exist, skipping", que en una base
    # nueva salen por decenas, son normales y asustan. Los errores si salen.
    PGOPTIONS="-c client_min_messages=warning" PGPASSWORD="$clave" pg psql -q -v ON_ERROR_STOP=1 \
        -U "$usuario" -d "$PGDATABASE" < "$ruta" > /dev/null
}

consulta_app() {
    PGPASSWORD="$CLAVE_APP" pg psql -q -t -A -U "$PGUSER" -d "$PGDATABASE" "$@"
}

echo "Base de datos en modo: $(modo_bd)"

tablas=$(consulta_app -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" \
         2>/dev/null || echo 0)
tablas=${tablas//[^0-9]/}
if [ "${tablas:-0}" -lt 10 ]; then
    morir "la base solo tiene ${tablas:-0} tablas. Arranca la aplicacion primero y espera a que cree el esquema."
fi
echo "Base '$PGDATABASE' con $tablas tablas. Pasando migraciones:"

for m in "${COMO_SUPERUSUARIO[@]}"; do
    pasar "$m" "$MIGRATION_USER" "$MIGRATION_PASSWORD"
done
for m in "${COMO_APLICACION[@]}"; do
    pasar "$m" "$PGUSER" "$CLAVE_APP"
done

echo
echo "==> Comprobando que ninguna tabla con club_id se ha quedado sin policy"
sueltas=$(consulta_app <<'SQL'
SELECT coalesce(string_agg(c.relname, ', ' ORDER BY c.relname), '')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND NOT c.relrowsecurity
  AND EXISTS (SELECT 1 FROM information_schema.columns col
              WHERE col.table_schema = 'public'
                AND col.table_name = c.relname
                AND col.column_name = 'club_id');
SQL
)
sueltas=${sueltas//$'\r'/}

if [ -n "$sueltas" ]; then
    rojo "Estas tablas llevan club_id y NO tienen Row Level Security: $sueltas"
    rojo "Una tabla asi devuelve filas de otro club en cualquier findById. Falta su migracion."
    exit 1
fi

verde "Migraciones pasadas y todas las tablas con club_id estan bajo policy."
