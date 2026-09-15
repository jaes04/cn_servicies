# Roadmap de desarrollo — Sistema de gestión de clubes de natación

**Proyecto:** `es.jaes:cn_servicies` (Spring Boot 3.4.0, Java 21, PostgreSQL 16)
**Frontend:** React + Vite
**Objetivo:** producto comercializable multi-club con app móvil

---

## Cómo leer este documento

Cada tarea lleva tres etiquetas: `tiempo · dificultad · importancia`

**Tiempo.** Estimación para tu nivel actual, incluyendo pruebas y el rato de leer documentación. No es el tiempo de un senior. Si una tarea te lleva el doble, es normal; si te lleva cinco veces más, para y pregunta.

**Dificultad**
- `Baja` — mecánico, sabes hacerlo o se aprende leyendo la documentación
- `Media` — requiere entender un concepto nuevo antes de escribir código
- `Alta` — fácil de hacer mal sin darte cuenta. Aquí es donde conviene revisar el resultado con calma.

**Importancia**
- `Crítica` — bloquea otras tareas, o hay riesgo legal o de seguridad si falta
- `Alta` — el producto no es vendible sin esto
- `Media` — mejora clara, puede esperar
- `Baja` — deseable, prescindible en la primera versión

---

## Decisiones de arquitectura tomadas

| Decisión | Elección | Motivo |
|---|---|---|
| Modelo multi-tenant | Shared schema con `club_id` | Estándar SaaS, una migración, salida abierta a extraer un club a su propia BD |
| Aislamiento | Filtro Hibernate + Row Level Security | RLS hace que el aislamiento lo imponga Postgres, no la confianza en el código |
| Origen del tenant | Claim `club_id` del JWT | Nunca desde parámetro de petición: el cliente podría enviar otro |
| Histórico de grupos | Sí, por temporada | El club quiere conservar temporadas anteriores |
| Grupos y temporadas | `Grupo` cuelga de `Temporada` | Entrenador, horario y composición cambian cada año |
| Sesiones | Filas reales generadas desde horario recurrente | Necesitas cancelar sesiones concretas y colgarles asistencia |
| Datos de salud | Solo metadatos del certificado médico | Sin veredicto ni diagnóstico, la minimización evita casi todo el problema |
| Administrador de club | `adminjaes` por club, creado con el club | La necesidad real es crear los demás admins del club, no ver todos los clubes. Resuelta así, el aislamiento no necesita ninguna excepción |

### Orden de las fases

Por delante de todo va la **Fase S.0**: cerrar los endpoints de autenticación que hoy permiten a cualquiera crearse un administrador. Son unas horas y no depende de nada, pero mientras siga abierto, todo el aislamiento entre clubes de la Fase 0 es decorativo.

Hecho eso, la división por clubes va **primera**, aunque el móvil sea lo último. Construir grupos y asistencia sin `club_id` obliga después a migrar cada tabla, cada query, cada endpoint y el JWT.

---

## Resumen de esfuerzo

| Fase | Contenido | Tiempo | Cuándo |
|---|---|---|---|
| S.0 | Agujeros de autenticación abiertos | ~4 h | **Lo primero de todo** |
| 0 | Multi-tenancy | ~35 h (4–5 días) | Bloquea todo lo demás |
| S.1 | Consentimiento y certificado médico | ~16 h (2 días) | Antes de la Fase 1 |
| 1 | Temporadas y grupos | ~38 h (5 días) | |
| 2 | Horarios y asistencia | ~52 h (6–7 días) | |
| 3 | Preparar API para móvil | ~26 h (3–4 días) | |
| S (resto) | Seguridad y cumplimiento | ~58 h + legal | Antes del primer cliente |
| Transversal | Backups y monitorización | ~14 h | Antes del primer cliente |
| 4 | App móvil | ~120 h (4 semanas) | |

**Total hasta producto vendible sin móvil:** unas 245 horas. A 15 horas semanales, en torno a 4 meses. Con la app móvil, 6 meses.

Cuenta un 30 % de margen por encima. Nunca he visto una estimación de software que sobrara.

---

## Fase S.0 — Agujeros de autenticación abiertos

**Va antes que todo lo demás, incluida la Fase 0.** No depende de nada, se resuelve en una tarde, y mientras siga ahí el resto del trabajo de aislamiento no sirve para nada: no hace falta saltarse la tenancy si te puedes crear un administrador.

**Bloquea:** nada técnicamente, pero cualquier despliegue fuera de local.

- [x] Cerrar `POST /api/auth/signup/with-role` — `1h · Baja · Crítica`
- [x] Validar en servidor qué roles puede asignar quien llama, sin fiarse del cuerpo — `1h · Media · Crítica`
- [x] Eliminar `GET /api/auth/hash` — `15min · Baja · Alta`
- [x] Repasar uno a uno los `permitAll` restantes de `SecurityConfig` — `1h · Media · Alta`
- [x] Rotar el secreto JWT, la contraseña de Postgres y la del administrador **antes del primer despliegue fuera de local** — `1h · Baja · Crítica`

> `signup/with-role` está hoy en `permitAll` y acepta un `Set<RoleName>` arbitrario en el cuerpo, que pasa a `userService.create()` sin comprobación, devolviendo además el JWT ya emitido. Una sola petición anónima basta para obtener `ROLE_ADMIN`.

> `/api/auth/hash` lleva un comentario que dice "TEMPORAL: eliminar tras las pruebas" y quedó público. No filtra nada, pero es cómputo BCrypt anónimo a demanda.

> La rotación no corre prisa mientras los datos vivan solo en la base local: no hay nada expuesto. Lo que no puede pasar es que esos valores lleguen a producción, porque están en el historial de git.

---

## Fase 0 — Multi-tenancy

**Objetivo:** que toda entidad del sistema pertenezca a un club y que ese aislamiento sea imposible de saltar por error.
**Bloquea:** todas las fases siguientes

### 0.1 Entidad Club — 2 h

- [x] Crear entidad `Club`: `id`, `nombre`, `slug`, `activo`, `fecha_alta` — `1h · Baja · Crítica`
- [x] Migración de la tabla `club` — `30min · Baja · Crítica`
- [x] Insertar un club por defecto, idempotente, en `data.sql` — `30min · Baja · Crítica`
- [x] Índice único sobre `slug` — `15min · Baja · Alta`

### 0.2 Añadir club_id a las entidades existentes — 5 h

- [x] Añadir columna `club_id` (nullable de momento) a `usuario` y `atleta` — `1h · Baja · Crítica`
- [x] Backfill: asignar todas las filas existentes al club por defecto — `1h · Media · Crítica`
- [x] Poner la columna `NOT NULL` **después** del backfill — `30min · Baja · Crítica`
- [x] Foreign key hacia `club` — `30min · Baja · Alta`
- [x] Índice sobre `club_id` en cada tabla — `30min · Baja · Alta`
- [x] Migrar el índice único de `username` a `(club_id, username)` — `1h · Media · Crítica`

> Sin ese último cambio, el segundo club no puede tener un usuario llamado "admin".

> Las tablas hijas (`atleta_grupo`, `horario_grupo`, `asistencia`) no llevan `club_id`: siempre se llega a ellas por el padre.

> **Ejecutado sobre tres tablas, no dos.** A `users` y `athletes` se sumó `posts`, que también es entidad raíz: el blog es del club y no cuelga de ninguna otra. `comments`, `post_images`, `competition_results`, `athlete_documents`, `user_athletes` y `athlete_invite_keys` son hijas y llegan a su club por el padre, siguiendo la regla de arriba.

> **`posts.slug` deja de ser único** — decidido. Puede repetirse entre clubes y el post se identifica por su `id`. Arrastra un cambio que **hay que hacer antes del segundo club**: `PostRepository.findBySlug` devuelve `Optional<Post>` y reventará con `NonUniqueResultException` en cuanto haya dos posts con el mismo slug. El endpoint público `GET /api/posts/published/{slug}` pasa a resolver por id, y con él la ruta `/noticia/:slug` del frontend. Es cambio de contrato: ver 0.2.b.

> **`athletes.dni` pasa a ser único por club** — decidido, mismo patrón que `username`. Se retira `athletes_dni_key` y entra `uk_athletes_club_dni`. El mismo nadador puede estar fichado en dos clubes, y cada uno tiene su propia ficha independiente: no se comparten datos entre clubes, que además es lo correcto teniendo dos responsables del tratamiento distintos. Dentro de un mismo club, dos fichas con el mismo DNI siguen siendo un error.

> **Queda abierto en `dni`:** la columna es `NOT NULL`. Muchos atletas son menores de 14 y pueden no tener DNI, y los extranjeros tienen NIE. Con la columna obligatoria alguien acaba tecleando un valor inventado, y con el índice único el segundo atleta sin DNI ya no se puede dar de alta. Hacerla nullable lo resuelve —un índice único de Postgres ignora los nulos— pero obliga a decidir cómo se detectan duplicados sin DNI y a tocar la validación de `AthleteRequest`.

> **Deuda que deja esta tarea:** `ClubService.getDefaultClub()` es un puente temporal. `UserService`, `AthleteService` y `AdminInitializer` lo usan para satisfacer el `NOT NULL` mientras no exista contexto de tenant; desaparece en la 0.4. `PostService` no lo necesita, porque el post hereda el club de su autor.

### 0.2.b Identificar el post por id, no por slug — 3 h

**Decisión tomada:** `posts.slug` puede repetirse entre clubes; el post se distingue por su `id`. Queda como campo descriptivo, útil para la URL, pero deja de ser identificador.

**Hay que hacerlo antes de que exista el segundo club**, no en cuanto se pueda: mientras haya uno solo, los slugs no colisionan y nada falla. Con dos, `findBySlug` lanza `NonUniqueResultException` y la noticia deja de abrirse.

- [x] Retirar la restricción única de `posts.slug` — `30min · Baja · Alta`
- [x] `GET /api/posts/published/{slug}` pasa a resolver por id — `1h · Media · Alta`
- [x] Frontend: la ruta `/noticia/:slug` pasa a `/noticia/:id` — `1h · Media · Alta`
- [x] Revisar que ningún enlace publicado dependa del slug — `30min · Baja · Media`

> **Corregido de paso:** el endpoint público servía borradores. `findBySlug` no comprobaba el estado, así que cualquiera que adivinara el slug —que se genera del título— leía un `DRAFT`. Ahora exige `PUBLISHED`. Los borradores siguen siendo visibles por `GET /api/posts/{id}`, que va detrás de autenticación.

> **Corregido de paso:** un id mal formado en la ruta acababa en el manejador genérico y devolvía 500. `GlobalExceptionHandler` trata ahora `MethodArgumentTypeMismatchException` y devuelve 400. Afecta a todos los endpoints con id en la ruta, no solo a este.

> **Cambio de contrato de API**, hay que coordinarlo con el repositorio del frontend.

> Los enlaces antiguos por slug dejan de funcionar. Con el blog recién estrenado no importa; si alguna noticia ya está compartida fuera, conviene mantener la resolución por slug como alternativa mientras haya un solo club.

### 0.3 club_id en el JWT — 3,5 h

- [x] Añadir el claim `club_id` al generar el token — `1h · Baja · Crítica`
- [x] Extraerlo y validarlo al parsear — `1h · Media · Crítica`
- [x] Rechazar cualquier token sin el claim — `30min · Baja · Crítica`
- [x] Actualizar el login para resolver el club del usuario — `1h · Media · Crítica`

> `AuthenticatedUser` es el `UserDetails` del proyecto y lleva el `clubId`. Existe para que el club llegue hasta la emisión del token sin arrastrarlo como parámetro por todas las firmas del camino. `JwtTokenProvider` revienta si recibe otro `UserDetails`: mejor fallar al emitir que soltar un token sin club, que el filtro rechazaría después lejos de la causa.

> `JwtAuthFilter` comprueba además que el club del token sea el del usuario. La firma ya impide falsificarlo, pero un token puede quedar obsoleto, y así **no se autentica a nadie en un club que no es el suyo**. Eso adelanta el criterio de aceptación de la 0.7 "token manipulado con otro `club_id` no da acceso", verificado con tokens firmados a mano con el secreto real.

> **Los tokens emitidos antes de este cambio dejan de valer**: no traen el claim y se rechazan. Todo el mundo vuelve a iniciar sesión. Es el mismo efecto que tendrá rotar el secreto JWT, así que conviene hacer las dos cosas a la vez.

> **Lo que NO resuelve, y sigue siendo el punto abierto:** el login recibe solo `username` y `password`, así que `loadUserByUsername` resuelve por username a secas. Correcto mientras haya un club; con dos, la consulta es ambigua. Determinar el club *antes* de autenticar depende de cómo se sirva cada uno —subdominio, slug en la petición o selector en el formulario—, que es decisión abierta y arrastra cambio de contrato en el login. Hay que cerrarla antes del segundo club.

### 0.4 TenantContext — 4,5 h

- [x] Clase `TenantContext` con `ThreadLocal<UUID>` — `1h · Media · Crítica`
- [x] Filtro o interceptor que lo rellena desde el claim en cada petición — `2h · Media · Crítica`
- [x] **Limpiarlo en un `finally`** — `30min · Media · Crítica`
- [x] Definir el comportamiento en endpoints públicos: contexto vacío, nunca club por defecto — `1h · Media · Alta`

> **Paquete nuevo `tenant/`**, en vez de meterlo en `config/`. La 0.5 y la 0.6 van a añadir más piezas de tenancy —activación del filtro de Hibernate, `SET LOCAL app.club_id`— y `CLAUDE.md` avisa de no convertir `config/` en un cajón.

> `TenantFilter` va **después** de `JwtAuthFilter`: necesita el `SecurityContext` ya poblado. Toma el club de `AuthenticatedUser`, que lo trae del claim ya verificado.

> **`TenantContext.get()` devuelve `Optional` y `require()` revienta si no hay club.** No existe forma de obtener un club por defecto desde el contexto: quien no tiene club, no tiene club. Vacío no es "el club por defecto".

> **Hay tests**, en `TenantFilterTest`, los primeros del proyecto aparte del `contextLoads`. El `finally` no se puede comprobar desde fuera —ningún endpoint expone el contexto y el fallo que evita es intermitente por naturaleza—, así que se prueba ahí: que se limpia al terminar, que se limpia **también cuando la petición revienta**, y que una petición anónima en el mismo hilo no hereda el club de la anterior. Se ejecutan con `./mvnw test -Dtest=TenantFilterTest`.

