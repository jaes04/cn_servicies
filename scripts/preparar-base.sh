#!/usr/bin/env bash
#
# Crea el rol de la aplicacion y le fija la contrasena del .env.
#
#     bash scripts/preparar-base.sh
#
# CON DOCKER NO HACE FALTA LA PRIMERA VEZ: lo hace solo el contenedor de
# PostgreSQL al inicializar un volumen vacio (docker/initdb). Este script sirve
# para dos cosas:
#
#   - Una base instalada en la maquina, como la de desarrollo, donde no hay
#     initdb que lo haga. Va ANTES del primer arranque de la aplicacion.
#   - Cambiar la contrasena de cn_app despues. El initdb solo corre una vez:
#     si cambias PGPASSWORD en el .env, la base no se entera hasta que pases
#     esto.
#
# Es idempotente.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${MIGRATION_USER:?falta MIGRATION_USER en el .env}"
: "${MIGRATION_PASSWORD:?falta MIGRATION_PASSWORD en el .env}"

[ "$PGUSER" = "cn_app" ] || morir "PGUSER es '$PGUSER'. Todas las migraciones dan por hecho que el rol se llama cn_app."

CLAVE_APP="$PGPASSWORD"

como_superusuario() { PGPASSWORD="$MIGRATION_PASSWORD" pg "$@"; }

echo "Base de datos en modo: $(modo_bd). Base '$PGDATABASE', como '$MIGRATION_USER'."

echo "==> Creando el rol y sus permisos"
como_superusuario psql -q -v ON_ERROR_STOP=1 -U "$MIGRATION_USER" -d "$PGDATABASE" \
    < "$RAIZ/migrations/0.0-bootstrap-rol.sql" > /dev/null \
    || morir "no puedo entrar como $MIGRATION_USER. Revisa MIGRATION_PASSWORD: tiene que ser la contrasena real del superusuario."

# La contrasena va dentro del SQL por la entrada estandar, no como argumento:
# lo que se pasa en la linea de comandos lo ve cualquiera con un `ps`. Y se
# cita con :'clave' de psql, que escapa las comillas por su cuenta.
echo "==> Fijando la contrasena de $PGUSER"
escapada=${CLAVE_APP//\\/\\\\}
escapada=${escapada//\'/\\\'}
printf "\\set clave '%s'\nALTER ROLE cn_app PASSWORD :'clave';\n" "$escapada" \
    | como_superusuario psql -q -v ON_ERROR_STOP=1 -U "$MIGRATION_USER" -d "$PGDATABASE" > /dev/null

# Por el camino que exige contrasena: ver host_con_clave en comun.sh. Dentro
# del contenedor, por localhost entraria cualquier contrasena.
echo "==> Comprobando que la aplicacion puede entrar con ella"
if PGPASSWORD="$CLAVE_APP" pg psql -q -t -A -h "$(host_con_clave)" -U "$PGUSER" -d "$PGDATABASE" -c "SELECT 1" > /dev/null 2>&1; then
    verde "$PGUSER entra en $PGDATABASE."
else
    morir "$PGUSER no puede entrar. Revisa PGPASSWORD y el pg_hba.conf del servidor."
fi

echo
verde "Rol preparado."
