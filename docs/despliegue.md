# Despliegue

Cómo se levanta un entorno desde cero y cómo se actualiza. Escrito para que lo
siga alguien a quien se le ha caído el servidor un domingo, no para leerlo
entero de antemano.

> **Estado: probado a medias.** El arranque de una base nueva y la copia de
> seguridad están verificados. **La restauración no**, porque crear una base
> necesita un rol con `CREATEDB` y `MIGRATION_PASSWORD` no es hoy la contraseña
> real de `postgres`. Ver §6.

---

## 1. El orden, que es lo único que no se puede improvisar

```
1. Base creada y vacía
2. scripts/preparar-base.sh        ← crea el rol cn_app y le da permiso a crear tablas
3. Arrancar la aplicación          ← Hibernate crea el esquema, como cn_app
4. scripts/migrar.sh               ← Row Level Security y limpiezas
5. scripts/comprobar-entorno.sh    ← antes de abrir al público
```

**Saltarse el 2 deja el 3 en un bucle de reinicios** con un `password
authentication failed` que parece un problema de contraseña y no lo es: el rol
`cn_app` no existe todavía. Y aunque existiera, desde PostgreSQL 15 el esquema
`public` no deja crear tablas a cualquiera, así que Hibernate no podría crear
ni una.

**Hacer el 4 antes del 3** falla con `relation does not exist`: las migraciones
ponen policies sobre tablas que todavía no están.

El detalle de por qué está en `migrations/0.0-bootstrap-rol.sql`.

---

## 2. Primera vez, paso a paso

```bash
git clone <repo> && cd cn_servicies
cp .env.example .env
```

**Rellena el `.env`.** Lo que no puede quedarse como está:

| Variable | Qué poner |
|---|---|
| `PGPASSWORD` | La de `cn_app`. La pone el paso 2 a partir de este valor |
| `MIGRATION_PASSWORD` | **La de verdad de `postgres`**, no un `change_me`. Ver §6 |
| `JWT_SECRET` | `openssl rand -base64 32`. Nunca el del `.env.example` |
| `ADMIN_PASSWORD` | 12 caracteres o más, o la aplicación no arranca con base nueva |
| `CORS_ALLOWED_ORIGINS` | El dominio del frontend |
| `TUNNEL_TOKEN` | El del panel de Cloudflare Zero Trust |
| `FORWARD_HEADERS_STRATEGY` | `framework`, **solo** detrás del túnel. Ver §5 |

```bash
bash scripts/comprobar-entorno.sh     # antes de nada: repasa el .env entero
bash scripts/preparar-base.sh         # paso 2
docker compose up -d                  # paso 3
docker compose logs -f api            # espera a "Started CnServiciesApplication"
bash scripts/migrar.sh                # paso 4
bash scripts/comprobar-entorno.sh     # paso 5
```

El último paso de `migrar.sh` comprueba que **ninguna tabla con `club_id` se ha
quedado sin policy**. Si sale en rojo, falta una migración y hay una tabla que
devuelve filas de otro club en cualquier `findById`: no abras al público.

---

## 3. Actualizar una versión

```bash
git pull
docker compose build api
docker compose up -d api
bash scripts/migrar.sh                # idempotente: pásalo siempre
```

`migrar.sh` se pasa **después de cada despliegue**, no solo la primera vez. Las
migraciones son idempotentes, y una versión que añade una entidad raíz trae su
policy: si no se pasa, esa tabla nace fuera del aislamiento.

**Antes de actualizar, copia:**

```bash
bash scripts/copia-seguridad.sh
```

---

## 4. Copias de seguridad

```bash
bash scripts/copia-seguridad.sh [directorio]      # por defecto ./copias
bash scripts/restaurar.sh <archivo.dump> <base-destino-nueva>
```

**Lo que hay que saber de esto, y no es evidente:**

- **`pg_dump` a secas no funciona aquí.** Las tablas llevan `FORCE ROW LEVEL
  SECURITY`, que aplica la policy también a su dueño, y `pg_dump` apaga RLS por
  su cuenta para llevárselo todo. El resultado es
  `ERROR: query would be affected by row-level security policy`.
- **Peor aún: sin `app.club_id`, `pg_dump` termina con éxito y deja una copia
  válida, comprimida, con todas las tablas y sin una sola fila.** Comprobado. Por
  eso el script cuenta filas al terminar y se niega a dar por buena una copia que
  no cuadra con la base. Si alguna vez haces el volcado a mano, cuenta las filas.
- La copia se lleva **solo el esquema `public`**. En la base de desarrollo
  conviven esquemas de otros proyectos que `cn_app` no puede leer.
- **No va cifrada.** Dentro hay nombres, fechas de nacimiento y tutores de
  menores. Mientras la tarea S.5 siga pendiente, **la copia no debe salir del
  servidor**.
- La restauración va siempre a una base **nueva**. El script se niega a escribir
  sobre la de trabajo.

---

## 5. Detrás del túnel

La API no debe ser alcanzable más que por el túnel de Cloudflare. Dos cosas:

- **`docker-compose.yml` no publica ningún puerto**, y así debe quedarse. El
  `docker-compose.override.yml` que publica el 8080 es de desarrollo, está en
  `.gitignore` y **no debe existir en el servidor**. Un `git clone` no lo trae;
  un `scp` de la carpeta entera, sí.
- **`FORWARD_HEADERS_STRATEGY=framework`.** Sin esto todas las peticiones llegan
  con la IP del proxy, comparten el límite de intentos de login, y los fallos de
  un desconocido dejan al club entero sin poder entrar. Y al revés: no lo pongas
  si la API es alcanzable sin el túnel, porque entonces esa cabecera la escribe
  quien quiere y cualquiera se inventa una IP por intento.

---

## 6. Lo que sigue sin estar resuelto

- **`MIGRATION_PASSWORD` no es la contraseña real de `postgres`.** Se sabía que
  bloqueaba el paso a Flyway. Bloquea además **crear la base y restaurar una
  copia**, que es justo lo que hace falta el día que algo se rompa. Es la primera
  cosa que arreglar en el servidor.
- **La restauración no está probada de punta a punta** por lo anterior. Hacerlo
  es un comando, y hasta que no se haga las copias son una suposición:
  ```bash
  createdb -h localhost -U postgres -O cn_app cn_prueba
  bash scripts/restaurar.sh copias/<la-última>.dump cn_prueba
  dropdb -h localhost -U postgres cn_prueba
  ```
- **El volumen de PostgreSQL es de la 18** desde este despliegue. Si en algún
  entorno quedaba uno creado por la 16, el contenedor no arranca: hace falta
  `pg_upgrade` o un dump con la 16 y una restauración con la 18.
- **Copias cifradas, retención y borrado automático**: tareas S.5 y S.2, sin
  hacer.
- **Nada de esto se ejecuta solo.** No hay copia programada ni aviso si falla.
  Mientras siga así, la copia depende de que alguien se acuerde.