> **Deuda de la 0.2 saldada a medias.** `AthleteService` ya toma el club del contexto. `UserService` no puede del todo: lo llaman el panel autenticado y el alta pública anónima, y en qué club se registra alguien que llega de fuera es la misma decisión abierta que la del login. `AdminInitializer` tampoco: se ejecuta al arrancar, sin petición, y desaparece en la 0.8. Los dos siguen usando `getDefaultClub()`.

> El `finally` cuesta cinco minutos y es el bug de multi-tenancy más difícil de reproducir que existe. Tomcat reutiliza los hilos del pool: sin limpieza, la siguiente petición hereda el club de la anterior de forma intermitente.

### 0.5 Filtro automático de Hibernate — 6 h

- [x] `@FilterDef` con parámetro `clubId` — `1h · Media · Crítica`
- [x] `@Filter` en cada entidad con `club_id` — `1h · Baja · Crítica`
- [x] Activarlo por petición desde el `TenantContext` — `2h · Alta · Crítica`
- [x] Auditar todas las queries nativas y `@Query` con SQL nativo — `2h · Alta · Crítica`

> Las queries nativas **no** pasan por el filtro. Son la vía de escape más habitual.

> **Auditoría: limpia.** Cero queries nativas y cero `@Query` en todo el proyecto. El acceso a datos es Spring Data derivado y Specifications. Esa vía de escape no existe todavía, y conviene que siga sin existir: cualquier `@Query` nativo que se añada a partir de ahora hay que escribirlo con su `WHERE club_id`.

> **Se activa con un aspecto, no con un filtro de servlet.** El filtro vive en la sesión de Hibernate, que nace con la transacción, no con la petición. Spring Boot engancha OSIV como interceptor del `DispatcherServlet`, así que un filtro de servlet corre antes de que exista sesión alguna. Enganchado a la transacción, funciona con OSIV activo o desactivado. Dependencia nueva: `spring-boot-starter-aop`.

> **El orden del consejo transaccional está invertido** en `TenantTransactionConfig`. Por defecto Spring lo pone como el más interno, y entonces el aspecto correría antes de que la transacción empezara. Afecta al orden de todos los consejos de la aplicación: recordarlo si algún día entran `@Async`, `@Cacheable` o validación por aspectos.

> **`findById` se salta el filtro, verificado.** Los filtros de Hibernate no se aplican a las cargas por clave primaria. Con dos clubes montados, el admin del club B obtuvo por id la ficha completa de un atleta del club A —nombre, fecha de nacimiento y DNI— con un 200. **Lo cierra la 0.6**, porque Postgres filtra la fila sea cual sea la vía por la que Hibernate la pida. Hasta entonces, esa fuga está abierta.

> **Lo que sí aísla ya**, verificado con dos clubes reales: listados y búsquedas. El admin del club A vio 8 atletas y 16 usuarios; el del club B, 1 y 1. Ninguno vio un registro del otro.

### 0.6 Row Level Security en PostgreSQL — 8 h

- [x] `ENABLE ROW LEVEL SECURITY` en cada tabla con `club_id` — `1h · Media · Crítica`
- [x] Policy por tabla usando `current_setting('app.club_id')` — `2h · Alta · Crítica`
- [x] `SET LOCAL app.club_id` al inicio de cada transacción — `3h · Alta · Crítica`
- [x] Usuario de aplicación **sin** `BYPASSRLS` — `1h · Media · Crítica`
- [ ] Usuario separado para migraciones, con permisos para saltarse las policies — `1h · Alta · Crítica`

> Lo más difícil es el `SET LOCAL`: hay que engancharlo al ciclo de vida de la transacción de Spring, no al de la petición. Cuenta con perder una tarde aquí.

> **El `SET LOCAL` salió gratis**, porque la 0.5 ya había pagado esa tarde: el aspecto sobre transacciones creado allí es exactamente el sitio donde engancharlo. Se usa `set_config(..., true)`, que es `SET LOCAL` en forma de función y admite parámetro enlazado.

> **`app.club_id` tiene tres estados y el tercero es el que importa:** un UUID limita a ese club; `public` lo abre todo, y es lo que se pone en las peticiones anónimas —login, alta de usuario, blog—; **sin poner no se ve ninguna fila**. Si un día el aspecto deja de ejecutarse, o alguien abre una transacción por otra vía, el síntoma es "no aparecen datos", no "aparecen los del otro club". Falla cerrado.

> **`ddl-auto` y RLS se estorban, y esa es la decisión de fondo.** Para que las policies actúen la aplicación no puede ser superusuario; pero sin ser dueña de las tablas, Hibernate no puede seguir gestionando el esquema; y si deja de hacerlo, la única fuente pasa a ser `schema.sql`, que estaba desfasado —le faltaba `genders` y describía `athletes` con columnas que no existen—. Nunca se notó porque Hibernate creaba las tablas primero y esos `CREATE TABLE IF NOT EXISTS` no llegaban a ejecutarse.

> **Resuelto haciendo a `cn_app` dueño de las tablas con `FORCE ROW LEVEL SECURITY`.** El dueño de una tabla se salta sus policies salvo que se fuerce lo contrario; con `FORCE`, `ddl-auto` sigue funcionando y las policies se aplican igual. Verificado: `cn_app` es dueño de las tres tablas y aun así ve cero filas sin la variable puesta.

> **Lo que esta solución no protege.** `cn_app`, por ser dueño, puede ejecutar `ALTER TABLE ... DISABLE ROW LEVEL SECURITY`. Protege de un error en el código —una consulta que se olvida del club, que es la amenaza real hoy— pero no de la aplicación comprometida. Separar de verdad el rol de aplicación del de esquema exige sacar las migraciones de Hibernate, probablemente con Flyway: es la quinta casilla, un bloque propio de un día largo, y hay que hacerlo antes de producción.

> **`schema.sql` y `data.sql` fijan `app.club_id` a `public` al empezar y lo dejan sin valor al terminar.** Los ejecuta la aplicación con su propio usuario, sujeto a las policies: sin eso, el `WITH CHECK` rechaza cada `INSERT` y los `UPDATE` del backfill no ven ninguna fila. Restablecerlo al final importa porque la conexión vuelve al pool y no debe llevar `public` pegado.

> **En vigor y verificado de punta a punta.** Con la aplicación conectada como `cn_app` y dos clubes montados: el admin de cada uno ve solo sus atletas (8 y 1), y **el `findById` cruzado que en la 0.5 devolvía la ficha completa de un atleta ajeno ahora devuelve 404**, mientras el propio sigue dando 200. Blog anónimo y login siguen funcionando.

> **El aspecto necesitaba `@annotation` además de `@within`.** Con solo `@within` —anotaciones de clase— se escapaba `UserDetailsServiceImpl`, que lleva `@Transactional` en el método. Sin la variable puesta, las policies ocultaban todos los usuarios y **nadie podía iniciar sesión**. Es el fallo cerrado funcionando: el síntoma fue "no se puede entrar", no "se ven datos ajenos". Solo apareció al ejecutar de verdad con RLS activo.

> **Cuidado con las contraseñas que contienen `$`.** No las leas con `source` en un script: bash expande `$`, comillas y backticks, y lo que llega no es lo que hay escrito en el archivo. Perdí un rato diagnosticando un fallo de autenticación que era mío.

### 0.7 Criterio de aceptación — 6 h

- [x] Test de integración: usuario del club A no ve ni un registro del club B — `2h · Media · Crítica`
- [x] Test negativo: query sin filtro **sigue** sin devolver datos ajenos gracias a RLS — `2h · Alta · Crítica`
- [x] Test: token manipulado con otro `club_id` no da acceso — `1h · Media · Crítica`
- [x] Regresión: login y funcionalidad actual siguen funcionando — `1h · Baja · Crítica`

> Los cuatro viven en `TenantIsolationTest`, 10 tests. La suite pasa de 6 a 16.

> **El primer test comprueba que la conexión no es superusuario.** Postgres deja que los superusuarios se salten las policies, así que ejecutados como `postgres` la mitad de estos tests pasarían en verde sin demostrar nada — peor que no tenerlos. Si alguien ejecuta la suite sin `PGUSER=cn_app`, ese test falla y explica por qué.

> **Los datos los crea el propio test**, dos clubes suyos, y los borra al terminar. No depende de lo que haya en la base ni de conteos absolutos, así que no se rompe cuando cambian los datos de desarrollo.

> **Verificado que los tests fallan cuando deben.** Quitando `FORCE ROW LEVEL SECURITY` —con lo que `cn_app`, al ser dueño, vuelve a saltarse las policies— fallan exactamente los dos que dependen de RLS, `findById` y el 404 por HTTP, mientras los listados siguen pasando porque a esos los cubre el filtro de Hibernate. Las dos capas quedan distinguidas por los tests, no solo sobre el papel.

> **Corren contra la base de desarrollo**, no contra una propia. Crear `cn_test_it` exige `CREATE DATABASE` y el rol de la aplicación no puede: hace falta el de migraciones. Pendiente, y con él pasar la suite a una base aislada.

---

### 0.8 Alta de club con su administrador — 2 h

**Decisión tomada:** el alta de un club crea, en la misma transacción, el usuario `adminjaes` como `ROLE_ADMIN` **de ese club**. Nada más. No hay administrador que vea varios clubes.

Como `username` es único por club (ver 0.2), `adminjaes` puede existir una vez en cada club sin colisionar. Cada uno es un administrador corriente del suyo: el filtro de la 0.5 y las policies de la 0.6 le tratan igual que a cualquier otro usuario. **Ninguna excepción al aislamiento, en ningún sitio.**

- [x] `ClubService.create()` crea club y administrador en una sola transacción — `1h · Media · Crítica`
- [x] Si falla el alta del administrador, no se crea el club — `30min · Media · Crítica`
- [x] Adaptar `AdminInitializer`: hoy crea `adminjaes` al arrancar sin club y **dejará de funcionar en cuanto `club_id` sea NOT NULL**. Pasa a apoyarse en el alta conjunta — `30min · Media · Crítica`

> **El club dejó de ser estado ambiental.** `UserService.create` lo recibe como parámetro en vez de sacarlo del `TenantContext`. Por diseño —quien da de alta a alguien sabe en qué club lo hace— y por necesidad: si `UserService` dependiera de `ClubService` y este de aquel para crear al administrador, la dependencia sería circular. Ahora quien conoce el club lo resuelve y lo pasa: `UserController` desde el contexto, `AuthService` desde el contexto o el club por defecto para el alta pública.

> **No hay endpoint de alta de club, y es a propósito.** Sin rol que atraviese clubes, protegerlo con `ROLE_ADMIN` dejaría que el administrador de cualquier club creara otros. Es una operación de aprovisionamiento: vive en el servicio, la usa `AdminInitializer` y la usará la herramienta de administración cuando exista.

> **Tests en `ClubCreationTest`.** La suite pasa de 16 a 19. Verificado que el del rollback falla cuando debe: quitando `@Transactional` de `ClubService.create`, el club queda creado sin administrador y solo ese test se pone en rojo.

> El propósito de esta cuenta es **poder crear los demás administradores del club**, no ver sus datos desde fuera. Es una necesidad de aprovisionamiento, puntual y por club, no un privilegio permanente. Confundir las dos cosas es lo que lleva a construir un administrador global, que es un agujero en el aislamiento que aquí no hace falta.

> Contrapartida que queda viva: mantienes una cuenta con acceso permanente a cada club. Si te la comprometen, el alcance es total. La diferencia con un rol global es que ese acceso son filas que puedes listar, no una rama de código que puede fallar sola. MFA para estas cuentas (S.3.1) sigue siendo obligatorio.

> **Migrar antes del primer cliente que pague.** El destino es que el club ponga su propio primer administrador mediante enlace de un solo uso —el patrón de `AthleteInviteKey` ya está en el proyecto— y que tú no conserves cuenta en ningún club, con un acceso de emergencia temporal y auditado para el caso de que un club se quede sin administrador. El momento natural es al redactar el bloque S.2: es cuando hay que poner por escrito a qué datos accedes como encargado del tratamiento, y llegar ahí sin acceso permanente vale más que lo que cuesta.

---

## Fase S.1 — Consentimiento y certificado médico

**Va antes de la Fase 1** porque son tablas relacionadas con `Atleta`. Añadirlas después es una migración con datos ya cargados.

### S.1.a Consentimiento de menores — 11 h

En España la edad de consentimiento son **14 años** (LOPDGDD art. 7), no 16. La mayoría de tus atletas estarán por debajo.

- [x] Entidad `Tutor`: `id`, `club_id`, `nombre`, `dni`, `email`, `telefono`, `parentesco` — `1h · Baja · Crítica`
- [x] Relación `Atleta` ↔ `Tutor`, varios tutores por atleta — `1h · Media · Crítica`
- [x] Entidad `Consentimiento`: `id`, `atleta_id`, `tutor_id`, `tipo`, `otorgado`, `fecha`, `evidencia`, `ip_origen`, `fecha_revocacion` — `2h · Media · Crítica`
- [x] Tipos separados y granulares: `TRATAMIENTO_DATOS`, `IMAGEN`, `COMUNICACIONES`, `DATOS_SALUD` — `1h · Media · Crítica`
- [x] Consulta que determina si el atleta era menor de 14 en la fecha del consentimiento — `1h · Media · Alta`
- [x] Bloquear el alta de menor de 14 sin consentimiento de tutor registrado — `2h · Media · Crítica`
- [x] Revocación por `fecha_revocacion`, nunca borrando la fila — `1h · Baja · Crítica`
- [x] Almacenar la evidencia del consentimiento con sello de tiempo — `2h · Media · Alta`

> El consentimiento de imagen va **separado** del general y tiene que poder revocarse solo. Una casilla única para todo no es válida.

> **En inglés, como el resto del dominio:** `Guardian`, `AthleteGuardian`, `Consent`, `ConsentType`, `ConsentEvidenceType`, todo en el paquete `guardian/`. El consentimiento vive con el tutor y no en un módulo propio: sin tutor no hay consentimiento, y así `ConsentService` no cruza la frontera de ningún repositorio ajeno.

> **El tutor es una persona, no una cuenta.** `Guardian.user` es opcional y se rellena cuando el tutor se registra. Tenía que ser así para poder registrar el consentimiento en el alta, que es justo cuando todavía no tiene usuario. Convive con `UserAthlete` de tipo `TUTOR`, que sigue siendo el vínculo de **acceso** —quién puede ver los datos—; este es el sujeto que **otorga**. No se sustituyen.

