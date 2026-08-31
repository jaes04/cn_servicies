# CLAUDE.md

Contexto permanente de `cn_servicies`. Léelo entero antes de tocar código.
Documentos de apoyo en `docs/`.

---

## Qué es este proyecto

Sistema de gestión para clubes de natación españoles, pensado para venderse como SaaS
multi-cliente.

**Arquitectura: monolito modular.** Un solo despliegue, pero separado internamente en
módulos por funcionalidad, de forma que uno pueda extraerse como servicio independiente si
alguna vez necesita escalar aparte. La regla que sostiene eso: **un módulo nunca usa el
repositorio de otro módulo**, pasa por su servicio. Ver `docs/arquitectura.md` §1.

**Estado real: es todavía una aplicación mono-club.** El objetivo es multi-tenant, pero
no está implementado. No asumas que existe aislamiento entre clubes — no existe. Ver
`docs/arquitectura.md` §2.

El sistema maneja datos personales de menores y almacena documentos que pueden contener
datos de salud. Eso condiciona decisiones técnicas en todo el proyecto — ver
`docs/rgpd.md`.

---

## Stack

**Backend**
- Java 21, Spring Boot 3.4.0, Maven
- `groupId: es.jaes` · `artifactId: cn_servicies`
- PostgreSQL 16, Hibernate/JPA
- Autenticación JWT

**Frontend**
- **Repositorio aparte.** Este repo es solo backend.
- React + Vite, archivos `.jsx`
- Todo cambio en un `*Response` es un cambio de contrato: avísalo en el reporte final

**Infraestructura**
- Docker Compose (API + PostgreSQL)
- Producción prevista: VPS en Hetzner + Cloudflare Tunnel (sin puertos abiertos) +
  Cloudflare Pages para el frontend

No introduzcas librerías, frameworks ni servicios nuevos sin proponerlo antes y esperar
respuesta. Esto incluye utilidades pequeñas.

---

## Comandos

Verifica los scripts reales en `pom.xml` / `package.json` antes de asumir:

```bash
# Backend
./mvnw spring-boot:run
./mvnw test
./mvnw clean package

# Stack local completo
docker compose up -d
docker compose logs -f
docker compose down          # sin -v: borrar el volumen de Postgres rompe la inicialización
```

Hay además `seed.sh` en la raíz para poblar datos. Revisa qué hace antes de ejecutarlo.

---

## Modelo de dominio actual

**El dominio está nombrado en inglés.** `Athlete`, no `Atleta`. Mantén ese idioma en todo
el código nuevo; no mezcles.

Entidades que existen hoy:

| Entidad | Notas |
|---|---|
| `User` | Cuenta de acceso. `username`, `email`, `passwordHash`, `roles`, `blocked` |
| `Role` | Rol global. Enum `RoleName`: ADMIN, EDITOR, USER, TECHNICAL_STAFF |
| `Athlete` | Deportista. Incluye `dni` y `birthDate`. Frecuentemente menor |
| `UserAthlete` | Vínculo usuario–atleta con tipo TUTOR o ATHLETE |
| `AthleteInviteKey` | Clave de invitación para vincular un usuario a un atleta |
| `AthleteDocument` | Documento subido. Tipos: MEDICAL, TRAINING, COMPETITION, CONSENT, IDENTIFICATION, OTHER |
| `CompetitionResult` | Marca de competición. Soporta parciales vía autorreferencia |
| `Post` / `PostImage` / `Comment` | Blog público del club |

Detalle completo, relaciones y campos en `docs/arquitectura.md`.

**No existen todavía**: `Club`, `Season`, `Group`, `GroupSchedule`, `Session`,
`Attendance`. Todo el bloque de gestión de entrenamientos está sin construir.

---

## Reglas innegociables

1. **Toda entidad nueva nace con `club_id`.** Aunque la multi-tenancy no esté activa
   todavía, añadirlo después cuesta mucho más. Not null, con índice.

2. **Un módulo no toca el repositorio de otro módulo.** Pasa por su `Service`. Es la
   regla que hace que la separación en paquetes signifique algo.

