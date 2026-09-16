#!/bin/bash
#
# Crea el rol de la aplicacion la primera vez que arranca el contenedor de
# PostgreSQL. Es el paso 2 de docs/despliegue.md, hecho solo.
#
# La imagen oficial de postgres ejecuta todo lo que haya en
# /docker-entrypoint-initdb.d/ al inicializar un volumen VACIO, como
# superusuario, y nunca mas. Es exactamente el momento que hace falta: la base
# existe, esta vacia, y la aplicacion todavia no ha intentado conectarse.
#
# Sin esto, `docker compose up` en un servidor nuevo deja la API en bucle de
# reinicios con "password authentication failed": se conecta como cn_app, y
# cn_app no existe.
#
# OJO: como solo corre con el volumen vacio, cambiar PGPASSWORD en el .env
# DESPUES no cambia la contrasena del rol. Para eso esta
# scripts/preparar-base.sh, que se puede pasar cuando se quiera.

set -euo pipefail

: "${APP_DB_PASSWORD:?falta APP_DB_PASSWORD: el compose tiene que pasarle PGPASSWORD al contenedor db}"

echo "[initdb] Creando el rol cn_app y sus permisos"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -f /migraciones/0.0-bootstrap-rol.sql

# :'clave' es psql citando el valor como literal de SQL, comillas incluidas.
# Interpolarlo a mano en la cadena romperia con una contrasena que lleve '.
echo "[initdb] Fijando la contrasena de cn_app"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v clave="$APP_DB_PASSWORD" <<'SQL'
ALTER ROLE cn_app PASSWORD :'clave';
SQL

echo "[initdb] Listo. La aplicacion ya puede conectarse como cn_app."
