#!/usr/bin/env bash
#
# Paso 1 de un entorno nuevo: deja la base lista para que arranque la aplicacion.
#
# Crea el rol `cn_app`, le pone la contrasena del .env y le da permiso para
# crear tablas. Se ejecuta UNA VEZ por entorno, como superusuario, con la base
# ya creada y vacia.
#
#     bash scripts/preparar-base.sh
#
# Despues de esto arranca la aplicacion, que crea el esquema, y solo entonces
# corre scripts/migrar.sh. El orden no es negociable y esta explicado en
# migrations/0.0-bootstrap-rol.sql.
#
# Es idempotente: pasarlo dos veces no rompe nada y sirve para volver a fijar la
# contrasena del rol si se cambia en el .env.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env
PSQL="$(localizar_psql)"

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${MIGRATION_USER:?falta MIGRATION_USER en el .env}"
: "${PGHOST:=localhost}"

echo "Base '$PGDATABASE' en $PGHOST, como '$MIGRATION_USER'."

# La contrasena del superusuario, no la de la aplicacion.
export PGPASSWORD_APP="$PGPASSWORD"
export PGPASSWORD="${MIGRATION_PASSWORD:?falta MIGRATION_PASSWORD en el .env}"

echo "==> Creando el rol y sus permisos"
"$PSQL" -v ON_ERROR_STOP=1 -h "$PGHOST" -U "$MIGRATION_USER" -d "$PGDATABASE" \
        -f "$RAIZ/migrations/0.0-bootstrap-rol.sql"

# La contrasena va por la entrada estandar y no como argumento: lo que se pasa
# en la linea de comandos lo ve cualquiera con un `ps` en esa maquina.
echo "==> Fijando la contrasena de $PGUSER"
escapada=${PGPASSWORD_APP//\'/\'\'}
printf "ALTER ROLE %s PASSWORD '%s';\n" "$PGUSER" "$escapada" \
    | "$PSQL" -q -v ON_ERROR_STOP=1 -h "$PGHOST" -U "$MIGRATION_USER" -d "$PGDATABASE"

echo "==> Comprobando que la aplicacion puede entrar con ella"
export PGPASSWORD="$PGPASSWORD_APP"
if "$PSQL" -q -t -A -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "SELECT 1" > /dev/null 2>&1; then
    verde "$PGUSER entra en $PGDATABASE."
else
    morir "$PGUSER no puede entrar. Revisa PGPASSWORD y el pg_hba.conf del servidor."
fi

echo
verde "Base preparada. Ahora:"
echo "  1. Arranca la aplicacion y espera a que cree el esquema."
echo "  2. Cuando responda, pasa: bash scripts/migrar.sh"
