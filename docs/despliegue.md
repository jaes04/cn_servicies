# Despliegue

Cómo se levanta un entorno desde cero y cómo se actualiza. Escrito para que lo
siga alguien a quien se le ha caído el servidor un domingo, no para leerlo
entero de antemano.

> **Probado de punta a punta el 16/09/2026, en Docker y desde un volumen vacío:**
> el rol se crea solo, la API arranca, las migraciones pasan, se entra con el
> administrador, se hace una copia, se restaura en una base nueva, la aplicación
> arranca sobre la restaurada y el mismo administrador entra y ve sus datos.

---

## 0. Qué contratar (primera vez)

**Un VPS en Hetzner Cloud con, como mínimo:**

| | Mínimo | Por qué |
|---|---|---|
| Memoria | **4 GB** | La imagen de la API se construye en el servidor con Maven, y eso con 2 GB se queda sin memoria. Luego la JVM usa la mitad de lo que haya |
| CPU | 2 vCPU | De sobra para un club |
| Disco | 40 GB | Base, subidas y copias |
| Arquitectura | **x86 (Intel/AMD)** | Es donde se ha probado. ARM debería ir —todas las imágenes lo soportan— pero no está probado |
| Ubicación | **Alemania o Finlandia** | Datos de menores: dentro de la UE. Evita las de EE. UU. y Singapur |
| Sistema | Ubuntu 24.04 LTS | |

Los nombres de los planes y los precios cambian: elige el más barato que cumpla
la tabla.

**Antes de subir nada:**

1. **Acceso solo con clave SSH.** Añádela al crear el servidor y no pongas
   contraseña de root.
2. **Firewall de Hetzner Cloud: entrante solo el puerto 22.** Nada más. El
   túnel de Cloudflare sale del servidor hacia fuera, no necesita ningún puerto
   abierto. Ni el 80, ni el 443, ni el 5432, ni el 8080.
3. Un usuario normal para trabajar, no root, y Docker instalado con el
   repositorio oficial de Docker.
4. `unattended-upgrades` encendido, para los parches de seguridad.

**Lo que necesitas además, fuera de Hetzner:**

- **Un dominio en Cloudflare.** El túnel publica la API en un nombre de ese
  dominio. *La decisión del dominio de producción sigue pendiente del club*; para
  la beta vale cualquiera tuyo.
- **Un túnel creado en Cloudflare Zero Trust**, que te da el `TUNNEL_TOKEN`, con
  un nombre público —por ejemplo `api.tudominio.es`— apuntando a
  `http://api:8080`.

> **Con datos reales, esto no basta.** Hetzner pasa a ser subencargado y hace
> falta firmar su contrato de encargo (S.9), cifrar el disco (S.4.1) y cifrar las
> copias (S.5). Para una beta con datos de mentira, no.

---

## 1. El orden, que es lo único que no se puede improvisar

```
1. Base vacía
2. Crear el rol cn_app          ← en Docker lo hace solo el primer arranque
3. Arrancar la aplicación       ← Hibernate crea el esquema, como cn_app
4. scripts/migrar.sh            ← Row Level Security y limpiezas
5. scripts/comprobar-entorno.sh
```

**Sin el 2, la API no arranca**: se conecta como `cn_app`, que no existe, y entra
en bucle con un `password authentication failed` que parece un problema de
contraseña y no lo es. En Docker lo evita `docker/initdb/01-rol-aplicacion.sh`,
que la imagen de postgres ejecuta **una sola vez**, al inicializar un volumen
vacío.

**El 4 antes del 3** falla con `relation does not exist`: pone policies sobre
tablas que todavía no están. El script lo comprueba antes de empezar.

---

## 2. Primera vez, paso a paso (en el servidor)

```bash
git clone <repo> && cd cn_servicies
cp .env.example .env
```

**Rellena el `.env`:**

| Variable | Qué poner |
|---|---|
| `PGDATABASE` | `cn_servicies` |
| `PGUSER` | `cn_app`. **No lo cambies**: todas las migraciones dan por hecho ese nombre |
| `PGPASSWORD` | La de `cn_app`. Generala: `openssl rand -hex 24` |
| `MIGRATION_USER` | `postgres` |
| `MIGRATION_PASSWORD` | La del superusuario. Generala igual. En Docker, la base se crea con ella |
| `JWT_SECRET` | `openssl rand -base64 32`. Nunca el del `.env.example` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | El primer administrador. La contraseña, 12 caracteres o más |
| `CORS_ALLOWED_ORIGINS` | El dominio del frontend |
| `TUNNEL_TOKEN` | El de Cloudflare Zero Trust |
| `FORWARD_HEADERS_STRATEGY` | `framework`. Ver §5 |

`openssl rand -hex` da contraseñas sin `$` ni comillas, que son las que rompen a
docker compose.

