#!/usr/bin/env bash
#
# Paso 3 de un entorno nuevo: las migraciones, en orden y con el rol que toca.
#
#     bash scripts/migrar.sh
#
# VA DESPUES DE QUE LA APLICACION HAYA ARRANCADO AL MENOS UNA VEZ. Todas estas
# migraciones tocan tablas, y las tablas las crea Hibernate al arrancar. Si se
# pasan antes, fallan con "relation does not exist".
#
# Todas son idempotentes: se pueden volver a pasar sin efecto, y eso es lo que
# las hace utiles despues de cada despliegue que anada una tabla nueva.
#
# EL ORDEN IMPORTA: la 0.6 crea la funcion `app_club_visible(uuid)` de la que
# dependen todas las policies de las demas.
#
# EL ROL IMPORTA MAS: la 0.6 crea el rol de la aplicacion, asi que necesita al
# superusuario. El resto las puede `cn_app`, que es dueno de sus tablas, y de
# hecho es como se han pasado siempre en desarrollo.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env
PSQL="$(localizar_psql)"

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${MIGRATION_USER:?falta MIGRATION_USER en el .env}"
: "${MIGRATION_PASSWORD:?falta MIGRATION_PASSWORD en el .env}"
: "${PGHOST:=localhost}"

CLAVE_APP="$PGPASSWORD"

# Con el superusuario, porque crea el rol.
COMO_SUPERUSUARIO=(
    "0.6-row-level-security.sql"
)

# Con cn_app, que es dueno de las tablas. Las de RLS primero y las de limpieza
# al final: las segundas borran restricciones que Hibernate genero de mas.
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
    PGPASSWORD="$clave" "$PSQL" -q -v ON_ERROR_STOP=1 \
        -h "$PGHOST" -U "$usuario" -d "$PGDATABASE" -f "$ruta" > /dev/null
}

# Que nadie pase esto contra una base sin esquema y se quede con medio trabajo.
export PGPASSWORD="$CLAVE_APP"
tablas=$("$PSQL" -q -t -A -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" \
         -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" 2>/dev/null || echo 0)
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
export PGPASSWORD="$CLAVE_APP"
sueltas=$("$PSQL" -q -t -A -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" <<'SQL'
SELECT string_agg(c.relname, ', ' ORDER BY c.relname)
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN information_schema.columns col
     ON col.table_name = c.relname AND col.table_schema = 'public' AND col.column_name = 'club_id'
WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relrowsecurity;
SQL
)

if [ -n "$sueltas" ]; then
    rojo "Estas tablas llevan club_id y NO tienen Row Level Security: $sueltas"
    rojo "Una tabla asi devuelve filas de otro club en cualquier findById. Falta su migracion."
    exit 1
fi

verde "Migraciones pasadas y todas las tablas con club_id estan bajo policy."
