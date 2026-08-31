# Arquitectura

Distingue en todo momento **lo que existe** de **lo que está planificado**. Confundirlos
es la forma más rápida de escribir código equivocado en este proyecto.

---

## 1. Monolito modular

La aplicación es un **monolito**, y así se queda. La separación en paquetes por
funcionalidad no es estilo: es la preparación para poder **extraer un módulo como servicio
independiente** si algún día uno necesita escalar por su cuenta.

Esa intención cambia lo que cuenta como código correcto aquí.

### La regla que sí hay que cumplir

**Un módulo no toca el repositorio de otro módulo.** Si `post` necesita datos de `user`,
llama a `UserService`, nunca a `UserRepository`.

Es barata de mantener, se detecta a simple vista en una revisión, y es lo que hace que un
módulo tenga una frontera real en lugar de una carpeta. Sin esto, la separación por
paquetes es decorativa.

Consecuencias prácticas:

- Cada módulo es dueño de sus entidades y sus repositorios. Nadie más los usa.
- El servicio del módulo es su superficie pública. Todo lo demás debería ser detalle
  interno.
- Para efectos secundarios entre módulos (notificar, registrar, reaccionar a algo),
  prefiere eventos de Spring a llamadas directas. Reduce el acoplamiento en la dirección
  que importa.
- Nada de utilidades compartidas que acaben conociendo a todos los módulos. `config/` es
  transversal por necesidad; que no se convierta en un cajón.

### El modelo de extracción previsto: base de datos compartida

Si algún día se extrae un módulo, **seguirá apuntando a la misma base de datos**. No es
el modelo de manual (un servicio, una base de datos), y esa decisión cambia todo lo demás.

**Consecuencia directa: las relaciones JPA entre módulos no son un problema.**
`AthleteDocument → Athlete`, `Post → User` y `UserAthlete → Athlete, User` son legítimas y
se quedan. Con base compartida la clave foránea sigue existiendo, los joins siguen
funcionando y la navegación entre entidades no se rompe al extraer. No las conviertas en
UUIDs sueltos: sería pagar un coste diario a cambio de nada.

#### Qué resuelve este modelo

Escalar cómputo por separado. Si el cuello de botella está en la aplicación (I/O de
archivos, memoria, ancho de banda), extraer ese servicio y levantar varias instancias
funciona. Es exactamente el caso de `DocumentStorageService`.

#### Qué no resuelve, y hay que tener presente

- **El esquema pasa a ser compartido.** Ninguna migración se puede hacer mirando un solo
  proyecto: hay que coordinarla con todos los servicios desplegados y ordenar los
  despliegues. Es el coste real de este modelo, y elimina la independencia de despliegue
  que suele motivar la separación.
- **La frontera deja de ser técnica.** Hoy un acceso indebido a otro módulo se ve en el
  código. Con dos servicios sobre la misma base, uno puede escribir en las tablas del otro
  sin que nada lo impida ni lo señale. La disciplina pasa a ser solo convencional.
- **Propiedad de las clases `@Entity`.** O se duplican en ambos proyectos y se
  desincronizan con el tiempo, o se comparten en un JAR común y entonces ambos servicios
  quedan acoplados a ese artefacto. No hay opción cómoda; decidir cuál antes de extraer.
- **Solo un servicio puede tener `ddl-auto` activo.** Dos Hibernate inicializando el mismo
  esquema es un problema garantizado.
- **Contención compartida.** Un pool de conexiones, un espacio de bloqueos. Una consulta
  lenta en el servicio extraído afecta al núcleo.

#### Regla mientras tanto

Nada de esto cambia el día a día. Lo único que hay que sostener es **la regla de la
frontera**: un módulo no toca el repositorio de otro. Con base compartida esa regla es aún
más importante, porque el día de la extracción será lo único que quede separando los
módulos.

### Qué se extraería realmente

Merece la pena aplicar más disciplina donde la extracción es plausible, y menos donde no
lo es:

| Módulo | ¿Candidato? | Por qué |
|---|---|---|
| Almacenamiento de archivos | **Sí** | I/O pesado, escala distinto al resto, frontera natural |
| Blog público (`post`, `comment`) | **Sí** | Lectura intensiva, cacheable, sin acoplamiento al núcleo |
| `athlete`, `user`, `auth` | No | Son el núcleo. Extraerlos es partir el dominio, no escalarlo |
| `competition_result` | Poco probable | Volumen bajo, muy acoplado a `athlete` |

`DocumentStorageService` e `ImageStorageService` ya están separados: eso está bien y hay
que mantenerlo. Son la frontera más limpia que tiene el proyecto ahora mismo.

### El riesgo real

