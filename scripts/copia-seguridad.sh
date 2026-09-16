#!/usr/bin/env bash
#
# Copia de seguridad de la base.
#
#     bash scripts/copia-seguridad.sh [directorio]
#
# Deja un archivo con fecha en el directorio que se le diga, o en ./copias.
#
# FORMATO `custom` (-Fc), no SQL plano: va comprimido, se restaura con
# pg_restore y permite sacar una sola tabla si hace falta. Un .sql de 200 MB
# solo se puede tragar entero.
#
# ---------------------------------------------------------------------------
# POR QUE ESTO NO ES UN `pg_dump` A SECAS
# ---------------------------------------------------------------------------
# Las tablas llevan FORCE ROW LEVEL SECURITY, que aplica la policy tambien a su
# dueno. Y `pg_dump` desactiva RLS por su cuenta para asegurarse de llevarselo
# todo, lo que con un rol que no puede saltarsela termina en:
#
#     ERROR: query would be affected by row-level security policy for table "athletes"
#
# Hay dos salidas. La estandar es copiar con el superusuario, que se salta RLS
# por definicion. Aqui se hace la otra —seguir con `cn_app`, encender
# `--enable-row-security` y poner `app.club_id=public`, que es el valor que abre
# todos los clubes— por dos motivos: no hace falta la contrasena del
# superusuario en el servidor, y una copia no necesita mas permisos que los de
# la aplicacion.
#
# EL PELIGRO DE ESTA VIA, y por eso el script cuenta filas al final: si
# `app.club_id` no llega, RLS no falla, simplemente no devuelve ninguna fila. La
# copia sale correcta, comprimida, con todas las tablas... y vacia. Una copia
# vacia que parece buena es peor que no tener copia, asi que aqui se comprueba
# que lo guardado coincide con lo que hay en la base.
# ---------------------------------------------------------------------------
#
# NO CIFRA NADA, y eso importa en cuanto la copia salga del servidor: aqui
# dentro van nombres, fechas de nacimiento y tutores de menores. Cifrarla antes
# de moverla es la tarea S.5 y sigue pendiente. Mientras tanto, esta copia no
# debe salir de la maquina.
#
# Tampoco borra las viejas: la retencion depende de la politica de conservacion
# (S.2), que tampoco esta escrita.

source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_env
PG_DUMP="$(localizar_herramienta pg_dump)"
PG_RESTORE="$(localizar_herramienta pg_restore)"
PSQL="$(localizar_psql)"

: "${PGDATABASE:?falta PGDATABASE en el .env}"
: "${PGUSER:?falta PGUSER en el .env}"
: "${PGPASSWORD:?falta PGPASSWORD en el .env}"
: "${PGHOST:=localhost}"

DESTINO="${1:-$RAIZ/copias}"
mkdir -p "$DESTINO"

SELLO="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/$PGDATABASE-$SELLO.dump"

# Solo el esquema `public`. Sin esto, pg_dump intenta bloquear todas las tablas
# de la base —incluidos esquemas de otros proyectos que compartan servidor, como
# pasa en la base de desarrollo— y muere con "permission denied for schema".
export PGOPTIONS="-c app.club_id=public"

echo "==> Copiando el esquema public de '$PGDATABASE' a $ARCHIVO"
"$PG_DUMP" -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" \
           -n public --enable-row-security -Fc -f "$ARCHIVO"

echo "==> Comprobando que se puede leer"
objetos=$("$PG_RESTORE" -l "$ARCHIVO" | grep -c ';' || true)
[ "$objetos" -gt 0 ] || morir "la copia no se puede leer. NO la des por buena."

# Lo importante: que lo guardado sea lo que hay. `pg_restore -l` solo lee el
# indice, y un volcado a medias tambien tiene indice.
echo "==> Comprobando que no esta vacia"
fallos=0
for tabla in clubs users athletes; do
    en_la_base=$("$PSQL" -q -t -A -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" \
                 -c "SELECT count(*) FROM $tabla")
    en_la_copia=$("$PG_RESTORE" -f - "$ARCHIVO" 2>/dev/null | awk -v t="public.$tabla" '
        $1 == "COPY" && $2 == t { dentro = 1; n = 0; next }
        dentro && $0 == "\\." { print n; dentro = 0; salido = 1; exit }
        dentro { n++ }
        END { if (!salido) print 0 }')
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