3. **Ninguna consulta puede cruzar clubes.** Cuando el filtro de tenancy esté activo,
   esto lo garantiza Hibernate; hasta entonces, no introduzcas patrones que lo pongan
   difícil (queries nativas sin `WHERE`, joins amplios, endpoints que aceptan IDs sin
   validar propiedad).

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
    haría automáticamente.

---

## Cómo trabajar en este proyecto

- **Plan primero.** Antes de escribir código, explica qué vas a tocar y por qué. Espera
  confirmación si el cambio afecta a más de dos o tres archivos, al esquema, o a
  seguridad.
- **Bloques pequeños.** El trabajo se organiza en bloques de 2–6 tareas relacionadas que
  terminan en algo probable a mano.
- **Termina con un reporte breve**: archivos tocados, cómo probarlo, qué queda pendiente.
- **Si algo de este contexto contradice el código real, dilo** en vez de seguirlo a
  ciegas.
- **Si te falta información, pregunta.** No inventes convenciones, entidades ni endpoints
  sin verificarlos en el repositorio.

---

## Cambios acordados — PENDIENTES DE EJECUTAR

**`Athlete.birthDate` pasa de `LocalDateTime` a `LocalDate`.** Es un cambio de tipo, no de
nombre. Arrastra:

- La serialización JSON (desaparece la parte horaria) → contrato de API, afecta al
  frontend
- `AthleteSpecification`, si filtra por fecha de nacimiento
- Cualquier cálculo de edad o categoría deportiva

Se hace como tarea propia, en un solo bloque, con el proyecto compilando al final.

Pendiente de verificar en el código antes de tocar nada: si el campo de borrado lógico se
llama `deleteAt` o `deletedAt`. Si es lo primero, se renombra en la misma pasada.

**`User.username` pasa a ser único por club, no único global.** Decisión tomada; se
ejecuta en la tarea 0.2 del roadmap. El índice único de `username` se migra a
`(club_id, username)`. Arrastra:

- Cada cuenta pertenece a **un** club. Una persona en dos clubes necesita dos cuentas:
  no hay cuenta compartida entre clubes.
- **El login deja de poder resolverse solo con el username**, porque puede haber un
  `admin` por club. `UserDetailsServiceImpl.loadUserByUsername` recibe hoy solo la
  cadena y llama a `findByUsername`, que pasará a devolver varias filas. Hay que
  resolver el club antes de autenticar — por subdominio, por slug en la petición o por
  un selector en el formulario. Es la tarea 0.3, y **afecta al contrato de login**:
  cambio de API que hay que coordinar con el frontend.
- `UserRepository.findByUsername` y `existsByUsername` quedan ambiguos: pasan a
  necesitar el club como parámetro.

---

## Decisiones abiertas

No las cierres tú. Si una tarea depende de una, pregunta.

- **Multi-tenancy**: cuándo se introduce `Club` y se migran las entidades existentes.
  Cuanto más código se acumule antes, más cara es. Ver `docs/arquitectura.md` §1.
- **Documentos médicos**: si `AthleteDocument` sigue almacenando archivos de tipo
  `MEDICAL` tal cual, o si el certificado federativo pasa a ser una entidad aparte solo
  con metadatos. Ver `docs/rgpd.md` §1.
- **`User.email`**: hoy es único global, igual que lo era `username`. Al pasar el
  username a único por club, el email queda como el nuevo obstáculo para que una
  persona use el mismo correo en dos clubes. Sin resolver.
- **Roles**: `Role` es global hoy. Con multi-tenancy hará falta que sean por club.
- **Dominio de producción**: pendiente de decisión del club.

---

## Documentos de apoyo

| Archivo | Cuándo leerlo |
|---|---|
| `docs/arquitectura.md` | Modelo de datos real, multi-tenancy pendiente, despliegue |
| `docs/convenciones.md` | Estructura de paquetes, naming, DTOs, errores, tests |
| `docs/rgpd.md` | Cualquier cosa que toque datos personales, menores, documentos o logs |