El fallo típico de esta estrategia no es que la separación se rompa: es **pagar el coste
del diseño distribuido sin cobrar nunca el beneficio**. Si dentro de dos años sigue siendo
un monolito —que es el resultado más probable, y perfectamente bueno— toda la disciplina
que no fuera barata habrá sido pérdida neta.

Por eso: la regla de los repositorios sí, siempre. El resto, solo cuando haya un motivo
concreto y medido.

**Nota sobre multi-tenancy**: cuando llegue, el `club_id` atraviesa todos los módulos por
igual, y con base de datos compartida también atravesaría a un servicio extraído. Ese
servicio tendría que establecer el contexto de club en su propia conexión para que las
políticas de Row Level Security funcionen. Es un motivo más para no extraer nada antes de
tener la tenancy resuelta.

---

## 2. Multi-tenancy — PLANIFICADA, NO IMPLEMENTADA

### Situación actual

No existe la entidad `Club`. Ninguna entidad tiene `club_id`. No hay filtro de Hibernate,
no hay Row Level Security, no hay aislamiento. **Hoy esto es una aplicación de un solo
club.**

No escribas código que dé por hecho que el aislamiento existe, ni tests que lo verifiquen
contra algo que no está.

### Objetivo

Shared schema con discriminador `club_id`: una base de datos, un esquema, todas las filas
etiquetadas con su club. Se descartaron schema-per-tenant y database-per-tenant por coste
operativo y complejidad de migraciones.

Tres capas de defensa, previstas las tres:

1. **Filtro de Hibernate** activado por sesión con el `club_id` del usuario autenticado.
   Defensa principal.
2. **Row Level Security de PostgreSQL** sobre las tablas con `club_id`. Red de seguridad
   para lo que esquive a Hibernate.
3. **Validación en la capa de servicio**: al recibir un ID desde fuera, comprobar que el
   recurso pertenece al club del usuario.

### Lo que hay que hacer cuando se aborde

Todas estas entidades necesitarán `club_id`: `User`, `Athlete`, `AthleteDocument`,
`AthleteInviteKey`, `UserAthlete`, `CompetitionResult`, `Post`, `PostImage`, `Comment`.

Puntos que van a doler y conviene tener presentes desde ya:

- **`Role` es global.** `RoleName` no está scopeado por club. Un ADMIN hoy es ADMIN de
  todo. Habrá que decidir si el rol pasa a ser por club o si se introduce una entidad
  intermedia usuario–club–rol.
- **Hay un administrador de plataforma** — decidido. `adminjaes`, con rol propio
  `ROLE_PLATFORM_ADMIN`, ve todos los clubes: da de alta clubes y da soporte. Es la única
  excepción legítima a las tres capas de defensa de arriba, y por eso vive en un rol
  aparte, con auditoría de cada acceso cruzado, en lugar de repartirse como un privilegio
  más de `ROLE_ADMIN`.
- **`User.username` pasa a ser único por club** — decidido. El índice único se migra a
  `(club_id, username)`, de forma que cada club pueda tener su propio `admin`. La
  contrapartida es que una cuenta pertenece a un solo club: quien esté en dos clubes
  tendrá dos cuentas. Y el login ya no se resuelve solo con el username, hay que
  determinar el club antes de autenticar. `User.email`, que sigue siendo único global,
  queda como la siguiente decisión de este mismo bloque.
- **`Post` y `Comment`** son contenido público. El blog multi-club implica resolver qué
  club sirve cada dominio o ruta.
- Los datos existentes habrá que asignarlos a un club por defecto en la migración.

### Mientras tanto

**Toda entidad nueva nace con `club_id` not null e indexado.** Añadirlo después, con
datos en producción y frontend acoplado, cuesta un orden de magnitud más.

---

## 3. Modelo de datos actual

Identificadores: **UUID** en todas las entidades salvo `Role`, que usa `Long`.

Auditoría: `createdAt` / `updatedAt` según entidad. Borrado lógico mediante `deleteAt` *(verificar nombre exacto)*
(nota: el nombre está en presente; lo habitual sería `deletedAt`).

### Identidad y acceso

**`User`**
`id` (uuid) · `username` · `email` · `passwordHash` · `blocked` (bool) · `roles`
(HashSet) · `profilePhoto` · `createdAt` · `updatedAt` · `deleteAt` *(verificar nombre exacto)*

**`Role`**
`id` (Long) · `name` (`RoleName`)
`RoleName`: `ROLE_ADMIN`, `ROLE_EDITOR`, `ROLE_USER`, `ROLE_TECHNICAL_STAFF`

Roles globales, sin ámbito de club. Ver §2.

### Atletas

**`Athlete`**
`id` (uuid) · `firstName` · `lastName` · `birthDate` (LocalDateTime) · `dni` ·
`gender` · `createdAt` · `updatedAt` · `deleteAt` *(verificar nombre exacto)*

