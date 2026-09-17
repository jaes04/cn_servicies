# CLAUDE.md

Contexto permanente de `cn_servicies`. Léelo entero antes de tocar código.
Documentos de apoyo en `docs/`. El plan de trabajo vivo está en
`roadmap-desarrollo.md`.

---

## Qué es este proyecto

Sistema de gestión para clubes de natación españoles, pensado para venderse como SaaS
multi-cliente.

**Arquitectura: monolito modular.** Un solo despliegue, pero separado internamente en
módulos por funcionalidad, de forma que uno pueda extraerse como servicio independiente si
alguna vez necesita escalar aparte. La regla que sostiene eso: **un módulo nunca usa el
repositorio de otro módulo**, pasa por su servicio. Ver `docs/arquitectura.md` §1.

**Estado real: la multi-tenancy está activa.** La Fase 0 está completa. `users`,
`athletes`, `posts`, `guardians` y `consents` llevan `club_id`, y el aislamiento se apoya
en tres capas:

1. **Filtro de Hibernate**, activado por `ClubFilterAspect` al entrar en cada método
   transaccional, con el club del `TenantContext`.
2. **Row Level Security en Postgres**, que es lo que tapa las cargas por clave primaria
   —donde los filtros de Hibernate no se aplican— y por tanto lo que hace que un
   `findById` no devuelva la fila de otro club.
3. **El claim `club_id` del JWT**, puesto por `TenantFilter`. Nunca se resuelve el club
   desde un parámetro, cabecera o cuerpo de la petición.

**Lo que llega sin token lleva el slug del club** (decidido en septiembre de 2026). Cada
frontend lo tiene configurado (`VITE_CLUB_SLUG`) y lo manda: en la ruta del blog público
(`/api/clubs/{slug}/posts/published/**`) y como `clubSlug` en el cuerpo del login y del alta
pública. **El slug no fija el `TenantContext`**: solo acota esa consulta o elige la cuenta.
Un slug inexistente o de un club de baja responde 404. Tareas 0.2.c y 0.3.b del roadmap.

La **Fase S.1.a** también está hecha: tutores, consentimientos granulares y el bloqueo del
alta de menores de 14 sin consentimiento.

El sistema maneja datos personales de menores y almacena documentos que pueden contener
datos de salud. Eso condiciona decisiones técnicas en todo el proyecto — ver
`docs/rgpd.md`.

---

## Stack

**Backend**
- Java 21, Spring Boot 3.4.0, Maven
- `groupId: es.jaes` · `artifactId: cn_servicies`
- PostgreSQL, Hibernate/JPA. Esquema gestionado por `ddl-auto=update` más `schema.sql`
- Autenticación JWT

**Frontend**
- **Repositorio aparte**, en `C:\user\jorge\web\sierra_oeste`. Este repo es solo backend.
- React + Vite, archivos `.jsx`
- Todo cambio en un `*Request` o un `*Response` es un cambio de contrato: avísalo en el
  reporte final

**Infraestructura**
- Docker Compose (API + PostgreSQL)
- Producción prevista: VPS en Hetzner + Cloudflare Tunnel (sin puertos abiertos) +
  Cloudflare Pages para el frontend

No introduzcas librerías, frameworks ni servicios nuevos sin proponerlo antes y esperar
respuesta. Esto incluye utilidades pequeñas.

---

## Entorno local — cosas que ya han costado tiempo

**La base de desarrollo NO es la de Docker.** El `docker-compose.yml` no publica el puerto
de `db`; el override solo expone el 8080 del `api`. Lo que responde en `localhost:5432` es
un **PostgreSQL 18 nativo** instalado como servicio de Windows, y es contra ese que corren
los tests.

**No cargues el `.env` con `. ./.env` en bash.** Las contraseñas contienen `$` y bash las
expande: llega a la aplicación un valor distinto del que hay en el archivo, y el síntoma es
un `password authentication failed` que parece un problema de la base. Hay que leerlo línea
a línea sin interpretar:

```bash
while IFS= read -r line || [ -n "$line" ]; do
  line=${line%$'\r'}; case "$line" in ''|'#'*) continue;; esac
  k=${line%%=*}; v=${line#*=}; export "$k=$v"
done < .env
```

Ese mismo `$` es un problema pendiente para el despliegue: **`docker compose` también
interpreta `$` dentro de los valores del `.env`** y hay que escribirlo `$$`, o el
contenedor de Postgres arrancará con una contraseña distinta de la esperada.