```bash
bash scripts/comprobar-entorno.sh     # repasa el .env entero antes de nada
docker compose up -d                  # pasos 2 y 3: rol, base y API
docker compose logs -f api            # espera a "Started CnServiciesApplication", sal con Ctrl+C
bash scripts/migrar.sh                # paso 4
bash scripts/comprobar-entorno.sh     # paso 5
```

`migrar.sh` termina comprobando que **ninguna tabla con `club_id` se ha quedado
sin policy**. Si sale en rojo, hay una tabla que devuelve filas de otro club: no
abras al público.

**Si `docker compose logs db` no dice `[initdb] Listo`**, el volumen no estaba
vacío y el rol no se ha creado. Pasa `bash scripts/preparar-base.sh`.

---

## 3. Actualizar una versión

```bash
bash scripts/copia-seguridad.sh       # antes de tocar nada
git pull
docker compose build api
docker compose up -d api
bash scripts/migrar.sh                # SIEMPRE, no solo la primera vez
```

Una versión que añade una entidad raíz trae su migración de RLS: si no se pasa
`migrar.sh`, esa tabla nace fuera del aislamiento entre clubes. Todas las
migraciones son idempotentes, así que pasarla de más no cuesta nada.

**Cambiar `PGPASSWORD` en el `.env` no cambia la contraseña del rol**: el initdb
solo corre una vez. Después de cambiarla, `bash scripts/preparar-base.sh`.

---

## 4. Copias de seguridad

```bash
bash scripts/copia-seguridad.sh [directorio]      # por defecto ./copias
bash scripts/restaurar.sh <archivo.dump> <base-nueva>
```

Los dos detectan solos si la base está en Docker o instalada en la máquina.

**Lo que no es evidente, y está comprobado:**

- **`pg_dump` a secas no funciona aquí.** Las tablas llevan `FORCE ROW LEVEL
  SECURITY`, y `pg_dump` apaga RLS por su cuenta: falla con `query would be
  affected by row-level security policy`.
- **Peor: sin `app.club_id`, `pg_dump` termina con éxito y deja una copia válida,
  con todas las tablas y sin una sola fila.** Por eso el script cuenta filas
  contra la base y se niega a dar por buena una copia que no cuadra. Si alguna
  vez copias a mano, cuenta las filas.
- **La copia la hace `cn_app`; la restauración, el superusuario.** Restaurar
  aplica los dueños de las tablas y los permisos por defecto, y eso `cn_app` no
  puede.
- La restauración va siempre a una base **nueva** y comprueba al final que las
  policies no solo están, sino que **filtran**.
- **No va cifrada.** Dentro hay nombres, fechas de nacimiento y tutores de
  menores. Hasta que la tarea S.5 esté hecha, **la copia no sale del servidor**.
- **No se hace sola.** No hay copia programada ni aviso si falla.

---

## 5. Detrás del túnel

- **`docker-compose.yml` no publica ningún puerto**, y así debe quedarse. El
  `docker-compose.override.yml` de desarrollo publica el 8080: está en
  `.gitignore` y **no debe existir en el servidor**. Un `git clone` no lo trae;
  un `scp` de la carpeta, sí.
- **`FORWARD_HEADERS_STRATEGY=framework`.** Sin esto todas las peticiones llegan
  con la IP del proxy, comparten el límite de intentos de login, y los fallos de
  un desconocido dejan al club entero fuera. Al revés, **no lo pongas si la API
  es alcanzable sin el túnel**: entonces esa cabecera la escribe quien quiere.

---

## 6. Trampas que ya han mordido

- **PostgreSQL 18 guarda los datos en otro sitio.** El volumen va en
  `/var/lib/postgresql`, no en `/var/lib/postgresql/data`. Con el montaje
  antiguo, el contenedor se niega a arrancar. Un volumen creado con la 16 no lo
  lee la 18: hace falta `pg_upgrade` o copia y restauración.
- **Dentro del contenedor de postgres, `localhost` entra sin contraseña**
  (`pg_hba`: `127.0.0.1/32 trust`). Cualquier comprobación de contraseña hecha
  por ahí dice que vale aunque no valga. Se comprueba por la red de Docker
  (`-h db`), que es por donde se conecta la API.
- **Un BOM al principio del `.env`** vuelve ilegible la primera variable. Lo
  meten algunos editores de Windows y `Set-Content` de PowerShell 5.
- **Un `$` en un valor del `.env`** lo expande docker compose.
- **`.sh` con finales de línea de Windows**: `bad interpreter: ^M`. Fijados a LF
  en `.gitattributes`.

---

## 7. Lo que sigue sin estar resuelto

- **`data.sql` siembra cuentas y datos de ejemplo en cualquier base nueva**,
  la de producción incluida: `admin` (administrador), `editor`, `tecnico` y
  `usuario`, cinco atletas y seis documentos. Ver el roadmap.
- **En la máquina de desarrollo, `MIGRATION_PASSWORD` no es la contraseña real
  de `postgres`**, así que ahí no se puede restaurar. En Docker no pasa: la base
  se crea con ella.
- Copias cifradas, programadas, con retención y aviso si fallan (S.5, S.2).
