#!/usr/bin/env bash
#
# Restaura una copia en una base NUEVA y comprueba que lo restaurado sirve.
#
#     bash scripts/restaurar.sh copias/cn_servicies-20260916-120000.dump cn_prueba
#
# Funciona igual con la base en Docker que instalada en la maquina.
#
# NUNCA sobre la base de trabajo, y el script se niega a hacerlo: restaurar
# encima de la buena es como un susto se convierte en una perdida de datos.
#
# CREAR LA BASE NECESITA AL SUPERUSUARIO. En Docker funciona sin mas: el
# superusuario del contenedor es MIGRATION_USER, creado con MIGRATION_PASSWORD.
# En desarrollo depende de que MIGRATION_PASSWORD sea la contrasena real de
# postgres, que hoy no lo es.
#
# Al terminar cuenta filas y comprueba que las policies siguen puestas: una
# restauracion que deja las tablas sin Row Level Security parece que ha ido
# bien, y el aislamiento entre clubes ya no existe.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env

ARCHIVO="${1:-}"
DESTINO="${2:-}"
[ -n "$ARCHIVO" ] || morir "uso: bash scripts/restaurar.sh <archivo.dump> <base-destino>"
[ -f "$ARCHIVO" ] || morir "no encuentro $ARCHIVO"
[ -n "$DESTINO" ] || morir "dime en que base restaurar. Nunca sobre la de trabajo."

: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${MIGRATION_USER:?falta MIGRATION_USER en el .env}"
: "${MIGRATION_PASSWORD:?falta MIGRATION_PASSWORD en el .env}"

[[ "$DESTINO" =~ ^[a-z_][a-z0-9_]*$ ]] || morir "'$DESTINO' no vale como nombre de base: minusculas, numeros y _"
if [ "$DESTINO" = "${PGDATABASE:-}" ]; then
    morir "'$DESTINO' es la base de trabajo. Restaura en otra y compara antes de tocar nada."
fi

CLAVE_APP="$PGPASSWORD"

como_superusuario() { PGPASSWORD="$MIGRATION_PASSWORD" pg "$@"; }
como_app()          { PGPASSWORD="$CLAVE_APP" pg "$@"; }

echo "Base de datos en modo: $(modo_bd)"

echo "==> Creando la base '$DESTINO'"
ya=$(como_superusuario psql -q -t -A -U "$MIGRATION_USER" -d postgres \
     -c "SELECT count(*) FROM pg_database WHERE datname = '$DESTINO'" 2>/dev/null) \
    || morir "no puedo entrar como $MIGRATION_USER. En desarrollo, MIGRATION_PASSWORD no es la contrasena real de postgres; ver docs/despliegue.md §6."
ya=${ya//[^0-9]/}
[ "${ya:-0}" = "0" ] || morir "la base '$DESTINO' ya existe. Borrala a mano si de verdad quieres reutilizar ese nombre."

como_superusuario createdb -U "$MIGRATION_USER" -O "$PGUSER" "$DESTINO"
verde "    creada, con '$PGUSER' de dueno"

# La copia se hace solo del esquema `public` (-n public), y por eso trae su
# propio CREATE SCHEMA public. Una base recien creada ya tiene uno, vacio, y
# pg_restore fallaria con "schema public already exists". Se quita antes. Es
# seguro solo porque esta base la acaba de crear este script y esta vacia: por
# eso se niega arriba a restaurar en una que ya existiera.
como_superusuario psql -q -v ON_ERROR_STOP=1 -U "$MIGRATION_USER" -d "$DESTINO" \
    -c "DROP SCHEMA public CASCADE" > /dev/null

# COMO SUPERUSUARIO, y sin --no-owner. La copia la hace cn_app, pero restaurar
# es otra cosa: el volcado trae los ALTER ... OWNER TO cn_app de cada tabla y
# los ALTER DEFAULT PRIVILEGES que puso la 0.6, y eso solo lo puede aplicar el
# superusuario. Con cn_app falla en "permission denied to change default
# privileges". Asi las tablas quedan con el mismo dueno que en el original, que
# es lo que necesitan FORCE ROW LEVEL SECURITY y las migraciones siguientes.
# El superusuario se salta RLS, asi que no hace falta app.club_id.
echo "==> Restaurando"
como_superusuario pg_restore -U "$MIGRATION_USER" -d "$DESTINO" --exit-on-error < "$ARCHIVO"

echo
echo "==> Que ha quedado dentro"
PGOPTIONS="-c app.club_id=public" como_app psql -U "$PGUSER" -d "$DESTINO" <<'SQL'
SELECT 'clubs' AS tabla, count(*) FROM clubs
UNION ALL SELECT 'users',    count(*) FROM users
UNION ALL SELECT 'athletes', count(*) FROM athletes
ORDER BY 1;
SQL

echo "==> Row Level Security en la base restaurada"
sueltas=$(como_app psql -q -t -A -U "$PGUSER" -d "$DESTINO" <<'SQL'
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
    rojo "Restaurada, PERO estas tablas con club_id se han quedado sin policy: $sueltas"
    rojo "Pasa scripts/migrar.sh contra '$DESTINO' antes de darla por buena."
    exit 1
fi

# Sin fijar club, RLS tiene que tapar todo. Si aqui se ven filas, las policies
# estan puestas pero no filtran.
tapadas=$(como_app psql -q -t -A -U "$PGUSER" -d "$DESTINO" -c "SELECT count(*) FROM athletes")
tapadas=${tapadas//[^0-9]/}
if [ "${tapadas:-0}" != "0" ]; then
    rojo "Sin club fijado se ven $tapadas atletas. Las policies no estan filtrando."
    exit 1
fi

verde "Restauracion correcta en '$DESTINO': datos dentro y policies filtrando."
echo
aviso "Cuando termines de mirarla, borrala: no dejes copias de los datos rodando."
if [ "$(modo_bd)" = docker ]; then
    echo "    docker compose exec -T db dropdb -U $MIGRATION_USER $DESTINO"
else
    echo "    dropdb -h ${PGHOST:-localhost} -U $MIGRATION_USER $DESTINO"
fi
