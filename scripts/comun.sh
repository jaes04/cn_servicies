#!/usr/bin/env bash
#
# Lo que comparten los scripts del kit de despliegue. No se ejecuta solo.
#
# Dos cosas con miga:
#
# 1. COMO SE LEE EL .env. Las contrasenas llevan caracteres que bash expande:
#    con `source .env`, un valor con un '$' llega a la aplicacion distinto del
#    que hay en el archivo, y el sintoma es un "password authentication failed"
#    que parece un problema de la base de datos.
#
# 2. DONDE ESTA LA BASE. En desarrollo, PostgreSQL esta instalado en la maquina
#    y las herramientas (psql, pg_dump...) se lanzan desde fuera. En el
#    servidor esta dentro de Docker y NO se ve desde fuera: el compose no
#    publica el 5432, y a proposito. Ahi las herramientas tienen que correr
#    dentro del contenedor. La funcion `pg` de abajo decide sola.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
aviso() { printf '\033[33m%s\033[0m\n' "$*"; }

morir() { rojo "ERROR: $*"; exit 1; }

# ---------------------------------------------------------------------------
#  El .env
# ---------------------------------------------------------------------------

# ENV_FILE permite apuntar a otro archivo —un .env de pruebas, o el de
# produccion con otro nombre— sin tocar nada.
cargar_env() {
    local archivo="${1:-${ENV_FILE:-$RAIZ/.env}}"
    [ -f "$archivo" ] || morir "no encuentro $archivo"

    # El BOM que dejan algunos editores de Windows se pega a la primera clave y
    # la convierte en una variable con un nombre invisible que nadie encuentra.
    if head -c 3 "$archivo" | grep -q $'\xef\xbb\xbf'; then
        morir "$archivo empieza por un BOM. Vuelve a guardarlo como UTF-8 sin BOM."
    fi

    local linea clave valor
    while IFS= read -r linea || [ -n "$linea" ]; do
        linea=${linea%$'\r'}
        case "$linea" in ''|'#'*) continue;; esac
        case "$linea" in *=*) ;; *) continue;; esac
        clave=${linea%%=*}
        valor=${linea#*=}
        export "$clave=$valor"
    done < "$archivo"
}

# ---------------------------------------------------------------------------
#  Donde esta la base
# ---------------------------------------------------------------------------

# "docker" si el servicio db del compose esta corriendo; "local" si no.
# MODO_BD lo fuerza, por si la deteccion se equivoca.
modo_bd() {
    if [ -n "${MODO_BD:-}" ]; then
        echo "$MODO_BD"
        return
    fi
    if command -v docker > /dev/null 2>&1 \
       && docker compose ps --status running --services 2>/dev/null | grep -qx db; then
        echo docker
    else
        echo local
    fi
}

localizar_herramienta() {
    local nombre="$1"
    if command -v "$nombre" > /dev/null 2>&1; then
        echo "$nombre"; return
    fi
    local windows="/c/Program Files/PostgreSQL/18/bin/$nombre.exe"
    if [ -x "$windows" ]; then
        echo "$windows"; return
    fi
    morir "no encuentro $nombre. Ponlo en el PATH, o levanta el compose para usar el del contenedor."
}

# Host por el que conectarse cuando lo que se quiere comprobar ES la contrasena.
#
# Dentro del contenedor, la imagen oficial de postgres confia en el socket Y en
# localhost (pg_hba: "host all all 127.0.0.1/32 trust"): por ahi entra
# cualquier contrasena, y una comprobacion diria "vale" sin mirar nada.
# Comprobado. Por el nombre del servicio (-h db) la conexion llega por la red de
# Docker, cae en "host all all all scram-sha-256" y la contrasena se exige: es
# el mismo camino por el que se conecta la API.
host_con_clave() {
    if [ "$(modo_bd)" = docker ]; then
        echo db
    else
        echo "${PGHOST:-localhost}"
    fi
}

# Ejecuta una herramienta de PostgreSQL alli donde este la base.
#
#     pg psql -U cn_app -d cn_servicies -c "SELECT 1"
#     pg pg_dump -U cn_app -d cn_servicies -Fc > copia.dump
#
# PGPASSWORD y PGOPTIONS, si estan puestas, viajan con ella.
#
# LOS ARCHIVOS SIEMPRE POR LA ENTRADA ESTANDAR (< archivo), NUNCA CON -f. En
# modo docker la herramienta corre dentro del contenedor y no ve el disco de la
# maquina: un -f apuntaria a una ruta que alli no existe.
pg() {
    local herramienta="$1"
    shift
    if [ "$(modo_bd)" = docker ]; then
        local entorno=()
        [ -n "${PGPASSWORD:-}" ] && entorno+=(-e "PGPASSWORD=$PGPASSWORD")
        [ -n "${PGOPTIONS:-}" ]  && entorno+=(-e "PGOPTIONS=$PGOPTIONS")
        # -T: sin terminal. Con terminal, docker mezcla retornos de carro en la
        # salida y un pg_dump binario sale corrupto.
        docker compose exec -T "${entorno[@]}" db "$herramienta" "$@"
    else
        "$(localizar_herramienta "$herramienta")" -h "${PGHOST:-localhost}" "$@"
    fi
}