> **El parentesco vive en el vínculo, no en el tutor**: la misma persona es madre de un atleta y puede ser tutora legal de otro.

> **`guardians` y `consents` llevan `club_id` y policy de RLS propia.** En `consents` se aparta del criterio de la 0.2 para tablas hijas, a propósito: es lo que sostiene la licitud del tratamiento de un menor, y merece que el aislamiento lo imponga Postgres en vez de la confianza en que toda consulta futura pase por el atleta.

> **El registro es append-only.** Ni se actualiza ni se borra: revocar es escribir `revoked_at`, y volver a consentir es una fila nueva. Sin `unique` sobre `(athlete_id, type)`, porque el historial completo es la prueba (RGPD art. 7.1). Una negativa se guarda igual que una concesión: "dijo que no a la imagen" y "todavía no se le ha preguntado" no permiten lo mismo.

> **La IP solo se conserva con evidencia `ONLINE_FORM`.** En papel o por correo no prueba nada y es dato personal.

> **La edad se mide en la fecha de la decisión, no en la de hoy.** Lo que un tutor firmó cuando el atleta tenía 11 sigue siendo válido a los 16. Son dos preguntas distintas y están separadas: `requiresGuardianConsent` (hoy) y `wasUnderConsentAgeAtDecision` (entonces).

> **El bloqueo va en la transacción del alta.** `AthleteRequest` acepta un bloque opcional con el tutor y sus consentimientos, y `AthleteService.create` lo exige por debajo de 14. Es **cambio de contrato de `POST /api/athletes`**: el frontend tiene que enviarlo. Obligatorio el de tratamiento; el de imagen hay que preguntarlo, pero un 
o\ es respuesta válida y no bloquea. El tutor se reutiliza por DNI dentro del club, así que el segundo hermano no crea ficha nueva. La modificación **rechaza** el bloque en vez de ignorarlo.

> **Endpoints en `/api/consents`**, ruta propia y no anidada bajo `/api/athletes/**`, que está cerrada en bloque. El **entrenador ve el estado** por finalidad —lo necesita antes de publicar una foto— **pero no el historial**: quién firmó y con qué papel es información del club. Registrar y revocar es solo de `ADMIN` hasta que exista un portal donde el tutor conteste por sí mismo. Revocar es `POST /{id}/revocation`, no `DELETE`: la fila no desaparece.

> **Migraciones `S.1-guardians-rls.sql` y `S.1-consents-rls.sql`, ejecutadas con `cn_app`** y no con `postgres`: `ENABLE ROW LEVEL SECURITY`, `CREATE POLICY` y `FORCE` los puede el dueño de la tabla, y hoy el dueño es `cn_app` porque las crea Hibernate. Cuando el esquema salga de Hibernate volverán a necesitar el rol de migraciones, que es el objetivo.

### S.1.b Certificado médico anual (FMN) — 5 h

Único dato de salud del sistema. **No se almacena el veredicto**: nadie presenta un certificado de "no apto", así que la existencia de un certificado en plazo *es* la aptitud. Un campo `apto` sería un juicio clínico; una fecha de caducidad no lo es.

- [x] Entidad `CertificadoMedico`: `id`, `atleta_id`, `temporada_id`, `fecha_emision`, `fecha_caducidad`, `estado`, `validado_por`, `validado_en` — `1h · Baja · Crítica`
- [x] **Sin diagnóstico, sin observaciones médicas, sin campo `apto`** — `0h · Baja · Crítica`
- [x] Estado calculado desde `fecha_caducidad`, no almacenado a mano — `1h · Baja · Alta`
- [x] Aviso al alta en grupo si no hay certificado vigente — `1h · Media · Alta` — *cubierto por el informe de documentación pendiente del bloque 3c*
- [ ] Aviso automático al tutor 30 días antes del vencimiento — `2h · Media · Alta`

> **Hecho como opción A: solo metadatos.** Entidades en inglés: `MedicalCertificate`, `MedicalCertificateStatus`. **Sin `season_id`** —la vigencia la definen sus fechas y `Season` no existe hasta la Fase 1— y con `club_id` y policy de RLS propia, misma decisión explícita que en `consents`.

> **El estado se calcula, nunca se almacena.** `VALID`, `EXPIRING_SOON` (30 días o menos), `EXPIRED` y `MISSING` cuando no consta ninguno. Guardarlo significaría que un certificado caduca sin que nadie se entere hasta que alguien lo actualice a mano.

> **Cuando hay varios manda el que más lejos caduca**, no el último registrado: poner al día el archivo tecleando el del año pasado no puede empeorar el estado.

> **Permisos:** el entrenador ve el estado —lo necesita antes de meter al nadador al agua— pero no las fechas ni quién validó. Registrar y consultar el historial es de `ADMIN`.

> **Los dos puntos que faltan dependen de otra cosa.** El aviso al alta en grupo necesita `Group`, que es la Fase 1. El aviso automático al tutor necesita envío de correo, que es un bloque propio; mientras tanto `GET /api/medical-certificates/expiring` da la lista y el club la ve.

> **Pendiente menor:** no hay forma de corregir un certificado mal tecleado. No hay PUT ni DELETE, a propósito de momento, pero una fecha equivocada hoy solo se arregla en la base.

**Opción B — almacenar el PDF.** Solo si un club lo exige. Suma unas 8 h.

- [ ] Fichero en almacenamiento privado, **nunca en la BD ni en carpeta servida estáticamente** — `2h · Media · Crítica`
- [ ] Descarga solo por endpoint controlado, sin URL directa ni predecible — `2h · Alta · Crítica`
- [ ] Nombre de fichero aleatorio (UUID), sin el nombre del atleta — `30min · Baja · Alta`
- [ ] Cifrado en reposo — `1h · Media · Crítica`
- [ ] Cada descarga registrada en auditoría con usuario, fecha e IP — `1h · Media · Crítica`
- [ ] Solo `ADMIN_CLUB` abre el documento; `ENTRENADOR` ve solo el estado — `1h · Media · Crítica`
- [ ] Borrado automático al ser sustituido por el del año siguiente — `1h · Media · Alta`

### S.1.c Registro de entregas de papeles — bloque 3a

**Sale de una propuesta del primer club:** no subir los documentos, sino anotar que se entregaron, con su estado y su caducidad. No sustituye a la subida para siempre: la apaga para el despliegue inicial.

- [x] Entidad `DocumentDelivery`: solicitud de licencia (por temporada), documento de identidad (hasta su caducidad) y permiso de viaje (fechas del viaje) — `2h · Media · Crítica`
- [x] Estado calculado, nunca almacenado — `1h · Media · Alta`
- [x] Cobertura del permiso de viaje para unas fechas concretas — `1h · Media · Alta`
- [x] Apagar la subida de archivos de atleta por configuración — `1h · Baja · Crítica`
- [x] Policy de RLS propia, `migrations/S.1-document-deliveries-rls.sql` — `30min · Baja · Crítica`

> **Faltan dos tipos a propósito.** El certificado médico no está: es dato de salud y tiene su tabla, sus permisos y su policy, y un tipo médico aquí sería una puerta lateral para meterlo sin esa protección. El derecho de imagen tampoco: es un consentimiento `IMAGE` con evidencia en papel, y registrarlo también como documento dejaría dos registros de la misma firma.

> **Cada tipo lleva sus campos y ningún otro**, y lo que sobra se rechaza con 400 en vez de ignorarse: aceptar una licencia con fechas y no hacer nada con ellas dejaría creer que cuentan.

> **Sin número de documento, sin destino del viaje y sin notas.** El número ya está en la ficha; el destino no hace falta para saber si un permiso cubre unas fechas.

> **La licencia se mide contra la temporada activa, nunca contra la última registrada**: anotar en agosto la del curso que viene no puede dar por buena la de este. Si solo trajo la de un curso anterior sale `EXPIRED`, y si nunca trajo ninguna, `MISSING`: a unos hay que pedirles que renueven y a otros que la traigan.

> **Del documento de identidad manda el que más lejos caduca**, no el último anotado. Y si la ficha no tiene número, sale `NOT_REQUIRED`: hoy no ocurre porque el DNI es obligatorio, pero cuando deje de serlo en el 3b ya contesta lo correcto.

> **El permiso de viaje mide la edad el día de salida** y solo se admite para menores: el de un adulto no sirve para nada, y guardarlo sería guardar sus viajes. No tiene estado general —nadie lo necesita hasta que hay un viaje—; se pregunta por unas fechas, y la respuesta separa `required` de `covered` para no esconder por qué.

> **La subida se apaga, no se borra.** `app.documents.upload.enabled`, variable `DOCUMENTS_UPLOAD_ENABLED`, falsa por defecto. Apagada, las cinco rutas de `/api/athlete-documents` contestan 404 a todo el mundo, administrador incluido, antes de mirar permisos o ids. Encenderla exige resolver antes el cifrado en reposo, el directorio separado y el registro de accesos. `ObjectAccessTest` la enciende para seguir protegiendo lo que pasaría ese día.

> **Permisos, los del certificado:** el entrenador ve el estado y si el nadador puede viajar, solo de los atletas de sus grupos; registrar y el historial con fechas son del club.

> **Verificado que los tests fallan cuando deben**, con tres mutaciones: sin el guardián, el entrenador ve a un atleta ajeno; sin la comprobación de la subida, el listado contesta; y sin `FORCE ROW LEVEL SECURITY` en la tabla nueva, se pone rojo el guardia de `TenantIsolationTest`.

> **26 tests**: 22 de endpoint y 4 de reglas sin base de datos, que cubren los extremos —el día que caduca todavía vale; la víspera de cumplir 18 todavía es menor—. La suite pasa de 284 a 310.

> **La migración de RLS va una vez por entorno**, después de arrancar la aplicación: `psql -h localhost -U cn_app -d cn_test -f migrations/S.1-document-deliveries-rls.sql`.

> **Cambio de contrato:** rutas nuevas en `/api/document-deliveries`, y **las de `/api/athlete-documents` pasan a 404 con la configuración por defecto**. Si el frontend las usa, deja de funcionar hasta que se cambie al registro de entregas.

> **Queda abierto:** qué se hace con los `athlete_documents` que ya existan; y, como en el certificado, que una entrega mal tecleada no se puede corregir, porque no hay `PUT` ni `DELETE`.

### S.1.d Certificado por temporada y documento de identidad opcional — bloque 3b

- [x] Certificado médico por temporada: `season_id`, y la caducidad pasa a ser el final de la temporada — `2h · Media · Alta`
- [x] Documento de identidad del atleta opcional, admitiendo DNI, NIE y pasaporte — `2h · Media · Alta`
- [x] Duplicados sin documento, por nombre, apellidos y fecha de nacimiento — `1h · Media · Alta`

> **Decisiones del club y tuyas:** el certificado vale una temporada aunque el anterior siga en fecha; el documento de identidad es genérico y no se exige a quien no lo tiene; sin documento, un alta con el mismo nombre, apellidos y nacimiento se rechaza; y `GET /api/medical-certificates/expiring` se mantiene hasta el 3c.

> **El alta de certificado pide `seasonId` y ya no `expiresOn`.** La temporada es explícita y no la activa por defecto: a finales de agosto es muy fácil registrar el certificado del curso que empieza en la temporada que acaba. La caducidad la pone el servicio.

> **Manda el certificado de la temporada activa**, nunca el último registrado, igual que la licencia del 3a: `EXPIRED` si el último que trajo es de otro curso y `MISSING` si nunca trajo ninguno. El del curso que viene no cubre el actual.

> **Los certificados que ya existían se reparten en `schema.sql`**: van a la temporada que cubre su fecha de emisión, si no a la activa y si no a la más reciente, y solo después la columna pasa a obligatoria. La anotación no declara el `NOT NULL`, porque Hibernate intentaría añadir la columna obligatoria a una tabla con filas y fallaría antes del relleno. Si un club tuviera certificados y ninguna temporada, la aplicación no arrancaría, y es a propósito: un certificado sin temporada no se puede medir.

> **El campo sigue llamándose `dni`** aunque guarde NIE y pasaporte: renombrarlo rompe contrato y `Specification` sin que falle la compilación (regla 9). Admite letras y números entre 5 y 20, y se guarda sin espacios y en mayúsculas. Un vacío se guarda como nulo: dos cadenas vacías chocarían en el índice único, dos nulos no.

> **El duplicado sin documento se busca contra cualquier ficha del club, tenga documento o no**, sin distinguir mayúsculas. Es algo más amplio que "entre fichas sin documento": si alguien ya está fichado con su DNI y se le vuelve a dar de alta sin él, es la misma persona.

> **Corregido de paso:** editar una ficha sin DNI hacía `athlete.getDni().equals(...)`, que habría reventado con `NullPointerException`; y comprobar el DNI con un nulo habría encontrado a todos los atletas sin documento y rechazado el alta del segundo.

> **El `NOT_REQUIRED` del 3a ya contesta**: una ficha sin documento no tiene documento de identidad que traer. Hay test.

> **Hueco encontrado al preparar las mutaciones:** ningún test distinguía "manda la temporada activa" de "cuenta la que más lejos llega", ni en el certificado ni en la licencia; cambiar la regla habría dejado todo en verde. Hay un test nuevo para cada uno.

> **Verificado que los tests fallan cuando deben**, con cuatro mutaciones y siete rojos: sin el duplicado por nombre, sin normalizar el documento, y contando el certificado o la licencia que más lejos llega en lugar de la de la temporada activa.

> **28 tests nuevos**: 11 de identidad, 14 de validación del formato sin base de datos, uno más de certificado —el servicio se reescribió para la temporada— y dos en entregas. La suite pasa de 310 a 338. Tres fixtures ajustadas: `TenantIsolationTest` creaba certificados antes que temporadas y borraba las temporadas antes que los certificados, y `MedicalCertificateEndpointTest` insertaba certificados sin temporada.

> **Cambio de contrato:** el alta de certificado pide `seasonId` y no `expiresOn`; la respuesta añade `seasonId` y `seasonName`, y su `expiresOn` es el final de la temporada. En atletas, `dni` puede llegar `null` y admite NIE y pasaporte, y un alta duplicada sin documento devuelve 400.

### S.1.e Documentación pendiente — bloque 3c

- [x] Informe de lo que falta para la temporada activa: certificado, licencia y documento de identidad — `3h · Media · Alta`
- [x] Retirar `GET /api/medical-certificates/expiring`, que queda sustituido — `30min · Baja · Media`
- [x] Documento del tutor con NIE o pasaporte — `1h · Baja · Alta` *(encontrado al escribir los contratos)*
- [x] Errores del canje de claves de invitación como 400 y no como 500 — `30min · Baja · Media` *(encontrado al escribir los contratos)*

