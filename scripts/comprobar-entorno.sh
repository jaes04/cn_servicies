#!/usr/bin/env bash
#
# Repasa el .env y los archivos de despliegue antes de tocar nada.
#
# Cada comprobacion de aqui esta porque el fallo correspondiente ya ha pasado o
# estuvo a punto: no es una lista de buenas intenciones.
#
#     bash scripts/comprobar-entorno.sh
#
# Devuelve 0 si todo esta bien, 1 si hay algo que impide desplegar. Los avisos
# en amarillo no cortan, pero merecen una lectura.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

fallos=0
avisos=0

fallo() { rojo  "  [FALLO] $*"; fallos=$((fallos + 1)); }
ojo()   { aviso "  [AVISO] $*"; avisos=$((avisos + 1)); }
bien()  { verde "  [ok]    $*"; }

echo
echo "== 1. El archivo .env =="

ARCHIVO="${1:-$RAIZ/.env}"
[ -f "$ARCHIVO" ] || morir "no encuentro $ARCHIVO"

if head -c 3 "$ARCHIVO" | grep -q $'\xef\xbb\xbf'; then
    fallo "empieza por un BOM. La primera variable se vuelve ilegible para todo el mundo."
else
    bien "sin BOM"
fi

cargar_env "$ARCHIVO"

for v in PGDATABASE PGUSER PGPASSWORD MIGRATION_USER MIGRATION_PASSWORD \
         JWT_SECRET CORS_ALLOWED_ORIGINS ADMIN_USERNAME ADMIN_PASSWORD; do
    if [ -z "${!v:-}" ]; then
        fallo "falta $v"
    fi
done
[ "$fallos" -eq 0 ] && bien "estan todas las variables que hacen falta"

# Un '$' dentro de un valor lo expande docker compose al leer el .env, asi que
# el contenedor arranca con una contrasena distinta de la que pusiste. Hay que
# escribirlo '$$', o no usar '$'.
con_dolar=""
while IFS= read -r linea || [ -n "$linea" ]; do
    linea=${linea%$'\r'}
    case "$linea" in ''|'#'*) continue;; esac
    case "$linea" in *=*) ;; *) continue;; esac
    if [[ "${linea#*=}" == *'$'* ]]; then
        con_dolar="$con_dolar ${linea%%=*}"
    fi
done < "$ARCHIVO"
if [ -n "$con_dolar" ]; then
    fallo "estas variables llevan un '\$' en el valor y docker compose las cambiara:$con_dolar"
    echo  "          Escribelo '\$\$' o cambia el valor. Se nota como 'password authentication failed'."
else
    bien "ningun valor lleva un '\$' que docker compose vaya a expandir"
fi

echo
echo "== 2. Secretos =="

FILTRADO_JWT="404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
FILTRADA_ADMIN="th3-j@35Cre4T0r"

if [ "${JWT_SECRET:-}" = "$FILTRADO_JWT" ]; then
    fallo "JWT_SECRET es el valor que esta publicado en el historico de git."
    echo  "          Con el, cualquiera se firma un token de administrador de cualquier club."
    echo  "          Genera otro:  openssl rand -base64 32"
else
    bien "JWT_SECRET no es el valor filtrado"
fi

bytes=$(printf '%s' "${JWT_SECRET:-}" | base64 -d 2>/dev/null | wc -c || echo 0)
if [ "$bytes" -lt 32 ]; then
    fallo "JWT_SECRET decodifica a $bytes bytes y HS256 pide 32 o mas."
else
    bien "JWT_SECRET decodifica a $bytes bytes"
fi

if [ "${ADMIN_PASSWORD:-}" = "$FILTRADA_ADMIN" ]; then
    fallo "ADMIN_PASSWORD es la que estuvo en el historico de git."
else
    bien "ADMIN_PASSWORD no es la filtrada"
fi

if [ "${#ADMIN_PASSWORD}" -lt 12 ]; then
    fallo "ADMIN_PASSWORD tiene ${#ADMIN_PASSWORD} caracteres y la politica pide 12."
    echo  "          Con una base nueva, la aplicacion no arrancaria: el alta del administrador falla."
else
    bien "ADMIN_PASSWORD llega al minimo de 12"
fi

if [ "${PGUSER:-}" != "cn_app" ]; then
    fallo "PGUSER es '${PGUSER:-}'. Las migraciones y el initdb dan por hecho que el rol se llama cn_app."
else
    bien "PGUSER es cn_app"
fi

if [ "${PGUSER:-}" = "${MIGRATION_USER:-}" ]; then
    fallo "PGUSER y MIGRATION_USER son el mismo rol."
    echo  "          La aplicacion se conectaria como superusuario y RLS deja de aislar nada."
else
    bien "el rol de la aplicacion y el de migraciones son distintos"
fi

echo
echo "== 3. Docker =="

COMPOSE="$RAIZ/docker-compose.yml"
if grep -qE "image:.*:latest" "$COMPOSE"; then
    fallo "hay imagenes sin version fija (:latest) en docker-compose.yml:"
    grep -nE "image:.*:latest" "$COMPOSE" | sed 's/^/          /'
else
    bien "todas las imagenes llevan version fija"
fi

if [ -f "$RAIZ/docker-compose.override.yml" ]; then
    ojo "hay un docker-compose.override.yml y 'docker compose up' lo aplica solo."
    echo "          Esta en .gitignore, asi que un 'git clone' en el servidor no lo trae."
    echo "          Pero si subes los archivos con scp o rsync, publicaria el 8080 al host y"
    echo "          se saltaria el tunel. En el servidor no debe existir."
else
    bien "no hay override que publique puertos sin querer"
fi

if grep -qE "^\s*ports:" "$COMPOSE"; then
    ojo "docker-compose.yml publica puertos al host. Detras del tunel no hace falta ninguno."
else
    bien "docker-compose.yml no publica ningun puerto"
fi

echo
echo "== 4. Detras del tunel =="

if [ "${FORWARD_HEADERS_STRATEGY:-none}" = "framework" ]; then
    bien "FORWARD_HEADERS_STRATEGY=framework: el limite de intentos distingue cada IP"
else
    ojo "FORWARD_HEADERS_STRATEGY no esta en 'framework'."
    echo "          Detras de Cloudflare Tunnel todas las peticiones llegan con la IP del proxy,"
    echo "          comparten el limite de intentos y los fallos de un desconocido dejan al club"
    echo "          entero sin poder entrar. Ponlo SOLO si la API no es alcanzable sin el tunel."
fi

echo
if [ "$fallos" -gt 0 ]; then
    rojo "== $fallos cosa(s) que impiden desplegar, $avisos aviso(s) =="
    exit 1
fi
verde "== Todo en orden, $avisos aviso(s) =="