**`MIGRATION_PASSWORD` del `.env` no es la contraseña real de `postgres`.** Hoy no bloquea
nada, porque las migraciones de RLS las puede ejecutar `cn_app` —es dueño de las tablas, ya
que las crea Hibernate—, pero bloqueará el paso a Flyway.

---

## Comandos

Verifica los scripts reales en `pom.xml` antes de asumir:

```bash
# Backend (con el .env cargado como se explica arriba)
./mvnw spring-boot:run
./mvnw test
./mvnw clean package

# Stack local completo
docker compose up -d
docker compose logs -f
docker compose down          # sin -v: borrar el volumen de Postgres rompe la inicialización

# Migraciones de RLS: una vez por entorno, después de arrancar la aplicación
"/c/Program Files/PostgreSQL/18/bin/psql.exe" -h localhost -U cn_app -d cn_test \
  -f migrations/S.1-guardians-rls.sql
```

Hay además `seed.sh` en la raíz para poblar datos. Revisa qué hace antes de ejecutarlo.

**Los tests necesitan un PostgreSQL de verdad y conectarse como `cn_app`, no como
superusuario.** Postgres deja que los superusuarios se salten las policies: ejecutados como
`postgres`, los tests de aislamiento pasarían en verde sin demostrar nada. `TenantIsolationTest`
comprueba eso lo primero.

---

## Modelo de dominio actual

**El dominio está nombrado en inglés.** `Athlete`, no `Atleta`. Mantén ese idioma en todo
el código nuevo; no mezcles.

| Entidad | Notas |
|---|---|
| `Club` | Tenant raíz. `name`, `slug`, `active`. Sin borrado lógico: dar de baja es `active = false` |
| `User` | Cuenta de acceso. Lleva `club_id`. `username` único **por club**; `email` todavía único global |
| `Role` | Rol global. Enum `RoleName`: `ROLE_ADMIN`, `ROLE_EDITOR`, `ROLE_USER`, `ROLE_TECHNICAL_STAFF` |
| `Athlete` | Deportista. Lleva `club_id`. `dni` **opcional** (DNI, NIE o pasaporte, hasta 20) y único por club; sin él, el duplicado se detecta por nombre, apellidos y nacimiento. `birthDate` es `LocalDate`. Frecuentemente menor |
| `Gender` / `GenderEntity` | Enum `MALE`, `FEMALE` y su tabla de catálogo. `Athlete` apunta a la entidad, no al enum |
| `UserAthlete` | Vínculo usuario–atleta con tipo `TUTOR` o `ATHLETE`. Es el vínculo de **acceso** |
| `AthleteInviteKey` | Clave de invitación para vincular un usuario a un atleta |
| `Guardian` | Tutor legal. Lleva `club_id`. **Es una persona, no una cuenta**: `user` es opcional |
| `AthleteGuardian` | Vínculo atleta–tutor con el parentesco. Varios por atleta y por tutor |
| `Consent` | Consentimiento por finalidad. Lleva `club_id`. **Append-only**: ver abajo |
| `AthleteDocument` | Documento subido. Tipos: `MEDICAL`, `TRAINING`, `COMPETITION`, `CONSENT`, `IDENTIFICATION`, `OTHER` |
| `CompetitionResult` | Marca de competición. Soporta parciales vía autorreferencia |
| `Post` / `PostImage` / `Comment` | Blog público del club. `Post` lleva `club_id`; su `slug` ya no es único |

Detalle completo, relaciones y campos en `docs/arquitectura.md`.

**No existen todavía**: `Season`, `Group`, `GroupSchedule`, `Session`, `Attendance`,
`MedicalCertificate`. Todo el bloque de gestión de entrenamientos está sin construir.

### Qué llevan `club_id` y qué no

Solo las **entidades raíz**: `users`, `athletes`, `posts`, `guardians` y `consents`. Las
hijas llegan a su club por el padre y no lo repiten: `comments`, `post_images`,
`competition_results`, `athlete_documents`, `user_athletes`, `athlete_invite_keys` y
`athlete_guardians`.

`consents` es la excepción deliberada: es hija de `Athlete` y aun así lleva `club_id` y
policy propia, porque sostiene la licitud de todo el tratamiento de un menor y merece que
el aislamiento lo imponga Postgres.

### Tutores y consentimiento

`Guardian` y `UserAthlete` con `type = TUTOR` **no son lo mismo y no se sustituyen**:
`UserAthlete` es quién puede *ver* los datos del atleta; `Guardian` es quién *otorga* el
consentimiento, tenga cuenta o no.

El registro de `Consent` es **append-only**: nada se actualiza ni se borra. Revocar es
escribir `revokedAt`, y volver a consentir es una fila nueva. El historial completo es la
prueba que exige el RGPD art. 7.1. Una negativa se guarda igual que una concesión.