> **`GET /api/reports/documents/pending`**, bajo la regla de `/api/reports/**`: lo consultan el administrador y el entrenador, y al entrenador le llega acotado a sus grupos. Cada atleta sale con sus grupos y el estado de los tres papeles, sin documento de identidad ni fecha de nacimiento.

> **Solo cuentan los atletas que hoy están en algún grupo de la temporada activa.** Una ficha sin grupo puede ser de alguien que dejó el club hace años, y un aviso lleno de esas fichas deja de leerse. El precio: en septiembre hay que meter a cada nadador en su grupo antes de que aparezca.

> **A punto de caducar cuenta como pendiente**, porque es justo cuando conviene pedir el papel. `NOT_REQUIRED` no cuenta.

> **El informe no calcula ningún estado.** Se los pide a los servicios de certificados y de entregas, que ahora tienen versión en bloque y pasan por el mismo método que la consulta de un atleta suelto. Si fueran dos criterios, el aviso diría que falta algo que la ficha da por bueno. Hay test de que el certificado en bloque y el de uno en uno coinciden.

> **Los miembros se sacan grupo a grupo con la consulta de pertenencia de siempre**, y no con una consulta nueva: el criterio "quién está en un grupo en una fecha" ya está escrito tres veces.

> **El aviso al dar de alta en un grupo sin certificado**, pendiente desde la S.1.b, queda cubierto por este informe: en cuanto el nadador está en el grupo sin certificado, aparece. No se añade un aviso aparte en la respuesta del alta.

> **Una ruta que no existe devuelve 404, no 500.** Salió al retirar `/expiring`: sin manejador, quien la siguiera llamando recibía un error de servidor. Afecta a cualquier ruta mal escrita, no solo a esa.

> **Documento del tutor:** el mismo criterio que el del atleta en el 3b —DNI, NIE o pasaporte, normalizado a mayúsculas sin espacios—, pero **obligatorio**: el tutor firma el consentimiento y su documento es lo que reutiliza su ficha con el segundo hijo. Columna a 20 caracteres con un `ALTER` suelto.

> **Canje de claves:** clave usada, caducada y usuario ya vinculado lanzaban `IllegalStateException`, que el manejador no trata. Salían como 500, con el nombre de la excepción en el mensaje. Pasan a 400.

> **Verificado con seis mutaciones y diez rojos:** la clave usada volviendo a 500; el documento del tutor sin normalizar; el patrón del tutor de solo DNI; sin el manejador de rutas inexistentes; sin contar lo que está a punto de caducar; y el entrenador recibiendo el informe de todo el club.

> **Tests:** 10 del informe, 5 del canje de claves, 12 de validación del documento del tutor y 2 de alta con tutor extranjero. En certificados, los dos tests de `/expiring` se sustituyen por uno de estado en bloque. La suite pasa de 338 a 366.

> **Cambio de contrato:** ruta nueva `/api/reports/documents/pending`; `/api/medical-certificates/expiring` responde 404; el documento del tutor admite NIE y pasaporte; y el canje de claves devuelve 400 donde antes devolvía 500. Todo recogido en `docs/contratos-api.md`.

---

## Fase 1 — Temporadas y grupos

**Depende de:** Fase 0 y S.1

### 1.1 Temporada — 5 h

- [x] Entidad `Temporada`: `id`, `club_id`, `nombre`, `fecha_inicio`, `fecha_fin`, `activa` — `1h · Baja · Crítica`
- [x] Regla: solo una temporada activa por club — `1h · Media · Alta`
- [x] CRUD y endpoint para marcar la temporada activa — `2h · Baja · Alta`
- [x] Crear la temporada actual en el seed — `1h · Baja · Media`

> **Entidad `Season`**, en inglés como el resto. Con `club_id` y policy de RLS propia (`migrations/1.1-seasons-rls.sql`).

> **Una sola activa por club, impuesto en dos capas.** El servicio apaga la anterior antes de encender la nueva, y la base lo sostiene con un índice único **parcial** sobre `(club_id) WHERE active`. Un único normal sobre `(club_id, active)` dejaría tener una sola temporada pasada.

> **Activar es endpoint propio** (`POST /api/seasons/{id}/activation`) y no un campo del `PUT`: tiene efecto sobre otra fila y no puede pasar de rebote al editar unas fechas. `SeasonRequest` no lleva `active`, así que una temporada **nace apagada**.

> **No hay borrado, ni lógico ni físico**, a propósito: conservar las temporadas anteriores es el motivo de que la entidad exista. Una temporada terminada se queda con `active = false`. La `D` del CRUD no está y no debería estar.

> **Permisos:** el entrenador consulta (`GET`), el administrador crea, edita y activa.

> **Siembra en `AdminInitializer`, no en `data.sql`**: el club por defecto lo crea un runner que va después de `data.sql`, así que allí todavía no existe a qué colgarla. Septiembre a agosto, idempotente, y no toca nada si el club ya tiene temporadas.

> **Las temporadas no se solapan**, decidido: van una detrás de otra, de septiembre a agosto. Se valida en el alta y en la edición —esta última sin contarse a sí misma— y compartir un solo día ya cuenta como solape. La restricción vive solo en el servicio: llevarla a la base necesitaría una restricción de exclusión con `btree_gist`, y una extensión que pide superusuario en cada despliegue es desproporcionada para prevenir un error de tecleo.

> **Aun así, la temporada en curso se resuelve por la bandera `active`, nunca deduciéndola de la fecha de hoy.** La validación evita el error de tecleo; que la búsqueda no dependa de las fechas es lo que hace que no haya ambigüedad que resolver. `Season.covers(fecha)` existe para comprobar que la fecha de una sesión cae dentro de su temporada —una validación—, no para buscar temporada por fecha.

### 1.2 Grupo — 8 h

- [x] Entidad `Grupo`: `id`, `club_id`, `temporada_id`, `entrenador_id`, `nombre`, `categoria`, `nivel`, `plazas_max` — `1h · Baja · Crítica`
- [x] Relación con `Usuario` para el entrenador — `1h · Baja · Alta`
- [x] CRUD completo — `3h · Baja · Crítica`
- [x] Duplicar los grupos de una temporada a la siguiente — `3h · Media · Media`

> Duplicar temporada parece secundario hasta el septiembre en que el club tiene que recrear catorce grupos a mano.

> **Se llama `TrainingGroup`, no `Group`.** `GROUP` es palabra reservada de SQL y `Group` colisiona con la gramática de HQL. Tabla `training_groups`, paquete `training_group`, con `club_id` y policy propia (`migrations/1.2-training-groups-rls.sql`).

> **Categoría y nivel como enums cerrados.** `GroupCategory`: PREBENJAMIN, BENJAMIN, ALEVIN, INFANTIL, JUNIOR, ABSOLUTO, MASTER — nomenclatura RFEN, en español y sin tildes porque son nombres propios de la federación. `GroupLevel`: INICIACION, PERFECCIONAMIENTO, COMPETICION. Añadir un valor es una línea, pero **es cambio de contrato**.

> **El nivel es el que se va a quedar corto.** La categoría la fija la federación; el nivel lo fija cada club, así que con un segundo cliente probablemente haya que pasarlo a catálogo por club.

> **Entrenador opcional**, y estar en esa columna **no da permisos**: los da el rol. Por eso tampoco se valida que el usuario asignado sea personal técnico — una asignación equivocada es un error de datos, no un agujero. Si algún día el grupo concede acceso, eso hay que revisarlo.

> **Nombre único dentro de la temporada**, y repetible entre temporadas: "Alevín A" cada curso es el caso normal.

> **Duplicar copia el grupo, no la composición.** Que el equipo cambie cada año es el motivo de que el grupo cuelgue de la temporada. Se niega si el destino ya tiene grupos: es la protección contra el doble clic, que aquí deja veintiocho grupos donde debería haber catorce.

> **Borrado lógico**, porque en la 1.3 colgarán las pertenencias. Cuando exista `AthleteGroup` habrá que decidir si borrar un grupo con miembros se permite o se bloquea.

### 1.3 Pertenencia histórica — 10 h

- [x] Entidad `AtletaGrupo`: `id`, `atleta_id`, `grupo_id`, `fecha_alta`, `fecha_baja`, `motivo_baja` — `1h · Baja · Crítica`
- [x] Regla: no dos pertenencias abiertas al mismo grupo — `1h · Media · Alta`
- [x] Permitir varios grupos simultáneos (natación + preparación física) — `1h · Media · Media`
- [x] Endpoint de asignación: cierra la anterior si aplica y abre la nueva — `2h · Media · Crítica`
- [x] Endpoint de baja: rellena `fecha_baja`, nunca borra — `1h · Baja · Crítica`
- [x] Consulta de miembros a una fecha dada — `2h · Alta · Crítica`
- [x] Consulta de histórico de un atleta por temporada — `2h · Media · Alta`

> La consulta "miembros a una fecha dada" es la pieza sobre la que se apoya toda la Fase 2. Merece la pena hacerla bien y con tests.

> **Entidad `AthleteGroup`**, en el paquete `training_group`. El campo se llama `trainingGroup` y no `group`: `group` es palabra reservada también en HQL y rompe cualquier consulta que lo use.

> **`leftOn` es el ÚLTIMO DÍA de pertenencia, incluido.** Un atleta con baja el 30 de junio cuenta como miembro el 30 de junio. La Fase 2 hereda este criterio entero: un error de un día se multiplica por cada sesión y no se ve hasta que alguien reclama una falta de hace tres meses. Hay test de los cuatro extremos.

> **Varios grupos a la vez sí; dos veces en el mismo grupo no.** Lo sostienen el servicio y un índice único **parcial** sobre `(athlete_id, group_id) WHERE left_on IS NULL`. Las cerradas se repiten libremente: volver a entrar en un grupo años después es histórico, no duplicado.

> **Mover de grupo es explícito**, con `replacesGroupId`. "Cerrar la anterior" no se puede deducir cuando un atleta pertenece legítimamente a dos sitios: adivinarlo daría de baja al nadador de preparación física cada vez que cambia de grupo de natación.

> **El motivo de baja es un enum cerrado**, no texto libre: un campo de notas en un registro de menores es la vía más corta a que alguien escriba una causa médica donde no debe. Por lo mismo **no hay valor de lesión** — eso es una decisión de RGPD, no una constante.

> **El alta tiene que caer dentro de la temporada del grupo.** Es el primer uso real de `Season.covers()`.

> **Los dos endpoints de esta tarea se hacen en la 1.4**, junto con los suyos, para diseñar las rutas una sola vez. El dominio y las reglas ya están.

> **Queda abierto:** `plazas_max` del grupo no se comprueba al asignar. Hoy es un dato informativo; hay que decidir si bloquea el alta o solo avisa en la interfaz.

### 1.4 API — 6 h

- [x] `GET /grupos?temporadaId=` con conteo de atletas — `1h · Baja · Alta`
- [x] `GET /grupos/{id}/atletas` — `1h · Baja · Crítica`
- [x] `POST /grupos/{id}/atletas`, individual y en lote — `2h · Media · Crítica`
- [x] `DELETE /grupos/{id}/atletas/{atletaId}` (baja lógica) — `1h · Baja · Alta`
- [x] `GET /atletas/{id}/historial-grupos` — `1h · Baja · Media`

> **Rutas reales**, en inglés como el resto de la API: `GET /api/groups?seasonId=`, `GET|POST /api/groups/{id}/athletes`, `DELETE /api/groups/{id}/athletes/{athleteId}` y `GET /api/athletes/{id}/group-history?seasonId=`. **Sin cambios en `SecurityConfig`**: caen bajo reglas que ya existían.

> **Un solo endpoint de alta para el caso individual y el lote.** Dar de alta a uno es una lista de uno; dos endpoints para lo mismo solo consiguen que uno se quede sin mantener.

> **El lote se aplica entero o no se aplica.** Si un atleta ya está en el grupo, no entra ninguno: media asignación deja al club sin saber quién entró, y lo descubre pasando lista.

> **`GET /api/groups/{id}/athletes` acepta `?date=`**: sin ella los de hoy, con ella los que estaban ese día. Es lo que la Fase 2 necesita para pasar lista de una sesión pasada.

> **El `DELETE` no borra la fila.** El verbo describe lo que pasa de puertas afuera —el atleta deja de estar en el grupo—, no lo que ocurre en la tabla. Aquí es honesto, a diferencia de la revocación de un consentimiento, donde la fila *es* la prueba.

> **`memberCount` en el listado**, resuelto en una sola consulta agrupada: catorce grupos no pueden costar catorce viajes a la base para dibujar una tabla. Es cambio de contrato aditivo en `TrainingGroupResponse`.

> **`plazas_max` sigue sin bloquear el alta.** Va en la respuesta junto a `memberCount` para que la interfaz avise, pero no impide meter al niño trece en un grupo de doce. Decisión pendiente: ¿bloquea o solo avisa?

### 1.5 Frontend — 9 h

- [ ] Selector de temporada persistente en la cabecera — `2h · Media · Alta`
- [ ] Listado y detalle de grupos — `3h · Baja · Crítica`
- [ ] Pantalla de asignación con selección múltiple — `3h · Media · Crítica`
- [ ] Vista de histórico en la ficha del atleta — `1h · Baja · Media`

---

## Fase 2 — Horarios y asistencia

**Depende de:** Fase 1

### 2.1 Horario recurrente — 5 h

- [x] Entidad `HorarioGrupo`: `grupo_id`, `dia_semana`, `hora_inicio`, `hora_fin`, ~~`ubicacion`~~ `modalidad`, `vigente_desde`, `vigente_hasta` — `1h · Baja · Crítica`
- [x] Varios horarios por grupo — `1h · Baja · Crítica`
- [x] CRUD dentro del detalle del grupo — `3h · Baja · Alta`

> **Entidad `GroupSchedule`**, tabla `group_schedules`, en el paquete `training_group`. Es una propiedad del grupo, como `AthleteGroup`, y así el generador de sesiones de la 2.2 la alcanza por servicio y no por repositorio ajeno.

> **`ubicacion` se cambió por `modality`**, enum cerrado `SWIMMING` / `DRYLAND`. Es lo que el club necesita distinguir —agua o seco—, y evita el texto libre. Va en el **horario y no en el grupo**: un mismo grupo hace agua el martes y seco el jueves, y ponerlo en el grupo obligaría a partir "Alevín A" en dos grupos con los mismos nadadores y duplicar su composición y su histórico. Añadir un valor es **cambio de contrato**.