**Género**: el paquete contiene `Gender.java`, `GenderEntity.java` y `GenderRepository`.
Que exista un repositorio implica que el género está modelado como **tabla de catálogo**,
no como enum simple. Verifica cuál de las dos clases es la entidad y cuál el enum antes de
tocar nada aquí — la nomenclatura es confusa y es fácil equivocarse.

Si es catálogo, se siembra desde `data.sql` con inserts idempotentes.

`dni` es un identificador oficial: dato personal de categoría sensible en la práctica.
`birthDate` es hoy `LocalDateTime` para una fecha sin hora; **está acordado pasarlo a
`LocalDate`**, pero el cambio aún no se ha hecho.

**`UserAthlete`** — vínculo entre cuenta y deportista
`id` (uuid) · `user` · `athlete` · `type` (`UserAthleteType`) · `createdAt`
`UserAthleteType`: `TUTOR`, `ATHLETE`

Es la entidad que sostiene la relación tutor–menor. Tiene peso legal, no solo funcional:
ver `rgpd.md` §2.

**`AthleteInviteKey`** — invitación para vincular un usuario a un atleta
`id` (uuid) · `keyValue` · `athlete` · `type` (`UserAthleteType`) · `expiresAt` ·
`used` (bool) · `createdAt`

`keyValue` es un secreto de un solo uso: no debe aparecer en logs, ni en respuestas de
listado, ni en URLs que se registren.

### Documentos

**`AthleteDocument`**
`id` (uuid) · `title` · `type` (`AthleteDocumentType`) · `fileName` · `originalFileName` ·
`athlete` · `uploadedBy` (User) · `createdAt`
`AthleteDocumentType`: `MEDICAL`, `TRAINING`, `COMPETITION`, `CONSENT`, `IDENTIFICATION`,
`OTHER`

**Esta es la entidad de mayor riesgo del sistema.** Almacena archivos reales asociados a
menores, y uno de sus tipos es documentación médica. Lee `rgpd.md` §1 antes de tocarla.

No tiene `deleteAt` *(verificar nombre exacto)*: el borrado es físico. Eso afecta a si el archivo en disco se elimina
también y a si queda huella de quién borró qué.

### Competición

**`CompetitionResult`**
`id` (uuid) · `athlete` · `competitionDate` (LocalDate) · `distanceMeters` ·
`stroke` (`Stroke`) · `poolLength` · `partial` (bool) ·
`resultTimeMillis` (Long) · `finalResult` (autorreferencia) · `createdAt` · `updatedAt` ·
`deleteAt` *(verificar nombre exacto)*
`Stroke`: `FREESTYLE`, `BACKSTROKE`, `BREASTSTROKE`, `BUTTERFLY`, `MEDLEY`

Los parciales se modelan como registros con `partial = true` que apuntan al resultado
final vía `finalResult`. Al consultar marcas, filtra por `partial` o duplicarás tiempos.

`resultTimeMillis` como `Long` en milisegundos es la decisión correcta; no la cambies a
tipos de fecha/hora.

### Contenido público

**`Post`**
`id` (uuid) · `title` · `content` · `slug` · `status` (`PostStatus`) · `publishedAt` ·
`author` (User) · `images` (`List<PostImage>`) · `createdAt` ·
`updatedAt` · `deleteAt` *(verificar nombre exacto)*
`PostStatus`: `DRAFT`, `PUBLISHED`, `DELETED`

**`PostImage`** *(nombrada `PostImange` en el código)*
`id` (uuid) · `fileName` · `originalFileName` · `post` · `createdAt`

**`Comment`**
`id` (uuid) · `content` · `post` · `author` (User) · `blocked` (bool) ·
`createdAt` · `updatedAt` · `deleteAt` *(verificar nombre exacto)*

Ojo: hay dos mecanismos de borrado solapados en `Post` — `status = DELETED` y `deleteAt` *(verificar nombre exacto)*.
Verifica cuál manda antes de escribir consultas.

Contenido público con comentarios implica moderación y, si los autores pueden ser menores,
cautela adicional.

### Cambios acordados — pendientes de ejecutar

**`Athlete.birthDate` pasa de `LocalDateTime` a `LocalDate`.**

Alcance:

- Campo de la entidad y accesores
- Columna en base de datos
- `AthleteRequest` y `AthleteResponse`
- `AthleteSpecification`, si filtra por esa fecha — **referencia el campo por cadena de
  texto, así que el compilador no te avisa**
- Cualquier cálculo de edad o categoría deportiva
- **Frontend (repo aparte)**: cambia la serialización JSON, desaparece la parte horaria.
  Todo lo que parsee o formatee esa fecha hay que revisarlo a mano.

Como todavía no hay despliegue en producción, no hace falta migración de datos: basta con
recrear el esquema local.

