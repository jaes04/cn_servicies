#!/usr/bin/env bash
#
# Lo que comparten los scripts del kit de despliegue. No se ejecuta solo.
#
# Lo unico interesante que hay aqui es como se lee el .env, y tiene motivo:
# las contrasenas llevan caracteres que bash expande. Con `source .env`, un
# valor con un '$' llega a la aplicacion distinto del que hay en el archivo, y
# el sintoma es un "password authentication failed" que parece un problema de
# la base de datos y se tarda una tarde en descartar.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
aviso() { printf '\033[33m%s\033[0m\n' "$*"; }

morir() { rojo "ERROR: $*"; exit 1; }

# Lee el .env sin que bash interprete nada de los valores.
cargar_env() {
    local archivo="${1:-$RAIZ/.env}"
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

# psql no esta en el mismo sitio en el servidor y en el Windows de desarrollo.
localizar_psql() {
    if [ -n "${PSQL:-}" ]; then
        echo "$PSQL"; return
    fi
    if command -v psql > /dev/null 2>&1; then
        echo "psql"; return
    fi
    local windows="/c/Program Files/PostgreSQL/18/bin/psql.exe"
    if [ -x "$windows" ]; then
        echo "$windows"; return
    fi
    morir "no encuentro psql. Ponlo en el PATH o exporta PSQL=/ruta/a/psql"
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
    morir "no encuentro $nombre. Ponlo en el PATH."
}