> **Sin `club_id` ni policy de RLS**: es tabla hija y llega a su club por `group_id`. La contrapartida es que un `findById` suyo por clave primaria no lo tapa nada, y por eso **toda la API cuelga del grupo** (`/api/groups/{id}/schedules`): el servicio carga primero el grupo —que sí está bajo policy— y comprueba que el horario pertenece a él. Hay test de que un horario ajeno no se alcanza colgándolo de un grupo propio.

> **`validUntil` es el ÚLTIMO día de vigencia, incluido**, heredando el criterio de `AthleteGroup.leftOn`. Es el punto donde un error de un día no cuesta un día: cuesta una sesión por semana durante todo el rango que genere la 2.2. Hay test de los dos extremos, por arriba y por abajo.

> **Dos horarios del mismo grupo no se pisan**: mismo día de la semana, franjas horarias solapadas y vigencias coincidentes, los tres a la vez. Las franjas que solo se tocan —17:00–18:00 y 18:00–19:00— sí se permiten: encadenar seco y agua es un caso real. Solo en el servicio, no en la base: la restricción de exclusión pediría `btree_gist`, y una extensión que exige superusuario en cada despliegue es desproporcionada para prevenir un error de tecleo. Misma decisión que con el solape de temporadas en la 1.1.

> **Entre grupos distintos no se comprueba nada.** Dos grupos a la misma hora es normal. El conflicto real sería de ocupación de calle, y sin ubicación no hay nada que comparar: **si algún día el club quiere control de calles, la ubicación tiene que volver, y como catálogo por club, no como texto libre.**

> **La vigencia cae dentro de la temporada del grupo**, por los dos extremos. Segundo uso de `Season.covers()` después de la 1.3.

> **Ordenación en Java, no en SQL.** El día de la semana se guarda como texto (`DayOfWeek` de `java.time`, para no depender de si la semana empieza en lunes o en domingo), así que un `ORDER BY day_of_week` devuelve FRIDAY, MONDAY, SATURDAY. Hay test que se pone rojo si alguien mueve la ordenación al repositorio.

> **Borrado lógico**, como `TrainingGroup`: en la 2.2 las sesiones colgarán del horario que las generó. Borrar un horario **sí libera su franja** para uno nuevo. Qué pasa con las sesiones futuras ya generadas al borrar o editar un horario **es decisión de la 2.2**, no de aquí.

> **Sin cambios en `SecurityConfig`**: caen bajo las reglas de `/api/groups/**` que ya existían. El entrenador consulta, el administrador monta el horario. **Hay test de endpoint que lo sostiene**: abriendo la ruta anidada a cualquier autenticado se pone en rojo, así que la afirmación no es una suposición.

> **28 tests**: 20 de servicio (reglas y vigencia) y 8 de endpoint (permisos, códigos de error y aislamiento por HTTP).

> **Cambio de contrato aditivo**: endpoints y DTOs nuevos, nada existente modificado.

### 2.2 Generación de sesiones — 16 h

La parte con más trampas del proyecto.

**Partida en dos bloques.** La 2.2.a es el núcleo —la sesión y su generación— y termina en algo probable a mano; la 2.2.b es el envoltorio automático.

**2.2.a — la sesión y su generación**

- [x] Entidad `Sesion`: `grupo_id`, `horario_id`, `fecha`, `hora_inicio`, `hora_fin`, ~~`ubicacion`~~ `modalidad`, `estado`, `motivo_cancelacion` — `1h · Baja · Crítica`
- [x] Servicio que materializa sesiones desde los horarios para un rango — `4h · Alta · Crítica`
- [x] **Idempotencia**: índice único `(horario_id, fecha)` y lógica que no duplica — `2h · Alta · Crítica`
- [x] Cancelar una sesión concreta con motivo — `1h · Baja · Alta`
- [x] Crear sesión puntual fuera de horario (competición, extra) — `1h · Baja · Media`

**2.2.b.1 — calendario de excepciones**

- [x] Calendario de excepciones: festivos y cierres de piscina — `3h · Media · Alta`
- [x] Reactivar una sesión cancelada — `1h · Baja · Alta` *(no estaba en el roadmap; se añadió al hacer que el sistema cancele solo)*

**2.2.b.2 — automatización**

- [x] Job `@Scheduled` que mantiene generadas las próximas 4–6 semanas — `2h · Media · Crítica`
- [x] Regeneración al cambiar un horario: solo sesiones futuras, jamás las pasadas — `2h · Alta · Crítica`

> **`SessionGenerationJob`, a las 3:30.** Recorre los clubes activos, fija el `TenantContext` de cada uno y genera **6 semanas** de cada grupo de su temporada activa. Se apoya entero en que la generación es idempotente: pasa todas las noches por un rango que se solapa casi por completo con el de ayer.

> **El `TenantContext` lo pone el propio bucle**, porque aquí no hay petición ni JWT, y lo limpia en un `finally`: es un `ThreadLocal` y los hilos del planificador se reutilizan, así que un club que se quedara pegado se lo llevaría el siguiente. Hay test de que queda limpio, y se pone rojo si se quita el `finally`.

> **Un club que falla no tumba a los demás**: try/catch por club, con el slug en el log. Un club recién creado sin temporada activa no es un error, es un club por el que hoy no hay que pasar — para eso se añadió `SeasonService.findActiveSeason()`, que devuelve `Optional` en vez de lanzar.

> **`ClubService.findAllActive()` es la única consulta del proyecto que cruza clubes a propósito**, y puede porque `clubs` es la tabla raíz del tenant: no lleva `club_id` ni policy, ya que es la lista de tenants y no datos de uno.

> **Tres propiedades nuevas** en `application.properties`, las tres con valor por defecto: `app.sessions.generation.enabled`, `.cron` y `.weeks-ahead`. Apagarlo no rompe nada —las sesiones se siguen generando a mano desde su endpoint—; hace falta el día que haya más de una instancia, para que solo una haga el trabajo.

> **El horizonte lo comparten el job y la regeneración.** Si no fuera el mismo número, cambiar un horario dejaría un calendario más corto o más largo que el que mantiene el job, y la diferencia solo se notaría semanas después.

> **La regeneración solo mira hacia adelante**, y estrictamente: hoy no se toca, porque a las 20:00 el entrenamiento de las 18:00 ya ocurrió. Se lleva también las **canceladas** futuras —si el grupo se muda del martes al miércoles, la cancelación de un martes que ya no existe no explica nada— pero nunca las `DONE`.

> **`discardFutureForSchedule` cierra la pregunta que dejó abierta la 2.1**: un horario borrado no puede seguir poniendo entrenamientos en el calendario, así que se lleva sus futuras sin regenerarlas.

> **Sin apagar el job en los tests**, y no hace falta: el cron es de madrugada y ninguna suite dura lo suficiente. Los tests llaman al método directamente, que es como hay que probarlo — uno que dependiera del reloj no sería un test.

> **La regeneración se cablea con un servicio de coordinación, `ScheduleChangeService`, en `training_session`.** El horario vive en `training_group` y las sesiones en `training_session`, que ya depende de él: si el servicio de horarios llamara a la regeneración, los dos módulos se llamarían en círculo y Spring no arrancaría. Se descartaron las otras dos vías: **eventos de Spring**, que resolvían el círculo pero metían un patrón nuevo usado en un solo sitio y una dependencia que el compilador deja de ver; y **encadenar las dos llamadas en el controlador**, que no vale porque **un controlador no es transaccional** — el cambio del horario haría commit por su cuenta y, si la regeneración fallara después, el calendario quedaría describiendo un horario que ya no existe.

> Con el servicio de coordinación, `@Transactional` propio y los de debajo uniéndose a esa transacción, **o cambian las dos cosas o no cambia ninguna**. Lo que no da es que el efecto viaje con el cambio: quien edite un horario yendo directo a `GroupScheduleService` se salta la regeneración. Hoy no hay ningún camino así, y **hay test de endpoint que se pone rojo si alguien deshace el cableado** — que es justo el fallo silencioso que se temía de los eventos.

> **`flush()` explícito tras borrar las futuras**, antes de regenerar: en el flush de Hibernate los `INSERT` van *antes* que los `DELETE`, así que sin él la regeneración podría intentar insertar `(horario, fecha)` antes de haber borrado la fila vieja, y saltaría el índice único.

> **Entidad `ClubClosure`**, tabla `club_closures`, con `club_id` y policy propia (`migrations/2.2-club-closures-rls.sql`) — entidad raíz, así que esta vez por la regla de siempre y no por una excepción. Vive en el paquete `training_session` y no en `club` porque su único efecto es sobre las sesiones: ponerlo ahí evita que los dos módulos se llamen en círculo.

> **Un cierre no impide generar: hace que la sesión nazca cancelada.** Es la decisión de fondo. Si el 6 de diciembre no hubiera nada en el calendario, nadie sabría si es que era festivo o si el job no llegó a pasar por esa semana; así el calendario dice "6 de diciembre, cancelado: festivo". De paso hereda gratis que una cancelada no resucita al regenerar.

> **La modalidad acota el cierre.** Nulo afecta a todo; con `SWIMMING` solo tumba lo del agua. Sin esto, cerrar la piscina cancelaría el entrenamiento del gimnasio, que con la modalidad viviendo en el horario sería un error visible desde el primer festivo.

> **Declarar un cierre cancela lo ya generado, pero solo de hoy en adelante.** Lo pasado no se reescribe: si el 12 de marzo se entrenó y se pasó lista, declarar hoy que aquel día fue festivo no puede borrarlo. El día de hoy sí entra —la piscina se puede romper esta mañana—. Y no pisa el motivo de una sesión ya cancelada a mano.

> **La respuesta del alta trae `cancelledSessions`**: cuántos entrenamientos se ha llevado por delante. Equivocarse de fechas tumba veinte de golpe y quien lo hace tiene que enterarse en ese momento.

> **`POST /api/sessions/{id}/reactivation`**, y lo puede el entrenador igual que cancelar: quien puede equivocarse tiene que poder deshacerlo sin esperar al club. **Reactivar gana sobre el cierre**: el generador solo crea lo que no existe, así que no vuelve a tumbarla. Hay test, porque sin eso la reactivación no serviría de nada.

> **Borrar un cierre no reactiva nada**, a propósito: no hay forma de distinguir las que cayeron por ese cierre de las que alguien canceló a mano el mismo día. Lo que sí consigue es que lo que se genere después ya no nazca cancelado.

> **Sin único sobre las fechas**: dos cierres solapados no son un error. Declarar el puente y además la semana entera es una forma legítima de decirlo.

> **18 tests.** Y una corrección de método: el aislamiento de los cierres en las consultas por rango **lo hace el filtro de Hibernate, no RLS** —se comprobó desactivando la policy y seguían en verde—. Lo que solo tapa RLS es la carga por id, que es como llega el borrado: eso tiene test propio aquí y en `TenantIsolationTest`.

> **Guardia de la regla 1**, en `TenantIsolationTest.ningunaTablaConClubIdSeQuedaSinPolicy`: recorre el catálogo de Postgres y falla si alguna tabla con `club_id` no tiene RLS **activo, forzado y con policy**. Va contra el catálogo y no contra una lista escrita a mano, porque una lista hay que acordarse de actualizarla y ese es justamente el olvido del que protege. Se pone rojo con `NO FORCE` y con `DISABLE`. Una tabla con RLS pero sin ninguna policy no llega a él: Postgres la interpreta como "denegar todo" y el fallo salta antes, al insertar — que es el lado seguro. Cubre las dos formas de perder el aislamiento sin enterarse: una entidad raíz nueva sin migración, y un entorno donde no se pasaron.

> Si el job duplica sesiones, la asistencia queda inconsistente y el club pierde la confianza en el sistema entero. La idempotencia no es opcional.

> **Entidad `TrainingSession`**, tabla `training_sessions`, en un paquete nuevo `training_session`: de aquí colgará la asistencia de la 2.3. Alcanza los horarios por `GroupScheduleService` y el grupo por `TrainingGroupService`, nunca por sus repositorios.

> **Lleva `club_id` y policy propia (`migrations/2.2-training-sessions-rls.sql`) aunque sea tabla hija.** Segunda excepción deliberada a la regla, después de `consents`, y por un motivo distinto: **es la primera tabla hija cuyo id viaja solo en la API**. La 2.3 expone `/api/sessions/{id}/roster` para el móvil, y un id suelto es un `findById` por clave primaria — justo donde el filtro de Hibernate no llega. La alternativa era anidarlo todo bajo el grupo, como en la 2.1, y obligar al móvil a una ruta de cuatro segmentos.

> **La sesión copia hora y modalidad del horario, no las lee de él.** Si en marzo el grupo se mueve de las 18:00 a las 19:00, las de febrero siguen diciendo 18:00: es la hora a la que se entrenó. Hay test de que cambiar el horario no toca lo ya generado.

> **Sin zona horaria**: `LocalDate` + `LocalTime` sueltos. Un entrenamiento a las 18:00 es a las 18:00 también el fin de semana en que cambia la hora.

> **La idempotencia son dos capas**: el generador consulta los pares `(horario, fecha)` que ya existen y crea solo lo que falta; el índice único es la red. **Una sesión cancelada no resucita** al volver a generar, que es el caso que más duele. Hay test de las dos cosas, y de los rangos solapados, que es como llamará el job.

> **`schedule_id` nulo en las puntuales**, y eso hace que el índice único no les aplique —en Postgres dos nulos no son iguales—, así que una competición y un entrenamiento extra el mismo día conviven, y una puntual no impide generar el entrenamiento de ese día.

> **Tope de 400 días** por generación: un cero de más en una fecha son cientos de miles de filas.

> **Motivo de cancelación como enum cerrado**, sin nota libre, igual que `LeaveReason`: un campo de texto aquí acaba con el nombre del nadador que se puso malo. El precio es `OTHER`, que no informa; si un motivo se repite bajo `OTHER`, la respuesta es añadir el valor, no abrir el texto libre.

> **Cancelar lo puede el entrenador**, y es la primera vez que el personal técnico escribe algo: es quien se entera de que hoy no hay piscina, y esperar al administrador deja la sesión como celebrada cuando nadie se metió al agua. Generar el calendario y crear sesiones sueltas siguen siendo del club.