Pendiente de verificar antes de tocar nada: si el campo de borrado lógico se llama
`deleteAt` o `deletedAt` en el código. Si es lo primero, se renombra en la misma pasada
(afecta a `User`, `Athlete`, `Post`, `Comment`, `CompetitionResult`, a sus repositorios y
a las Specifications).

`GenderEntity` queda fuera de alcance.

---

## 4. Lo que no existe todavía

Todo el bloque de gestión de entrenamientos está sin construir:

| Entidad prevista | Función |
|---|---|
| `Club` | Tenant raíz |
| `Season` | Ciclo anual del club |
| `Group` | Grupo de entrenamiento dentro de una temporada |
| `AthleteGroup` | Pertenencia **histórica** de un atleta a un grupo |
| `GroupSchedule` | Horario recurrente del grupo |
| `Session` | Entrenamiento en una fecha concreta |
| `Attendance` | Presencia de un atleta en una sesión |

Reglas de diseño acordadas para cuando se construyan:

- **La pertenencia a grupo es histórica, no un puntero.** Nunca preguntes "¿en qué grupo
  está este atleta?" sin contexto temporal. Al cambiar de grupo se cierra el registro
  anterior y se abre uno nuevo; no se sobrescribe.
- **Las sesiones se generan automáticamente** desde los horarios recurrentes dentro del
  rango de la temporada. La generación debe ser idempotente, debe respetar sesiones
  editadas o canceladas a mano, y no debe borrar sesiones pasadas ni sus asistencias al
  cambiar un horario.
- Festivos y cierres del club son excepciones sobre el calendario generado.

---

## 5. Autenticación

- JWT.
- Los intentos de autenticación se registran con IP. **La IP es dato personal** (TJUE,
  *Breyer*), así que ese registro tiene finalidad acotada y retención limitada — ver
  `rgpd.md` §4.
- Cuando exista multi-tenancy, el `club_id` del contexto de seguridad será la única
  fuente de verdad para el filtro. Nunca se acepta un `club_id` enviado por el cliente.

---

## 6. Inicialización de datos

- **Esquema**: Hibernate / `schema.sql`.
- **Catálogos estáticos**: `data.sql`, con inserts idempotentes
  (`ON CONFLICT DO NOTHING` o equivalente). Aquí entran los `Role`, que tienen `id` Long
  y necesitan sembrarse.
- Requiere `spring.jpa.defer-datasource-initialization=true`, o `data.sql` se ejecuta
  antes de que existan las tablas.
- Datos de prueba nunca en el `data.sql` de producción.

---

## 7. Despliegue

```
Usuario
  │
  ├── Frontend  ──►  Cloudflare Pages          (build de Vite, VITE_API_URL apunta a la API)
  │
  └── API       ──►  Cloudflare (DNS + Tunnel)
                         │
                         ▼
                     VPS Hetzner
                         └── Docker Compose
                               ├── Spring Boot API
                               └── PostgreSQL 16
```

- **Cloudflare Tunnel: sin puertos de entrada abiertos en el VPS.** El túnel abre la
  conexión saliente. No propongas abrir 80/443 ni exponer Postgres.
- El token del túnel vive en `.env` en el VPS.
- `VITE_API_URL` se configura en Cloudflare Pages, no se hardcodea.
- Postgres no se expone fuera de la red de Docker Compose.

**Pendiente de resolver**: dónde viven los archivos subidos (`AthleteDocument`,
`PostImage`). Hoy se escriben en `uploads/` en la raíz del proyecto. Un volumen local en el
VPS implica que las copias de seguridad deben incluirlo y que el contenedor no es
desechable. No es un detalle menor cuando parte de esos archivos son documentos médicos de
menores.

**Problema activo: `uploads/` está bajo control de versiones.** Hay archivos de usuarios
commiteados en el repositorio. Hay que sacarlos del seguimiento (`git rm -r --cached
uploads/` más entrada en `.gitignore`), pero eso no los borra del historial. Si alguno es
un documento de atleta, la limpieza del historial es una tarea aparte y urgente. Ver
`rgpd.md`.

### Estado del despliegue

- [x] Paso 1 — Stack local con Docker Compose funcionando
- [ ] Paso 2 — Cuenta Hetzner y creación del servidor
- [ ] Paso 3 — Docker y despliegue del stack en el VPS
- [ ] Paso 4 — Configuración del Cloudflare Tunnel
- [ ] Paso 5 — Frontend en Cloudflare Pages
- [ ] Paso 6 — Dominio (pendiente de decisión del club)

### Trampa conocida

Cualquier variable añadida a `.env.example` hay que **copiarla a mano** al `.env` real de
cada entorno. Ya ha causado fallos de arranque difíciles de diagnosticar. Si tocas
`.env.example`, avísalo explícitamente al terminar la tarea.