La edad de consentimiento en España son **14 años** (LOPDGDD art. 7), no 16, y se mide
**en la fecha de la decisión, no en la de hoy**: `ConsentService.requiresGuardianConsent`
para la primera pregunta, `wasUnderConsentAgeAtDecision` para la segunda.

---

## Reglas innegociables

1. **Toda entidad raíz nueva nace con `club_id`**, not null y con índice, **y con su
   migración de RLS**. Sin la policy, la tabla nace fuera del aislamiento: el filtro de
   Hibernate la tapa en las consultas normales, pero un `findById` devuelve la fila ajena.
   Las tablas hijas no lo llevan, salvo decisión explícita como la de `consents`.

2. **Un módulo no toca el repositorio de otro módulo.** Pasa por su `Service`. Es la
   regla que hace que la separación en paquetes signifique algo.

3. **Ninguna consulta puede cruzar clubes.** Hoy lo garantizan el filtro de Hibernate y
   RLS, pero no introduzcas patrones que lo pongan difícil: queries nativas sin `WHERE`,
   joins amplios, endpoints que aceptan IDs sin validar propiedad.

4. **`AthleteDocument` de tipo `MEDICAL` es la pieza más sensible del sistema.** No
   amplíes su funcionalidad, no añadas campos de texto libre, no lo expongas en listados
   generales, no lo incluyas en exportaciones sin preguntar. Ver `docs/rgpd.md`.

5. **Nunca commitees secretos.** Ni en código, ni en tests, ni en fixtures, ni en
   ejemplos de documentación.

6. **Si añades una variable a `.env.example`, dilo en voz alta al terminar.** Hay que
   copiarla a mano al `.env` de cada entorno y esto ya ha roto despliegues antes.

7. **Datos de catálogo estáticos van en `data.sql`**, con inserts idempotentes, nunca en
   `schema.sql`. Requiere `spring.jpa.defer-datasource-initialization=true`.

8. **No reescribas ni borres código fuera del alcance de la tarea.** Si ves algo mal,
   señálalo al final en vez de arreglarlo por tu cuenta.

9. **Renombrar un campo de entidad rompe cosas en silencio.** Las `*Specification`
   referencian campos por cadena de texto y el proyecto compila igual. Revísalas a mano
   siempre que cambies un nombre de campo.

10. **Cambios de esquema se proponen antes de aplicarse**, incluidos los que Hibernate
    haría automáticamente. Ten en cuenta que **arrancar la aplicación o pasar los tests ya
    los aplica**: con `ddl-auto=update`, proponer y ejecutar están a un `./mvnw test` de
    distancia.

---

## Trampas conocidas

**`@Data` de Lombok genera un `toString()` que recorre las relaciones `LAZY`.** En cuanto
una de esas relaciones está tapada por RLS, cualquier cosa que imprima la entidad —un log,
un mensaje de fallo de un test, el depurador— revienta con `EntityNotFoundException` en vez
de decir lo que pasaba. Está en todas las entidades del proyecto. En los tests, asierta
sobre `Optional.isPresent()` y no sobre el `Optional`, o el mensaje de fallo se lo lleva
por delante.

**Lo que va dentro de un `CREATE TABLE` en `schema.sql` no llega a la base.** Con
`defer-datasource-initialization=true`, ese archivo se ejecuta **después** de que
Hibernate haya creado el esquema, así que todos sus `CREATE TABLE IF NOT EXISTS` no hacen
nada — y con ellos se pierden en silencio las claves foráneas con su `ON DELETE`, las
`CONSTRAINT ... UNIQUE` y los `DEFAULT`. Lo que queda es lo que Hibernate deduce de las
anotaciones: sin acción de borrado y con nombres generados distintos en cada base. Medido:
se declaran 24 `ON DELETE` y hay 8; se declaran 25 `DEFAULT` y hay 11.

Sí se aplican las **sentencias sueltas**, que no dependen de que la tabla exista:
`CREATE INDEX IF NOT EXISTS` y `ALTER TABLE`. Por eso los índices únicos del archivo sí
están y las cascadas no. **Si algo tiene que existir de verdad, escríbelo suelto.**
Recrear la base no lo arregla: en una base vacía Hibernate sigue yendo primero.

**No declares un índice único en los dos sitios.** Ponerlo en `schema.sql` *y* en el
`@Table(uniqueConstraints=...)` de la entidad crea dos restricciones equivalentes, y la de
Hibernate lleva un nombre aleatorio que no se puede referenciar en una migración. Va solo
en `schema.sql`, con nombre `uk_*`. `user_athletes` y `athlete_guardians` tienen el
problema contrario —solo existe la generada— y está anotado en la tarea 2.6.