> **`SecurityConfig` sí se toca**, a diferencia de la 2.1: `/api/sessions/**` es rama nueva y nace cerrada a ADMIN salvo la consulta y la cancelación.

> **26 tests**: 19 de servicio y 7 de endpoint, más uno en `TenantIsolationTest`.

> **Los índices únicos se declaran en `schema.sql` y no en la anotación `@Table`.** Declararlos en los dos sitios creaba dos restricciones equivalentes, y la de Hibernate lleva un nombre generado (`uk5a01qd9seiex615ick5rt6obp`) distinto en cada base, que no se puede nombrar en una migración ni en un `ON CONFLICT`. Se quitó de `TrainingSession` y de `TrainingGroup`, que arrastraba lo mismo. Como `ddl-auto=update` solo añade y nunca borra, las que ya existían hay que quitarlas con `migrations/2.2-limpiar-uniques-duplicados.sql`, **una vez por entorno**.

> **Queda abierto, y es el problema contrario:** `user_athletes` y `athlete_guardians` tienen su único **solo** con el nombre generado por Hibernate. El de `schema.sql` va dentro de un `CREATE TABLE IF NOT EXISTS` que no llegó a ejecutarse porque la tabla ya existía, así que nunca se creó. No se puede quitar la anotación sin dejarlas sin restricción: hay que sacar esos únicos a sentencias propias primero.

> **Queda abierto:** una sesión cancelada por error no se puede reactivar. El generador no la repone —es lo correcto— así que hoy el único arreglo es tocar la base. Con la cancelación en manos del entrenador esto se vuelve más probable: ¿hace falta un endpoint de reactivación?

### 2.3 Asistencia — 12 h

- [x] Entidad `Asistencia`: `sesion_id`, `atleta_id`, `estado`, `registrado_por`, `registrado_en` — `1h · Baja · Crítica`
- [x] **Índice único `(sesion_id, atleta_id)`** — `30min · Baja · Crítica`
- [x] `GET /sesiones/{id}/lista`: atletas del grupo con su estado, en una sola llamada — `3h · Media · Crítica`
- [x] `PUT /sesiones/{id}/asistencia`: guardado en lote — `2h · Media · Crítica`
- [x] La lista son los atletas con pertenencia activa **en la fecha de la sesión** — `3h · Alta · Crítica`
- [x] Marcar la sesión como `REALIZADA` al guardar — `1h · Baja · Alta`
- [x] Tests de concurrencia: dos entrenadores pasando lista a la vez — `2h · Alta · Alta`
- [x] **Constraints de `attendance` como sentencias sueltas**, no dentro del `CREATE TABLE` — `30min · Baja · Alta`

> El endpoint `/lista` es el que consumirá el móvil. Diseñarlo ahora pensando en una sola llamada te ahorra rehacerlo en la Fase 3.

> **`observaciones` se descartó**, y es la decisión del bloque. `docs/rgpd.md` §9 la marca como señal de alarma: un texto libre en el registro de asistencia de un menor, visible para todo el personal técnico, acaba conteniendo *"no vino, está con gastroenteritis"* — dato de salud, categoría especial del art. 9, sin base legal y sin que el tutor lo sepa. Mismo criterio que `LeaveReason` y `CancellationReason`. **`EXCUSED` tampoco guarda el motivo**: justificar una falta es una conversación con el tutor, no una columna.

> **`AttendanceStatus`**: `PRESENT`, `ABSENT`, `EXCUSED`, `LATE`. `PRESENT` y `LATE` cuentan como asistencia para los porcentajes de la 2.4.

> **Sin `club_id`**, a diferencia de `training_sessions`: el id de una asistencia no viaja solo, se entra siempre por `/api/sessions/{id}/...`. Es el patrón de `group_schedules`.

> **Rutas reales**: `GET /api/sessions/{id}/roster` y `PUT /api/sessions/{id}/attendance`. El `PUT` es idempotente a propósito — mandar dos veces lo mismo deja el mismo estado, que es lo que hace falta cuando la conexión se cae a mitad y el móvil reintenta. Las dos devuelven el roster completo, para que guardar no obligue a una segunda llamada.

> **El roster lleva id y nombre, y nada más.** Ni DNI ni fecha de nacimiento: `docs/rgpd.md` §3. Hay test de endpoint que lo comprueba sobre el JSON.

> **Los atletas son los de la fecha de la sesión**, vía `AthleteGroupService.membersOn` — la consulta de la 1.3. Hay test de que un atleta que dejó el grupo sigue apareciendo en la sesión anterior a su baja y no en la posterior.

> **Guardado incremental**: no hace falta mandar a todos, lo que no venga se queda como estaba. Pero **entero o nada** dentro de lo que se manda: si un atleta de la lista no pertenecía al grupo ese día, no entra ninguno.

> **La concurrencia se resuelve con `ON CONFLICT ... DO UPDATE`**, SQL nativo apoyado en el índice único. Con un leer-y-escribir, dos entrenadores verían la fila vacía a la vez y el segundo se estrellaría contra el índice; así gana el último en escribir y ninguno falla. Hay test con dos hilos.

> **Pasar lista marca la sesión `DONE`, salvo que sea futura.** Se permite pasar lista por adelantado, pero marcarla realizada antes de tiempo la contaría como celebrada en los informes de la 2.4 y —peor— la sacaría del alcance de la regeneración, que no toca las `DONE`: cambiar el horario dejaría de arrastrarla. No se pasa lista de una sesión cancelada.

> **Primera tabla del proyecto en la que `schema.sql` dice la verdad.** Sus tres claves foráneas y su índice único van como sentencias sueltas, y las relaciones llevan `@ForeignKey(NO_CONSTRAINT)` para que Hibernate no cree las suyas en paralelo — si lo hiciera habría dos claves foráneas sobre la misma columna y mandaría la más restrictiva, dejando la cascada sin efecto. **Ese es el patrón a seguir en la tarea 2.6.**

> **Consecuencia heredada de la 1.2**, que aquí se nota por primera vez: como `coach_id` no da permisos, cualquier entrenador del club puede pasar lista de cualquier grupo. Es coherente con el resto, pero es la primera vez que implica escribir datos de menores de un grupo que no llevas.

> **Ojo con las claves foráneas de `attendance`.** Es la primera tabla que cuelga de algo que **sí se borra físicamente**: la regeneración de la 2.2.b borra las sesiones futuras de un horario que cambia. Sin `ON DELETE CASCADE`, ese borrado empezará a fallar en cuanto una sesión tenga asistencia.
>
> Y ese `ON DELETE` **no se puede escribir dentro del `CREATE TABLE`**, porque ahí no se aplica —ver la cabecera de `schema.sql`—. Tiene que ir como `ALTER TABLE ... ADD CONSTRAINT` suelto, igual que el índice único `(sesion_id, atleta_id)`. Media hora, dentro de este bloque.

### 2.4 Informes — 9 h

**2.4.a — los informes**

- [x] Porcentaje de asistencia por atleta en un rango — `2h · Media · Alta`
- [x] Porcentaje de asistencia por grupo — `2h · Media · Alta`
- [x] Detección de ausencias consecutivas — `2h · Media · Media`
- [x] Aviso de lo pendiente de registrar — `2h · Media · Alta` *(no estaba en el roadmap)*

**2.4.b — exportación**

- [x] Exportación a CSV — `3h · Media · Media`

> **Lo exportan el administrador y el entrenador.** Se planteó dejarlo solo en ADMIN por ser una salida de datos del sistema, y se descartó con un argumento mejor: quien necesita el informe para trabajar es el cuerpo técnico, y **el administrador no tiene por qué formar parte de él** — puede ser el tesorero. Cae bajo la regla `GET /api/reports/**` que ya existía, así que `SecurityConfig` no se tocó.

> **El CSV lleva nombre y números, y nada más.** Ni DNI ni fecha de nacimiento: un archivo sale del sistema y ya no vuelve —se reenvía, acaba en una carpeta compartida, en un correo— así que lo que no salga ahí es lo único que seguro no acaba en ningún sitio. `docs/rgpd.md` §3. Hay test de endpoint que lo comprueba sobre el archivo generado.

> **Queda rastro en el log de quién exportó y qué rango**, sin ningún dato de atletas. Si algún día hay que responder de dónde salió un listado, esa línea es la respuesta. Es lo mínimo mientras no exista la auditoría de la S.6.

> **Tres decisiones de formato que deciden si el archivo se abre o no** en el ordenador de un club español: separador `;` —con comas, Excel en español abre todo en una sola columna—, **BOM de UTF-8** —sin él, "Alevín" sale como "AlevÃ­n"— y **coma decimal** en el porcentaje, o Excel lo lee como texto y no deja ni ordenar la columna. Un CSV "de manual" con comas y sin BOM se ve mal y parece que el sistema exporta mal.

> **Se neutralizan las fórmulas**: un campo que empiece por `=`, `+`, `-` o `@` lo ejecuta la hoja de cálculo. Es la inyección CSV de siempre; aquí el riesgo es pequeño porque los nombres los teclea el club, pero el arreglo cuesta una línea.

> **Solo se exporta el informe de grupo.** El de un atleta suelto se ve en pantalla y no hay caso real que pida sacarlo en archivo — y cada exportación que no existe es una vía menos por la que se van datos.

> **Qué se divide entre qué**, que es todo el bloque. El denominador son las sesiones **celebradas** —ni las canceladas ni las futuras: faltar a un entrenamiento que no existió no es faltar— en las que el atleta **pertenecía al grupo ese día**. `PRESENT` y `LATE` cuentan como asistencia.

> **Una sesión celebrada sin marcar cuenta como falta**, y sale además como incidencia con su fecha y su sesión para poder ir a corregirla. El dato no se infla, pero tampoco se esconde de dónde viene.

> **Una sesión pasada a la que nadie pasó lista es harina de otro costal**: no cuenta como falta de nadie. Contar catorce ausencias porque el entrenador no abrió el móvil no sería un dato, sería ruido en el historial de catorce familias. Sale aparte en `sessionsWithoutRoster`.

> **La media del grupo pondera por sesiones posibles**, no promedia los porcentajes de cada uno: quien solo pudo ir a dos sesiones no puede pesar lo mismo que quien pudo ir a veinte.

> **Un solo viaje a la base por informe**, con un `LEFT JOIN` desde las sesiones. El `LEFT` es la pieza: conserva la fila aunque no haya asistencia registrada, que es justo el caso que hay que contar y sacar como incidencia. Catorce atletas por seis semanas resueltos uno a uno serían cientos de consultas para pintar una tabla.

> **Ausencias consecutivas** con `threshold` ajustable, tres por defecto. Las no registradas rompen la racha igual que una falta, por coherencia con el criterio de arriba: si no se apunta a alguien, el sistema no puede decir que viene. **No avisa, no marca y no guarda nada**: es una consulta que el club mira.

> **`GET /api/reports/attendance/pending`** es el aviso: sesiones pasadas sin lista y sesiones con lista a medias, con cuántos faltan, para todo el club. Se consulta al entrar y se pinta como badge. Sin rango, los últimos 30 días — un aviso que arrastrara tres temporadas de olvidos no sería accionable, sería un número grande al que se deja de hacer caso.

> **Se descartó el job de las 00:00** que se planteó para esto. Sin canal de salida —no hay email ni push— un `cron` nocturno solo escribiría en un log que nadie lee. El aviso aparece donde la persona ya está mirando. **Si algún día se quiere correo de verdad**, hace falta `spring-boot-starter-mail`, configuración SMTP y decidir retención; sería el primer envío automático del sistema, y solo a personal del club: a menores no, `docs/rgpd.md` §9.

> **Deuda anotada:** el criterio de pertenencia está escrito **dos veces**, en la consulta por grupo y en la de por atleta. Si alguien cambia una y no la otra, los dos informes dirán cosas distintas del mismo nadador. Hay test en rojo para cada una, que es lo que hoy lo sostiene.

### 2.5 Frontend — 10 h

- [ ] Calendario de sesiones del grupo — `4h · Media · Alta`
- [ ] Pantalla de pasar lista con guardado en lote — `4h · Media · Crítica`
- [ ] Panel de informes con filtros — `2h · Media · Media`

### 2.6 Sanear las restricciones que `schema.sql` declara y no existen — 5–8 h

**No urge y no bloquea nada.** Sale de un hallazgo de la 2.2.b: lo que va dentro de un
`CREATE TABLE` en `schema.sql` no llega nunca a la base, porque Hibernate crea las
tablas antes. Ver la cabecera de `schema.sql`.

- [x] Sacar a `ALTER TABLE` sueltos los `ON DELETE` que se declaran y no existen — `3h · Alta · Media`
- [x] Recuperar el `UNIQUE (athlete_id, guardian_id)` de `athlete_guardians` — y de paso el de `user_athletes`, que tenía lo mismo — `30min · Media · Media`
- [x] Revisar los `DEFAULT` declarados que tampoco están — `1h · Media · Baja` — **revisados y descartados**, ver abajo
- [x] Test que compare lo declarado con lo real, como el de RLS — `2h · Alta · Alta`

> **Eran 13 desajustes, no 16.** Medidos comparando `schema.sql` con `pg_constraint`, no a ojo. Se aplicaron **12**; hoy la diferencia entre lo declarado y lo real es **cero**.

> **El que no se aplicó: `athlete_documents.uploaded_by_id`.** Declaraba `ON DELETE CASCADE`, que habría borrado los documentos de un atleta al dar de baja al usuario que los subió — el documento es del atleta, no de quien lo subió. Y no vale `SET NULL` porque la columna es `NOT NULL`. Se quitó la declaración para que el archivo dejara de prometerlo. **Arreglarlo de verdad es hacer la columna nullable, y eso es un cambio de modelo**: queda abierto. Es exactamente el caso que avisaba la nota de "revisar tabla por tabla antes de encender nada".

> **Las entidades llevan `@ForeignKey(ConstraintMode.NO_CONSTRAINT)`** en esas doce relaciones. Sin eso, Hibernate crea la suya en paralelo y **con dos claves foráneas sobre la misma columna manda la más restrictiva**: la cascada se quedaría de adorno. Es el patrón que salió de la 2.3.

> **`migrations/2.6-limpiar-fks-generadas.sql`** quita las que Hibernate ya había creado —`ddl-auto=update` solo añade, nunca borra lo que deja de estar declarado— y también los dos únicos generados. Una vez por entorno.

> **Los `DEFAULT` se revisaron y se dejaron como están.** Son casi todos `gen_random_uuid()` en claves primarias que Hibernate genera desde Java, así que no llegan a usarse nunca. Añadirlos solo serviría para que un `INSERT` al que le falta una columna dejara de fallar, que es lo contrario de lo que interesa.

