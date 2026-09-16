#!/usr/bin/env bash
#
# Copia de seguridad de la base.
#
#     bash scripts/copia-seguridad.sh [directorio]
#
# Deja un archivo con fecha en el directorio que se le diga, o en ./copias.
# Funciona igual con la base en Docker que instalada en la maquina.
#
# FORMATO `custom` (-Fc), no SQL plano: va comprimido, se restaura con
# pg_restore y permite sacar una sola tabla si hace falta.
#
# ---------------------------------------------------------------------------
# POR QUE ESTO NO ES UN `pg_dump` A SECAS
# ---------------------------------------------------------------------------
# Las tablas llevan FORCE ROW LEVEL SECURITY, que aplica la policy tambien a su
# dueno. Y `pg_dump` apaga RLS por su cuenta para asegurarse de llevarselo
# todo, lo que con un rol que no puede saltarsela acaba en:
#
#     ERROR: query would be affected by row-level security policy for table "athletes"
#
# Se copia con `cn_app`, `--enable-row-security` y `app.club_id=public`, que es
# el valor que abre todos los clubes. Sin necesitar al superusuario, que ademas
# es lo correcto: una copia no necesita mas permisos que la aplicacion.
#
# EL PELIGRO, y por eso se cuentan filas al final: si `app.club_id` no llega,
# RLS no falla, simplemente no devuelve ninguna fila. `pg_dump` termina con
# exito y la copia sale valida, comprimida, con todas las tablas... y vacia.
# Comprobado. Una copia vacia que parece buena es peor que no tener copia.
# ---------------------------------------------------------------------------
#
# NO CIFRA NADA: dentro van nombres, fechas de nacimiento y tutores de menores.
# Cifrarla antes de sacarla del servidor es la tarea S.5 y sigue pendiente.
# Mientras tanto, esta copia no sale de la maquina.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"

DESTINO="${1:-$RAIZ/copias}"
mkdir -p "$DESTINO"

SELLO="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/$PGDATABASE-$SELLO.dump"

export PGOPTIONS="-c app.club_id=public"

echo "Base de datos en modo: $(modo_bd)"

# Solo el esquema `public`. Sin esto, pg_dump intenta bloquear todas las tablas
# de la base —incluidos esquemas de otros proyectos que compartan servidor, como
# pasa en la base de desarrollo— y muere con "permission denied for schema".
#
# A la salida estandar y no con -f: en modo docker, pg_dump corre dentro del
# contenedor y un -f escribiria el archivo alli dentro, donde nadie lo ve.
echo "==> Copiando el esquema public de '$PGDATABASE' a $ARCHIVO"
pg pg_dump -U "$PGUSER" -d "$PGDATABASE" -n public --enable-row-security -Fc > "$ARCHIVO"

echo "==> Comprobando que se puede leer"
objetos=$(pg pg_restore -l < "$ARCHIVO" | grep -c ';' || true)
[ "${objetos:-0}" -gt 0 ] || morir "la copia no se puede leer. NO la des por buena."

# Lo importante: que lo guardado sea lo que hay. `pg_restore -l` solo lee el
# indice, y un volcado a medias tambien tiene indice.
echo "==> Comprobando que no esta vacia"
volcado=$(pg pg_restore -f - < "$ARCHIVO")
fallos=0
for tabla in clubs users athletes; do
    en_la_base=$(pg psql -q -t -A -U "$PGUSER" -d "$PGDATABASE" -c "SELECT count(*) FROM $tabla")
    en_la_base=${en_la_base//[^0-9]/}
    en_la_copia=$(printf '%s\n' "$volcado" | awk -v t="public.$tabla" '
        $1 == "COPY" && $2 == t { dentro = 1; n = 0; next }
        dentro && length($0) == 2 && substr($0, 1, 1) == "\\" && substr($0, 2, 1) == "." {
            print n; dentro = 0; visto = 1; exit
        }
        dentro { n++ }
        END { if (!visto) print 0 }')
    if [ "$en_la_base" = "$en_la_copia" ]; then
        printf '    %-10s %s filas\n' "$tabla" "$en_la_copia"
    else
        rojo "    $tabla: la base tiene $en_la_base filas y la copia $en_la_copia"
        fallos=$((fallos + 1))
    fi
done
[ "$fallos" -eq 0 ] || morir "la copia no coincide con la base. NO la des por buena."

tamano=$(du -h "$ARCHIVO" | cut -f1)
verde "Copia hecha y cuadrada: $ARCHIVO ($tamano, $objetos objetos)"
echo
aviso "Una copia que no has restaurado nunca no demuestra nada. Pruebala:"
echo "    bash scripts/restaurar.sh \"$ARCHIVO\" ${PGDATABASE}_prueba"