**Una cuenta se identifica por club y username, nunca por username solo.** El username es
único por club, así que `UserRepository.findByUsername` es ambiguo en cuanto dos clubes
tienen un `admin`. Solo es fiable dentro de una petición autenticada, donde el filtro y RLS
lo acotan al club del token. Todo lo que autentica —login, alta pública, `JwtAuthFilter`,
refresco— usa `UserDetailsServiceImpl.loadUserByClubAndUsername`; `loadUserByUsername`
lanza a propósito. El club sale del slug que manda el frontend (`clubSlug`) en lo público,
y del claim `club_id` en lo que lleva token.

---

## Decisiones abiertas

No las cierres tú. Si una tarea depende de una, pregunta.

- **Documentos subidos**: desde el bloque 3a la subida de `AthleteDocument` está apagada
  por defecto (`app.documents.upload.enabled`) y el club registra la entrega de papeles en
  `DocumentDelivery`. Sigue abierto qué se hace con los archivos que ya existan y si algún
  día se enciende —antes harían falta cifrado en reposo, directorio propio y registro de
  accesos—. Ver `docs/rgpd.md` §1.
- **`User.email`**: único global, igual que lo era `username`. Al pasar el username a único
  por club, el email queda como el nuevo obstáculo para que una persona use el mismo correo
  en dos clubes. Sin resolver.
- **Roles de club**: `Role` es global. Con multi-tenancy hará falta que `ROLE_ADMIN`,
  `ROLE_EDITOR`, `ROLE_USER` y `ROLE_TECHNICAL_STAFF` sean por club. Sin resolver.
- **Sacar el esquema de `ddl-auto`**, probablemente con Flyway. Mientras `cn_app` sea dueño
  de las tablas puede desactivar sus propias policies, así que la separación entre el rol
  de aplicación y el de migraciones no es real todavía. Pendiente antes de producción.

  **No corre prisa mientras el servicio no esté desplegado** (decidido en septiembre de
  2026): sin datos reales que migrar, el coste de hacerlo ahora es el mismo que el de
  hacerlo más tarde, y hacerlo después evita rehacerlo cada vez que cambia el modelo.
  Cuando se haga, resuelve de una vez tres cosas que hoy están sueltas: la separación de
  roles de arriba, las restricciones que `schema.sql` declara y no existen (tarea 2.6, que
  desaparecería), y `MIGRATION_PASSWORD`, que hoy no es la contraseña real de `postgres` y
  bloqueará el paso. **El día que haya un despliegue en producción, esto deja de ser
  opcional**: a partir de ahí cada cambio de esquema es una migración sobre datos vivos, y
  `ddl-auto=update` no sabe hacer eso.
- **Dominio de producción**: pendiente de decisión del club.

---

## Cómo trabajar en este proyecto

- **Plan primero.** Antes de escribir código, explica qué vas a tocar y por qué. Espera
  confirmación si el cambio afecta a más de dos o tres archivos, al esquema, o a
  seguridad.
- **Bloques pequeños.** El trabajo se organiza en bloques de 2–6 tareas relacionadas que
  terminan en algo probable a mano.
- **Un test que no has visto fallar no demuestra nada.** Cuando escribas uno que protege
  una regla —una transacción que deshace, una policy que tapa—, comprueba que se pone en
  rojo al quitar lo que protege, y déjalo dicho en el reporte.
- **Termina con un reporte breve**: archivos tocados, cómo probarlo, qué queda pendiente.
- **Si algo de este contexto contradice el código real, dilo** en vez de seguirlo a
  ciegas.
- **Si te falta información, pregunta.** No inventes convenciones, entidades ni endpoints
  sin verificarlos en el repositorio.

---

## Documentos de apoyo

| Archivo | Cuándo leerlo |
|---|---|
| `roadmap-desarrollo.md` | Qué toca hacer, en qué orden, y las decisiones ya tomadas en cada fase |
| `docs/arquitectura.md` | Modelo de datos y despliegue |
| `docs/convenciones.md` | Estructura de paquetes, naming, DTOs, errores, tests |
| `docs/rgpd.md` | Cualquier cosa que toque datos personales, menores, documentos o logs |

> **`docs/arquitectura.md` y `docs/convenciones.md` tienen partes desactualizadas**, las
> mismas que tenía este archivo: describen la multi-tenancy como pendiente y dicen que solo
> existe un archivo de tests, cuando hay seis clases y 36 tests. Están sin repasar.