> **`SchemaIntegrityTest` es el guardia**, con dos comprobaciones estructurales —ninguna columna con dos claves foráneas, ninguna tabla con dos únicos equivalentes, **ambas sin lista que mantener**— y una tercera con la lista explícita de las cascadas que el sistema sí ejercita. Las dos primeras cazan la regresión de Hibernate; la tercera documenta el contrato de borrado.

> **Un detalle que se descubrió probándolo:** la tercera comprobación no se puede poner en rojo tocando la base, porque arrancar la aplicación reejecuta `schema.sql` y **repara la cascada antes de mirarla**. Solo se pone roja quitando el `ALTER` del archivo, que es como se rompería de verdad. Que sea autorreparable es buena señal, pero conviene saberlo antes de confiar en ese test.

> **Aviso para el despliegue:** si un entorno tiene filas huérfanas, el `ADD CONSTRAINT` falla y **la aplicación no arranca**. Apareció al probar esto, con una fila que dejaron los propios experimentos. En una base con historia conviene comprobarlo antes de desplegar: `SELECT ... LEFT JOIN ... WHERE padre.id IS NULL` por cada clave foránea nueva.

> **Por qué no corre prisa:** hoy no rompe nada, porque casi todo el borrado del sistema
> es lógico —`deleted_at`, `left_on`, `revoked_at`— así que esas cascadas no llegan a
> ejercitarse nunca. La excepción aparece en la 2.3 y está resuelta allí, en su tarea.

> **Dificultad añadida:** las claves foráneas actuales las creó Hibernate con nombres
> generados (`fk629dnuhbjn2lukkl8aj4b25mi`), distintos en cada base. Sustituirlas pide un
> bloque `DO` que las localice, como el de `migrations/2.2-limpiar-uniques-duplicados.sql`.

> **Cuidado al aplicarlo:** añadir un `ON DELETE CASCADE` a una tabla con datos cambia lo
> que se lleva por delante un borrado que hoy simplemente falla. Hay que mirar tabla por
> tabla antes de encenderlo, no aplicarlo en bloque.

> **Si se hace Flyway antes, esta tarea desaparece**: el esquema pasaría a ser lo que
> dicen las migraciones y este desajuste dejaría de existir.

---

## Fase 3 — Preparar la API para móvil

No requiere escribir la app. Es lo que evita rehacer la API después.

**Depende de:** Fase 2

- [ ] **Refresh tokens**: access de 15 min, refresh de 30 días con rotación — `5h · Alta · Crítica`
- [ ] Endpoint de revocación (dispositivo perdido) — `2h · Media · Crítica`
- [ ] Paginación en todos los listados — `3h · Baja · Alta`
- [ ] Versionado de rutas `/api/v1/` — `2h · Baja · Alta`
- [ ] Formato de error consistente con `traceId` — `3h · Media · Alta`
- [ ] `updatedAt` en entidades sincronizables — `2h · Media · Alta`
- [ ] Endpoint de sincronización: grupos, atletas y sesiones del entrenador en un bloque — `4h · Alta · Alta`
- [ ] Documentación OpenAPI/Swagger — `2h · Baja · Media`
- [ ] Rate limiting por IP y por usuario — `2h · Media · Crítica`
- [ ] CORS revisado para el dominio de producción — `1h · Media · Crítica`

> Sin refresh tokens el entrenador se loguea cada día. Es la diferencia entre una app que se usa y una que se desinstala.

---

## Fase S — Seguridad y cumplimiento (RGPD + LOPDGDD)

> **Aviso:** no soy abogado y esto no es asesoramiento jurídico. Es un mapa de lo que hay que resolver y de qué partes tienen consecuencias técnicas. Revisa el bloque S.2 con alguien con formación jurídica antes de firmar con el primer club.

**Cuándo:** S.1 ya está hecho (va antes de la Fase 1). El resto, antes del primer cliente que pague.

### Marco aplicable

| Norma | Qué te afecta |
|---|---|
| RGPD (UE 2016/679) | Base legal, derechos, seguridad, brechas, EIPD, encargados |
| LOPDGDD (LO 3/2018) | **Edad de consentimiento: 14 años** (art. 7) |
| RGPD art. 9 | Datos de salud: minimizados a metadatos del certificado |
| RGPD art. 28 | Contrato de encargo obligatorio con cada club |
| ENS (RD 311/2022) | Solo si algún club es municipal o depende de administración pública |

**Roles:** el club es **responsable del tratamiento**, tú eres **encargado**.

### S.2 Documentación obligatoria — trabajo legal, no técnico

Nada de esto es código, pero sin ello no puedes vender.

- [ ] Contrato de encargo del tratamiento (art. 28) con cada club — `legal · Alta · Crítica`
- [ ] Registro de actividades de tratamiento (art. 30) — `4h · Media · Crítica`
- [ ] Evaluación de impacto (EIPD, art. 35) con la herramienta Gestiona EIPD de la AEPD — `8h · Alta · Crítica`
- [ ] Política de privacidad del servicio — `legal · Media · Crítica`
- [ ] Plantilla para que el club informe a las familias — `3h · Media · Alta`
- [ ] Formulario de consentimiento para tutores, con casillas separadas — `2h · Media · Crítica`
- [ ] Plazos de conservación por tipo de dato, documentados y aplicados en código — `4h · Media · Crítica`
- [ ] Valorar por escrito si necesitas Delegado de Protección de Datos (art. 34 LOPDGDD) — `2h · Media · Media`

### S.3 Seguridad de la aplicación

#### S.3.1 Autenticación — 13 h

- [ ] BCrypt con factor de coste ≥ 12 — `30min · Baja · Crítica`
- [ ] Política de contraseñas: mínimo 12 caracteres, sin composición forzada — `1h · Baja · Alta`
- [ ] Contrastar contra listas de contraseñas filtradas — `2h · Media · Media`
- [ ] Rate limiting en `/login` por IP y por usuario — `2h · Media · Crítica`
- [ ] Bloqueo temporal progresivo tras intentos fallidos — `2h · Media · Alta`
- [ ] **MFA obligatorio para roles de administración** (TOTP) — `4h · Alta · Crítica`
- [ ] Recuperación de contraseña con token de un solo uso y caducidad corta — `3h · Media · Crítica`
- [ ] Respuestas de login que no revelen si el usuario existe — `1h · Media · Alta`
- [ ] **Rotar toda credencial compartida en conversación o presente en el histórico de git** — `1h · Baja · Crítica`

#### S.3.2 JWT — 7 h

- [ ] Secreto de firma ≥ 256 bits, fuera del código, distinto por entorno — `1h · Baja · Crítica`
- [ ] Sin datos personales en el payload: solo `sub`, `club_id`, `rol`, `exp` — `30min · Baja · Crítica`
- [ ] Validar `exp`, `iss` y `aud` en cada petición — `1h · Media · Crítica`
- [ ] Lista de revocación para logout y dispositivos perdidos — `3h · Alta · Alta`
- [ ] Migrar a RS256 cuando entre el móvil — `2h · Media · Media`

#### S.3.3 Autorización — 10 h

**Partida en dos bloques**, porque son dos cambios de contrato distintos y meterlos en el
mismo commit deja sin saber cuál rompió qué. La a) cierra lo que hoy alcanza una cuenta de
tutor; la b) acota al entrenador.

**S.3.3.a — el guardián y el IDOR de tutores**

- [x] **Autorización a nivel de objeto, no solo de tenant** — `4h · Alta · Crítica`
- [x] Test específico de IDOR — `2h · Alta · Crítica`
- [x] Los tutores solo ven a sus propios hijos — `1h · Media · Alta`

**S.3.3.b — acotar al entrenador a sus grupos**

- [x] `coach_id` pasa a dar permisos: grupos, sesiones, roster, asistencia, informes y CSV — `4h · Alta · Crítica`
- [x] Entrenadores ayudantes: varios por grupo, con los mismos permisos que el principal — *(no estaba en el roadmap)*
- [x] Fichas, resultados, estados de consentimiento y certificado, documentos y claves de invitación: solo atletas que hoy están en sus grupos
- [ ] Denegar por defecto en cada endpoint — `1h · Media · Crítica` — **sigue pendiente**: `SecurityConfig` termina en `anyRequest().authenticated()` y no en `denyAll()`, así que una ruta nueva nace abierta a cualquier autenticado
- [ ] ~~Roles definidos: `SUPERADMIN`, `ADMIN_CLUB`, `ENTRENADOR`, `TUTOR`~~ — **descartado**

> El `club_id` no te protege del IDOR interno. Un entrenador del club A pidiendo la ficha de un atleta de otro grupo del club A pasa el filtro de tenant sin problema. Con datos de menores, es el fallo que peor sienta en una auditoría.

> **La lista de roles estaba desfasada y se retira.** Los reales son `ROLE_ADMIN`, `ROLE_EDITOR`, `ROLE_USER` y `ROLE_TECHNICAL_STAFF`; renombrarlos es cambio de contrato a cambio de nada, y "roles por club" sigue siendo decisión abierta. Lo que esta tarea tenía de trabajo real es la autorización por objeto.

> **`AccessGuard`, en un paquete `access/` propio** y no en `config/`. Responde una sola pregunta —"¿es tuyo?"— apoyándose en los servicios de los otros módulos, nunca en sus repositorios.

> **Se llama desde los controladores, jamás desde los servicios.** Es lo que evita el ciclo `athlete → access → athlete_link → athlete`, con el que Spring no arrancaría. La contrapartida es que quien llegue al servicio por otra vía se lo salta: hoy no hay ninguna, y lo que lo sostiene son los tests de endpoint.

> **Deniega con 404, no con 403**, siguiendo `docs/convenciones.md`. Hay test de que un documento ajeno y uno inexistente contestan lo mismo: con 403, el propio código de error confirma que ese documento existe.

> **El agujero que cierra, y era real:** `GET /api/athlete-documents/{id}/file` estaba en `isAuthenticated()` y el servicio no comprobaba nada. Cualquier cuenta del sistema abría el archivo de cualquier atleta con solo tener el id, **`MEDICAL` incluido**, que es lo más sensible que guarda el sistema. Y `athlete_documents` es tabla hija: sin `club_id` ni policy, tampoco lo tapaba RLS, así que el id de otro club también servía — y el `DELETE`, que es físico y se lleva el archivo del disco, igual.

> **La foto de perfil tenía lo mismo:** `POST /api/users/{id}/profile-photo` está en `authenticated()` porque cada uno cambia la suya, y sin comprobar el id eso significaba que cualquiera sobrescribía la de cualquier otro.

> **La regla de la subida vivía dentro de `AthleteDocumentService`**, leyendo el `SecurityContext` a mano. Se movió al guardián: era la misma regla que la de la descarga, escrita una sola vez y aplicada solo en la mitad de los sitios. El efecto visible es que subir a un atleta ajeno pasa de **403 a 404**.

> **12 tests en `access/ObjectAccessTest`, y verificado que fallan cuando deben:** desactivando el guardián se ponen rojos exactamente los seis negativos y los seis positivos siguen verdes. Uno de los negativos —la foto sobre una cuenta de otro club— **se queda verde**, porque a ese lo sostiene RLS y no el guardián; está anotado en el propio test para que nadie cuente con él como prueba de esta tarea.

> **Decisiones del club para la b):** un grupo tiene entrenador principal y ayudantes, con los mismos permisos; el entrenador ve a los atletas que **hoy** están en sus grupos; mantiene el alta de atletas y las claves de invitación —estas, solo para atletas suyos—; y el listado de grupos le muestra solo los suyos.

> **`coach_id` sigue siendo el principal y los ayudantes van en `group_assistant_coaches`**, tabla hija sin `club_id`: se llega a ella siempre por el grupo, que sí está bajo policy. Así `coachId` sigue en la API y el contrato solo crece: `assistantCoachIds` en la petición y `assistantCoaches` en la respuesta. La duplicación de temporada copia también a los ayudantes. Claves foráneas como `ALTER` sueltos con su `ON DELETE CASCADE`, y las dos añadidas a `SchemaIntegrityTest`.

> **Ficha vigente, roster histórico.** La ficha de un nadador que dejó el grupo ayer deja de ser visible, pero el roster de la sesión de la semana pasada lo sigue trayendo: a la sesión se llega por el grupo, no por el atleta. Hay test de las dos cosas, porque es justo la diferencia que un cambio descuidado borraría.

> **Asignar entrenador exige rol técnico o de administrador**, y devuelve 400 si no. Hasta ahora no se comprobaba a propósito —la columna no daba permisos—, y el comentario del propio servicio pedía cambiarlo el día que los diera.

> **Consecuencia aceptada de dejar el alta al entrenador:** un atleta recién creado no está en ningún grupo, así que quien lo crea no lo ve hasta que el administrador lo mete en uno de los suyos.

> **El informe de asistencia de un atleta suma todos sus grupos** también para un entrenador que solo lleva uno: es el dato del nadador y no lleva más que números.

> **Corregido de paso, y era mío de la a):** `requireUserAccess` documentaba "la propia cuenta o el administrador", pero dejaba pasar cualquier rol de club. Un entrenador podía cambiar la foto de perfil de cualquier usuario. Hay test.

> **Verificado que los tests fallan cuando deben.** Tratando al entrenador como administrador —que es el comportamiento anterior a la b)— se ponen rojos 13: los once negativos de `CoachScopeTest` y los dos del entrenador en `ObjectAccessTest`. Siguen verdes los positivos y las dos validaciones de asignación, que viven en el servicio.

> **Siete archivos de test ajustados**, todos del mismo modo: sus fixtures montaban grupos sin entrenador y comprobaban que un entrenador entraba. Es el cambio de contrato, no una regresión. La suite pasa de 265 a 284.

> **Deuda anotada:** el criterio de pertenencia con los dos extremos incluidos ya está escrito **tres veces** —`findMembersOn`, la consulta del informe de la 2.4 y ahora `findAthleteIdsInGroupsOn`—. Si uno cambia y otro no, un entrenador vería en el roster a un nadador cuya ficha no puede abrir.

> **Rendimiento, anotado para la Fase 3:** comprobar el acceso de un entrenador a un atleta cuesta tres consultas. Con un club no se nota; el endpoint de sincronización del móvil tendrá que resolverlo de una vez.

