#!/usr/bin/env bash
#
# Restaura una copia en una base NUEVA y comprueba que lo restaurado sirve.
#
#     bash scripts/restaurar.sh copias/cn_servicies-20260916-120000.dump cn_prueba
#
# NUNCA sobre la base de trabajo, y el script se niega a hacerlo: restaurar
# encima de la buena es como un susto se convierte en una perdida de datos, y a
# media restauracion ya no hay vuelta atras.
#
# CREAR LA BASE DESTINO NECESITA UN ROL CON CREATEDB, que `cn_app` no tiene a
# proposito. Si el script no puede crearla, te dice el comando y sales del paso
# creandola a mano; el resto lo hace igual.
#
# Al terminar cuenta filas y comprueba que las policies siguen puestas. Una
# restauracion que deja las tablas sin Row Level Security es peor que ninguna,
# porque parece que ha ido bien y el aislamiento entre clubes ya no existe.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env
PSQL="$(localizar_psql)"
PG_RESTORE="$(localizar_herramienta pg_restore)"

ARCHIVO="${1:-}"
DESTINO="${2:-}"
[ -n "$ARCHIVO" ] || morir "uso: bash scripts/restaurar.sh <archivo.dump> <base-destino>"
[ -f "$ARCHIVO" ] || morir "no encuentro $ARCHIVO"
[ -n "$DESTINO" ] || morir "dime en que base restaurar. Nunca sobre la de trabajo."

: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${PGHOST:=localhost}"

if [ "$DESTINO" = "${PGDATABASE:-}" ]; then
    morir "'$DESTINO' es la base de trabajo. Restaura en otra y compara antes de tocar nada."
fi

# app.club_id=public: las tablas llevan FORCE ROW LEVEL SECURITY, asi que sin
# esto el WITH CHECK de cada policy rechaza las filas segun entran.
export PGOPTIONS="-c app.club_id=public"

existe() {
    "$PSQL" -q -t -A -h "$PGHOST" -U "$1" -d postgres \
            -c "SELECT 1 FROM pg_database WHERE datname = '$DESTINO'" 2>/dev/null | grep -q 1
}

echo "==> Base destino '$DESTINO'"
if existe "$PGUSER"; then
    aviso "ya existe. Se restaura encima; si tenia algo, revisalo tu."
else
    creada=0
    if [ -n "${MIGRATION_USER:-}" ] && [ -n "${MIGRATION_PASSWORD:-}" ]; then
        CREATEDB="$(localizar_herramienta createdb)"
        if PGPASSWORD="$MIGRATION_PASSWORD" "$CREATEDB" -h "$PGHOST" -U "$MIGRATION_USER" \
               -O "$PGUSER" "$DESTINO" 2>/dev/null; then
            creada=1
            verde "creada, con '$PGUSER' de dueno"
        fi
    fi
    if [ "$creada" -eq 0 ]; then
        rojo "no he podido crearla: hace falta un rol con CREATEDB y su contrasena."
        echo "Creala a mano y vuelve a lanzar esto:"
        echo "    createdb -h $PGHOST -U ${MIGRATION_USER:-postgres} -O $PGUSER $DESTINO"
        exit 1
    fi
fi

echo "==> Restaurando"
"$PG_RESTORE" -h "$PGHOST" -U "$PGUSER" -d "$DESTINO" --no-owner --role="$PGUSER" "$ARCHIVO"

echo
echo "==> Que ha quedado dentro"
"$PSQL" -h "$PGHOST" -U "$PGUSER" -d "$DESTINO" <<'SQL'
SELECT 'clubs' AS tabla, count(*) FROM clubs
UNION ALL SELECT 'users',    count(*) FROM users
UNION ALL SELECT 'athletes', count(*) FROM athletes
ORDER BY 1;
SQL

echo "==> Row Level Security en la base restaurada"
sueltas=$("$PSQL" -q -t -A -h "$PGHOST" -U "$PGUSER" -d "$DESTINO" <<'SQL'
SELECT string_agg(c.relname, ', ' ORDER BY c.relname)
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN information_schema.columns col
     ON col.table_name = c.relname AND col.table_schema = 'public' AND col.column_name = 'club_id'
WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relrowsecurity;
SQL
)
if [ -n "$sueltas" ]; then
    rojo "Restaurada, PERO estas tablas con club_id se han quedado sin policy: $sueltas"
    rojo "Pasa scripts/migrar.sh contra '$DESTINO' antes de darla por buena."
    exit 1
fi

verde "Restauracion correcta en '$DESTINO', con las policies puestas."
echo
aviso "Cuando termines de mirarla, borrala: no dejes copias de los datos rodando."
echo "    dropdb -h $PGHOST -U ${MIGRATION_USER:-postgres} $DESTINO"