> **Cambio de contrato para el frontend:** para un entrenador, los grupos, sesiones, rosters, informes, fichas, resultados, estados y claves fuera de sus grupos pasan a 404, y los listados de grupos, atletas, resultados y pendientes llegan filtrados. El administrador no cambia.

#### S.3.4 Entrada y salida — 10 h

- [ ] Bean Validation en todos los DTO — `3h · Baja · Alta`
- [ ] Nunca exponer entidades JPA en la API, siempre DTO — `3h · Media · Alta`
- [ ] Revisar todas las queries nativas — `1h · Alta · Crítica`
- [ ] Límite de tamaño en subidas y validación de tipo real, no de extensión — `1h · Media · Alta`
- [ ] Cabeceras: CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, HSTS — `2h · Media · Alta`

### S.4 Seguridad de la infraestructura

#### S.4.1 VPS de Hetzner — 8 h

- [ ] SSH solo con clave, `PasswordAuthentication no`, sin login de root — `1h · Baja · Crítica`
- [ ] Firewall: solo 22 y lo que exija el túnel — `1h · Media · Crítica`
- [ ] `fail2ban` en SSH — `1h · Media · Alta`
- [ ] `unattended-upgrades` para parches de seguridad — `1h · Baja · Alta`
- [ ] **Cifrado del disco en reposo (LUKS)** — `3h · Alta · Crítica`
- [ ] Usuario no privilegiado para la aplicación — `1h · Baja · Alta`

> LUKS es requisito del art. 32 y es muy difícil de añadir con el servidor ya en producción. Hazlo al montar el VPS, no después.

#### S.4.2 Docker — 5 h

- [ ] Contenedores como usuario no root — `2h · Media · Alta`
- [ ] Imágenes base mínimas con versión fijada, nunca `latest` — `1h · Baja · Alta`
- [ ] Compose de producción sin puertos expuestos al host — `30min · Baja · Crítica`
- [ ] Escaneo de imágenes con Trivy en el pipeline — `1h · Media · Media`
- [ ] Sin secretos en el `Dockerfile` ni en variables de build — `30min · Baja · Crítica`

#### S.4.3 PostgreSQL — 3 h

- [ ] Puerto 5432 **nunca** expuesto a internet — `30min · Baja · Crítica`
- [ ] Usuario de aplicación con permisos mínimos y sin `BYPASSRLS` — `1h · Media · Crítica`
- [ ] `ssl = on` en las conexiones — `1h · Media · Alta`
- [ ] Logs sin datos personales — `30min · Baja · Alta`

#### S.4.4 Secretos — 4 h

- [ ] `.env` fuera de git, `.gitignore` verificado — `30min · Baja · Crítica`
- [ ] Permisos `600` en el `.env` del VPS — `15min · Baja · Crítica`
- [ ] Escanear el histórico de git con `gitleaks` — `1h · Media · Crítica`
- [ ] Secretos distintos por entorno — `1h · Baja · Crítica`
- [ ] Procedimiento de rotación documentado — `1h · Baja · Alta`

### S.5 Cifrado y minimización — 7 h

- [ ] TLS 1.3 en todo el tráfico, sin HTTP disponible — `1h · Media · Crítica`
- [ ] Backups cifrados antes de salir del servidor — `2h · Media · Crítica`
- [ ] **Minimización campo a campo**: ¿necesitas el DNI del menor? ¿la dirección postal? — `3h · Media · Crítica`
- [ ] Seudonimización en informes agregados — `1h · Media · Media`

> Cada campo que no guardas es un campo que no puedes filtrar. Es la medida de seguridad más barata que existe.

### S.6 Auditoría y trazabilidad — 8 h

- [ ] Tabla `auditoria`: `usuario_id`, `club_id`, `accion`, `entidad`, `entidad_id`, `fecha`, `ip` — `2h · Media · Crítica`
- [ ] Registrar accesos a fichas, exportaciones, cambios de rol y borrados — `3h · Media · Crítica`
- [ ] **Logs sin datos personales**: identificadores, nunca nombres — `2h · Media · Crítica`
- [ ] Retención de logs definida con borrado automático — `1h · Media · Alta`

### S.6.b Registro de autenticación — 8 h

La IP es dato personal (TJUE, caso Breyer). Este log es a su vez un tratamiento que hay que justificar y borrar.

- [ ] Tabla de eventos de autenticación: `usuario_id`, `club_id`, `evento`, `resultado`, `fecha`, `ip`, `user_agent` — `2h · Media · Crítica`
- [ ] Eventos: login correcto, login fallido, logout, refresh, cambio de contraseña, alta/baja de MFA, bloqueo — `2h · Media · Alta`
- [ ] **Nunca la contraseña introducida**, ni hasheada, ni en un fallo — `0h · Baja · Crítica`
- [ ] En login fallido: guardar `usuario_id` si existe, si no solo un booleano — `1h · Media · Crítica`
- [ ] Base legal de interés legítimo (art. 6.1.f) documentada en el registro de actividades — `1h · Media · Crítica`
- [ ] Retención: 90 días para correctos, 12 meses para fallidos, con borrado automático — `2h · Media · Crítica`
- [ ] Policy de RLS específica: en el login todavía no hay `club_id` en contexto — `incluido arriba · Alta · Crítica`

> Nunca guardes el `username` intentado en un login fallido. La gente escribe su contraseña en el campo de usuario más de lo que parece, y acabas con contraseñas en claro en la tabla de auditoría.

> Si quieres geolocalizar la IP, usa una base de datos local tipo MaxMind. Una API externa te convierte al proveedor en subencargado y hay que meterlo en el contrato con cada club.

### S.7 Derechos de los interesados — 12 h

Plazo legal de respuesta: un mes.

- [ ] **Acceso**: exportar todos los datos de un atleta en formato legible — `3h · Media · Crítica`
- [ ] **Supresión**: borrado real, incluyendo el plazo hasta que expiran los backups — `4h · Alta · Crítica`
- [ ] **Portabilidad**: exportación en JSON o CSV — `2h · Media · Alta`
- [ ] **Oposición y limitación**: marcar el registro sin borrarlo — `2h · Media · Alta`
- [ ] Registro de solicitudes recibidas y su resolución — `1h · Baja · Alta`

> Supresión y histórico chocan. Si un tutor pide borrar los datos de su hijo, un `DELETE` rompe la asistencia de toda la temporada. La salida es seudonimizar: el atleta pasa a ser un identificador sin datos personales y las estadísticas agregadas siguen cuadrando.

### S.8 Gestión de brechas — 5 h

- [ ] Procedimiento escrito de detección, contención y evaluación — `2h · Media · Crítica`
- [ ] **Notificación a la AEPD en 72 h** desde que la conoces (art. 33) — `1h · Media · Crítica`
- [ ] Como encargado, avisar al club sin dilación indebida — `1h · Baja · Crítica`
- [ ] Plantilla de comunicación preparada de antemano — `1h · Baja · Alta`

> Con 72 horas de plazo no da tiempo a improvisar el procedimiento. Escríbelo cuando no haya prisa.

### S.9 Cadena de subencargados — 5 h

- [ ] Inventario: Hetzner, Cloudflare, email transaccional, monitorización, tiendas de apps — `1h · Baja · Crítica`
- [ ] Verificar que Hetzner está en la UE y firmar su acuerdo de encargo — `1h · Media · Crítica`
- [ ] Firmar el DPA de Cloudflare y revisar transferencias internacionales — `2h · Media · Crítica`
- [ ] Cláusula que permita al club oponerse a un subencargado nuevo — `1h · Media · Alta`

### S.10 Ciclo continuo — 6 h de montaje

- [ ] Dependabot o Renovate en ambos repositorios — `1h · Baja · Alta`
- [ ] OWASP Dependency-Check en el pipeline — `2h · Media · Alta`
- [ ] Revisión anual contra OWASP ASVS nivel 1 — `recurrente · Media · Media`
- [ ] Pentest antes de superar unos pocos clubes — `externo · Alta · Media`
- [ ] Revisión de permisos y roles cada seis meses — `recurrente · Baja · Alta`
- [ ] Revisar la EIPD cuando cambie el tratamiento (por ejemplo al lanzar el móvil) — `3h · Media · Crítica`

### Criterio de aceptación de la Fase S

- [ ] Un entrenador no accede a datos de otro grupo ni de otro club, verificado con test
- [ ] Un menor de 14 no se da de alta sin consentimiento de tutor
- [ ] Contrato de encargo firmado con el primer club
- [ ] EIPD hecha y documentada
- [ ] Se puede exportar y borrar todos los datos de un atleta
- [ ] Procedimiento escrito de brecha con el plazo de 72 h

---

## Fase 4 — App móvil

**Objetivo:** que el entrenador pase lista desde el borde de la piscina, con o sin cobertura.
**Depende de:** Fase 3

### 4.1 Base — 30 h

- [ ] Proyecto React Native con Expo — `4h · Media · Crítica`
- [ ] Login con selección de club si el usuario pertenece a varios — `6h · Media · Crítica`
- [ ] Almacenamiento seguro del refresh token (Keychain / Keystore, **no** AsyncStorage) — `4h · Alta · Crítica`
- [ ] Navegación: mis grupos → sesiones de hoy → pasar lista — `8h · Media · Crítica`
- [ ] Pantalla de pasar lista optimizada para una mano y pantalla al sol — `8h · Media · Alta`

### 4.2 Offline — 60 h

La parte difícil no es la interfaz.

- [ ] Base de datos local (`expo-sqlite` o WatermelonDB) — `12h · Alta · Crítica`
- [ ] Descarga previa de grupos y sesiones de los próximos días — `10h · Alta · Crítica`
- [ ] Cola de operaciones pendientes con reintentos — `16h · Alta · Crítica`
- [ ] Resolución de conflictos: gana la última escritura por `registrado_en` — `12h · Alta · Crítica`
- [ ] Indicador visible de estado de sincronización — `6h · Media · Alta`
- [ ] Tests del ciclo completo sin red — `4h · Alta · Crítica`

> Si tienes que recortar la Fase 4, recorta funcionalidad pero no el offline. Una app que falla al borde de la piscina no se usa, y la cobertura en instalaciones deportivas es mala casi siempre.

### 4.3 Publicación — 30 h

- [ ] Iconos, splash, permisos — `4h · Baja · Alta`
- [ ] Política de privacidad de la app, obligatoria en ambas tiendas — `4h · Media · Crítica`
- [ ] Declaración de datos recogidos (App Store Privacy, Data Safety de Google) — `4h · Media · Crítica`
- [ ] Build con EAS — `6h · Alta · Crítica`
- [ ] Publicación en App Store — `6h · Alta · Alta`
- [ ] Publicación en Google Play — `6h · Media · Alta`

> Cuenta con rechazos en la primera revisión de App Store. Es normal, no es un fallo tuyo.

---

## Transversal — Operación del producto

Antes del primer cliente que pague.

### Backups — 9 h

- [ ] `pg_dump` automático diario — `2h · Media · Crítica`
- [ ] Copia fuera del VPS — `2h · Media · Crítica`
- [ ] **Restaurar un dump de prueba antes del primer cliente** — `3h · Media · Crítica`
- [ ] Retención definida: 7 diarios, 4 semanales, 12 mensuales — `2h · Baja · Alta`

> Un backup que nunca has restaurado no es un backup. Un backup en el mismo disco que la base de datos tampoco.

### Restaurar un solo club — 4 h

El dolor real del shared schema. Un club borra su temporada y pide recuperarla; no puedes restaurar el dump completo sin machacar a los demás.

- [ ] Procedimiento escrito: dump en BD temporal, extraer las filas del `club_id`, reinsertar — `2h · Alta · Crítica`
- [ ] Probarlo una vez antes de necesitarlo — `2h · Alta · Alta`
- [ ] Borrado lógico (`deleted_at`) en entidades críticas, que evita la mayoría de estas restauraciones — `incluido en S.7 · Media · Alta`

### Monitorización — 5 h

- [ ] Healthcheck del backend — `1h · Baja · Alta`
- [ ] Alertas de caída (Uptime Kuma o servicio externo) — `2h · Media · Alta`
- [ ] Logs centralizados con `club_id` en el contexto — `1h · Media · Media`
- [ ] Monitorizar espacio en disco del VPS — `1h · Baja · Alta`

---

## Riesgos identificados

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Query sin filtro de tenant | Fuga entre clubes, afecta a todos a la vez | RLS como red de seguridad (0.6) |
| `TenantContext` no limpiado | El siguiente request hereda el tenant | Limpieza en `finally` (0.4) |
| Job de sesiones duplicando filas | Datos corruptos, asistencia inconsistente | Índice único e idempotencia (2.2) |
| Pérdida total de datos | Fin del negocio | Backups probados (transversal) |
| VPS único como punto de fallo | Caída total del servicio | Asumido; documentar el tiempo de recuperación |
| `.env.example` desincronizado del `.env` | Fallos en despliegue | Ya conocido: copiar manualmente cada variable nueva |
| Acceso a otro grupo del mismo club (IDOR) | Fuga de datos de menores | Autorización a nivel de objeto (S.3.3) |
| Alta de menor sin consentimiento de tutor | Incumplimiento LOPDGDD art. 7 | Bloqueo en el alta (S.1.a) |
| Brecha sin notificar en 72 h | Sanción de la AEPD | Procedimiento escrito previo (S.8) |
| Secreto filtrado en el histórico de git | Compromiso total | `gitleaks` y rotación (S.4.4) |
| Disco del VPS sin cifrar | Incumplimiento art. 32 | LUKS al montar el servidor (S.4.1) |
| Contraseña en la tabla de auditoría | Compromiso masivo | No guardar el `username` intentado (S.6.b) |
| Supresión rompiendo el histórico | Datos inconsistentes o derecho no atendido | Seudonimización (S.7) |

---

## Dependencias

```
Fase 0 (multi-tenancy)          33 h
   └── Fase S.1 (consentimiento + certificado)   16 h
          └── Fase 1 (temporadas y grupos)       38 h
                 └── Fase 2 (horarios y asistencia)   52 h
                        └── Fase 3 (API para móvil)   26 h
                               └── Fase 4 (app móvil)  120 h

Fase S (resto)          58 h + trabajo legal  ── antes del primer cliente
Transversal             18 h                  ── antes del primer cliente
```

**Siguiente paso inmediato:** tarea 0.1. Para arrancar hace falta la lista de entidades actuales del proyecto.

**Decisión pendiente:** si la aplicación almacena el PDF del certificado médico o solo sus metadatos (S.1.b). Recomendación: solo metadatos hasta que un club lo exija.
