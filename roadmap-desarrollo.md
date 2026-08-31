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
| Administrador de plataforma | `adminjaes`, rol propio `ROLE_PLATFORM_ADMIN` | Alguien tiene que dar de alta clubes y dar soporte; separarlo de `ROLE_ADMIN` mantiene el agujero en un único sitio, visible y auditable |

### Orden de las fases

Por delante de todo va la **Fase S.0**: cerrar los endpoints de autenticación que hoy permiten a cualquiera crearse un administrador. Son unas horas y no depende de nada, pero mientras siga abierto, todo el aislamiento entre clubes de la Fase 0 es decorativo.

Hecho eso, la división por clubes va **primera**, aunque el móvil sea lo último. Construir grupos y asistencia sin `club_id` obliga después a migrar cada tabla, cada query, cada endpoint y el JWT.

---

## Resumen de esfuerzo

| Fase | Contenido | Tiempo | Cuándo |
|---|---|---|---|
| S.0 | Agujeros de autenticación abiertos | ~4 h | **Lo primero de todo** |
| 0 | Multi-tenancy | ~40 h (5–6 días) | Bloquea todo lo demás |
| S.1 | Consentimiento y certificado médico | ~16 h (2 días) | Antes de la Fase 1 |
| 1 | Temporadas y grupos | ~38 h (5 días) | |
| 2 | Horarios y asistencia | ~52 h (6–7 días) | |
| 3 | Preparar API para móvil | ~26 h (3–4 días) | |
| S (resto) | Seguridad y cumplimiento | ~58 h + legal | Antes del primer cliente |
| Transversal | Backups y monitorización | ~14 h | Antes del primer cliente |
| 4 | App móvil | ~120 h (4 semanas) | |

**Total hasta producto vendible sin móvil:** unas 250 horas. A 15 horas semanales, en torno a 4 meses. Con la app móvil, 6 meses.

Cuenta un 30 % de margen por encima. Nunca he visto una estimación de software que sobrara.

---

## Fase S.0 — Agujeros de autenticación abiertos

**Va antes que todo lo demás, incluida la Fase 0.** No depende de nada, se resuelve en una tarde, y mientras siga ahí el resto del trabajo de aislamiento no sirve para nada: no hace falta saltarse la tenancy si te puedes crear un administrador.

**Bloquea:** nada técnicamente, pero cualquier despliegue fuera de local.

- [ ] Cerrar `POST /api/auth/signup/with-role` — `1h · Baja · Crítica`
- [ ] Validar en servidor qué roles puede asignar quien llama, sin fiarse del cuerpo — `1h · Media · Crítica`
- [ ] Eliminar `GET /api/auth/hash` — `15min · Baja · Alta`
- [ ] Repasar uno a uno los `permitAll` restantes de `SecurityConfig` — `1h · Media · Alta`
- [ ] Rotar el secreto JWT, la contraseña de Postgres y la del administrador **antes del primer despliegue fuera de local** — `1h · Baja · Crítica`

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

- [ ] Añadir columna `club_id` (nullable de momento) a `usuario` y `atleta` — `1h · Baja · Crítica`
- [ ] Backfill: asignar todas las filas existentes al club por defecto — `1h · Media · Crítica`
- [ ] Poner la columna `NOT NULL` **después** del backfill — `30min · Baja · Crítica`
- [ ] Foreign key hacia `club` — `30min · Baja · Alta`
- [ ] Índice sobre `club_id` en cada tabla — `30min · Baja · Alta`
- [ ] Migrar el índice único de `username` a `(club_id, username)` — `1h · Media · Crítica`

> Sin ese último cambio, el segundo club no puede tener un usuario llamado "admin".

> Las tablas hijas (`atleta_grupo`, `horario_grupo`, `asistencia`) no llevan `club_id`: siempre se llega a ellas por el padre.

### 0.3 club_id en el JWT — 3,5 h

- [ ] Añadir el claim `club_id` al generar el token — `1h · Baja · Crítica`
- [ ] Extraerlo y validarlo al parsear — `1h · Media · Crítica`
- [ ] Rechazar cualquier token sin el claim — `30min · Baja · Crítica`
- [ ] Actualizar el login para resolver el club del usuario — `1h · Media · Crítica`

### 0.4 TenantContext — 4,5 h

- [ ] Clase `TenantContext` con `ThreadLocal<UUID>` — `1h · Media · Crítica`
- [ ] Filtro o interceptor que lo rellena desde el claim en cada petición — `2h · Media · Crítica`
- [ ] **Limpiarlo en un `finally`** — `30min · Media · Crítica`
- [ ] Definir el comportamiento en endpoints públicos: contexto vacío, nunca club por defecto — `1h · Media · Alta`

> El `finally` cuesta cinco minutos y es el bug de multi-tenancy más difícil de reproducir que existe. Tomcat reutiliza los hilos del pool: sin limpieza, la siguiente petición hereda el club de la anterior de forma intermitente.

### 0.5 Filtro automático de Hibernate — 6 h

- [ ] `@FilterDef` con parámetro `clubId` — `1h · Media · Crítica`
- [ ] `@Filter` en cada entidad con `club_id` — `1h · Baja · Crítica`
- [ ] Activarlo por petición desde el `TenantContext` — `2h · Alta · Crítica`
- [ ] Auditar todas las queries nativas y `@Query` con SQL nativo — `2h · Alta · Crítica`

> Las queries nativas **no** pasan por el filtro. Son la vía de escape más habitual.

### 0.6 Row Level Security en PostgreSQL — 8 h

- [ ] `ENABLE ROW LEVEL SECURITY` en cada tabla con `club_id` — `1h · Media · Crítica`
- [ ] Policy por tabla usando `current_setting('app.club_id')` — `2h · Alta · Crítica`
- [ ] `SET LOCAL app.club_id` al inicio de cada transacción — `3h · Alta · Crítica`
- [ ] Usuario de aplicación **sin** `BYPASSRLS` — `1h · Media · Crítica`
- [ ] Usuario separado para migraciones, con permisos para saltarse las policies — `1h · Alta · Crítica`

> Lo más difícil es el `SET LOCAL`: hay que engancharlo al ciclo de vida de la transacción de Spring, no al de la petición. Cuenta con perder una tarde aquí.

### 0.7 Criterio de aceptación — 6 h

- [ ] Test de integración: usuario del club A no ve ni un registro del club B — `2h · Media · Crítica`
- [ ] Test negativo: query sin filtro **sigue** sin devolver datos ajenos gracias a RLS — `2h · Alta · Crítica`
- [ ] Test: token manipulado con otro `club_id` no da acceso — `1h · Media · Crítica`
- [ ] Regresión: login y funcionalidad actual siguen funcionando — `1h · Baja · Crítica`

---

### 0.8 Administrador de plataforma — 7 h

**Decisión tomada:** `adminjaes` es administrador universal, con acceso a todos los clubes. Es quien da de alta clubes y quien puede entrar a dar soporte sin que el club le cree una cuenta.

Esto es, por definición, **un agujero deliberado en el aislamiento** que las tareas 0.4 a 0.6 construyen. Que sea deliberado no lo hace menos peligroso: es la ruta que un atacante buscará primero, porque es la única que existe. De ahí que sea un rol propio y no un `ROLE_ADMIN` con superpoderes.

- [ ] `ROLE_PLATFORM_ADMIN` nuevo en `RoleName`, distinto de `ROLE_ADMIN` — `1h · Baja · Crítica`
- [ ] `users.club_id` sigue **NOT NULL** también para él: cuelga del club por defecto. Lo que le da acceso cruzado es el rol, nunca la ausencia de club — `1h · Alta · Crítica`
- [ ] `TenantContext` admite un estado "todos los clubes", que solo puede originarse en ese rol y jamás en un parámetro de petición — `1h · Alta · Crítica`
- [ ] Filtro de Hibernate desactivado para ese rol (aplica sobre 0.5) — `1h · Alta · Crítica`
- [ ] Policy RLS con la excepción vía `current_setting('app.platform_admin')` (aplica sobre 0.6) — `1h · Alta · Crítica`
- [ ] Todo acceso cruzado de este rol queda auditado: quién, qué club, cuándo — `1h · Media · Crítica`
- [ ] Test: un `ROLE_ADMIN` de club **no** obtiene acceso cruzado; solo `ROLE_PLATFORM_ADMIN` — `1h · Media · Crítica`

> El primer punto es el que decide todo lo demás, y hay que resolverlo **durante la 0.2**, no después: si `club_id` se deja nullable "para el admin", el NOT NULL deja de ser una garantía en toda la tabla y cualquier fila mal insertada se cuela sin club.

> MFA para este rol no es opcional. Está en S.3.1 como MFA para roles de administración; con un admin universal, esa tarea deja de poder esperar a la Fase S.

---

## Fase S.1 — Consentimiento y certificado médico

**Va antes de la Fase 1** porque son tablas relacionadas con `Atleta`. Añadirlas después es una migración con datos ya cargados.

### S.1.a Consentimiento de menores — 11 h

En España la edad de consentimiento son **14 años** (LOPDGDD art. 7), no 16. La mayoría de tus atletas estarán por debajo.

- [ ] Entidad `Tutor`: `id`, `club_id`, `nombre`, `dni`, `email`, `telefono`, `parentesco` — `1h · Baja · Crítica`
- [ ] Relación `Atleta` ↔ `Tutor`, varios tutores por atleta — `1h · Media · Crítica`
- [ ] Entidad `Consentimiento`: `id`, `atleta_id`, `tutor_id`, `tipo`, `otorgado`, `fecha`, `evidencia`, `ip_origen`, `fecha_revocacion` — `2h · Media · Crítica`
- [ ] Tipos separados y granulares: `TRATAMIENTO_DATOS`, `IMAGEN`, `COMUNICACIONES`, `DATOS_SALUD` — `1h · Media · Crítica`
- [ ] Consulta que determina si el atleta era menor de 14 en la fecha del consentimiento — `1h · Media · Alta`
- [ ] Bloquear el alta de menor de 14 sin consentimiento de tutor registrado — `2h · Media · Crítica`
- [ ] Revocación por `fecha_revocacion`, nunca borrando la fila — `1h · Baja · Crítica`
- [ ] Almacenar la evidencia del consentimiento con sello de tiempo — `2h · Media · Alta`

> El consentimiento de imagen va **separado** del general y tiene que poder revocarse solo. Una casilla única para todo no es válida.

### S.1.b Certificado médico anual (FMN) — 5 h

Único dato de salud del sistema. **No se almacena el veredicto**: nadie presenta un certificado de "no apto", así que la existencia de un certificado en plazo *es* la aptitud. Un campo `apto` sería un juicio clínico; una fecha de caducidad no lo es.

- [ ] Entidad `CertificadoMedico`: `id`, `atleta_id`, `temporada_id`, `fecha_emision`, `fecha_caducidad`, `estado`, `validado_por`, `validado_en` — `1h · Baja · Crítica`
- [ ] **Sin diagnóstico, sin observaciones médicas, sin campo `apto`** — `0h · Baja · Crítica`
- [ ] Estado calculado desde `fecha_caducidad`, no almacenado a mano — `1h · Baja · Alta`
- [ ] Aviso al alta en grupo si no hay certificado vigente — `1h · Media · Alta`
- [ ] Aviso automático al tutor 30 días antes del vencimiento — `2h · Media · Alta`

**Opción B — almacenar el PDF.** Solo si un club lo exige. Suma unas 8 h.

- [ ] Fichero en almacenamiento privado, **nunca en la BD ni en carpeta servida estáticamente** — `2h · Media · Crítica`
- [ ] Descarga solo por endpoint controlado, sin URL directa ni predecible — `2h · Alta · Crítica`
- [ ] Nombre de fichero aleatorio (UUID), sin el nombre del atleta — `30min · Baja · Alta`
- [ ] Cifrado en reposo — `1h · Media · Crítica`
- [ ] Cada descarga registrada en auditoría con usuario, fecha e IP — `1h · Media · Crítica`
- [ ] Solo `ADMIN_CLUB` abre el documento; `ENTRENADOR` ve solo el estado — `1h · Media · Crítica`
- [ ] Borrado automático al ser sustituido por el del año siguiente — `1h · Media · Alta`

---

## Fase 1 — Temporadas y grupos

**Depende de:** Fase 0 y S.1

### 1.1 Temporada — 5 h

- [ ] Entidad `Temporada`: `id`, `club_id`, `nombre`, `fecha_inicio`, `fecha_fin`, `activa` — `1h · Baja · Crítica`
- [ ] Regla: solo una temporada activa por club — `1h · Media · Alta`
- [ ] CRUD y endpoint para marcar la temporada activa — `2h · Baja · Alta`
- [ ] Crear la temporada actual en el seed — `1h · Baja · Media`

### 1.2 Grupo — 8 h

- [ ] Entidad `Grupo`: `id`, `club_id`, `temporada_id`, `entrenador_id`, `nombre`, `categoria`, `nivel`, `plazas_max` — `1h · Baja · Crítica`
- [ ] Relación con `Usuario` para el entrenador — `1h · Baja · Alta`
- [ ] CRUD completo — `3h · Baja · Crítica`
- [ ] Duplicar los grupos de una temporada a la siguiente — `3h · Media · Media`

> Duplicar temporada parece secundario hasta el septiembre en que el club tiene que recrear catorce grupos a mano.

### 1.3 Pertenencia histórica — 10 h

- [ ] Entidad `AtletaGrupo`: `id`, `atleta_id`, `grupo_id`, `fecha_alta`, `fecha_baja`, `motivo_baja` — `1h · Baja · Crítica`
- [ ] Regla: no dos pertenencias abiertas al mismo grupo — `1h · Media · Alta`
- [ ] Permitir varios grupos simultáneos (natación + preparación física) — `1h · Media · Media`
- [ ] Endpoint de asignación: cierra la anterior si aplica y abre la nueva — `2h · Media · Crítica`
- [ ] Endpoint de baja: rellena `fecha_baja`, nunca borra — `1h · Baja · Crítica`
- [ ] Consulta de miembros a una fecha dada — `2h · Alta · Crítica`
- [ ] Consulta de histórico de un atleta por temporada — `2h · Media · Alta`

> La consulta "miembros a una fecha dada" es la pieza sobre la que se apoya toda la Fase 2. Merece la pena hacerla bien y con tests.

### 1.4 API — 6 h

- [ ] `GET /grupos?temporadaId=` con conteo de atletas — `1h · Baja · Alta`
- [ ] `GET /grupos/{id}/atletas` — `1h · Baja · Crítica`
- [ ] `POST /grupos/{id}/atletas`, individual y en lote — `2h · Media · Crítica`
- [ ] `DELETE /grupos/{id}/atletas/{atletaId}` (baja lógica) — `1h · Baja · Alta`
- [ ] `GET /atletas/{id}/historial-grupos` — `1h · Baja · Media`

### 1.5 Frontend — 9 h

- [ ] Selector de temporada persistente en la cabecera — `2h · Media · Alta`
- [ ] Listado y detalle de grupos — `3h · Baja · Crítica`
- [ ] Pantalla de asignación con selección múltiple — `3h · Media · Crítica`
- [ ] Vista de histórico en la ficha del atleta — `1h · Baja · Media`

---

## Fase 2 — Horarios y asistencia

**Depende de:** Fase 1

### 2.1 Horario recurrente — 5 h

- [ ] Entidad `HorarioGrupo`: `grupo_id`, `dia_semana`, `hora_inicio`, `hora_fin`, `ubicacion`, `vigente_desde`, `vigente_hasta` — `1h · Baja · Crítica`
- [ ] Varios horarios por grupo — `1h · Baja · Crítica`
- [ ] CRUD dentro del detalle del grupo — `3h · Baja · Alta`

### 2.2 Generación de sesiones — 16 h

La parte con más trampas del proyecto.

- [ ] Entidad `Sesion`: `grupo_id`, `horario_id`, `fecha`, `hora_inicio`, `hora_fin`, `ubicacion`, `estado`, `motivo_cancelacion` — `1h · Baja · Crítica`
- [ ] Servicio que materializa sesiones desde los horarios para un rango — `4h · Alta · Crítica`
- [ ] Job `@Scheduled` que mantiene generadas las próximas 4–6 semanas — `2h · Media · Crítica`
- [ ] **Idempotencia**: índice único `(horario_id, fecha)` y lógica que no duplica — `2h · Alta · Crítica`
- [ ] Calendario de excepciones: festivos y cierres de piscina — `3h · Media · Alta`
- [ ] Cancelar una sesión concreta con motivo — `1h · Baja · Alta`
- [ ] Crear sesión puntual fuera de horario (competición, extra) — `1h · Baja · Media`
- [ ] Regeneración al cambiar un horario: solo sesiones futuras, jamás las pasadas — `2h · Alta · Crítica`

> Si el job duplica sesiones, la asistencia queda inconsistente y el club pierde la confianza en el sistema entero. La idempotencia no es opcional.

### 2.3 Asistencia — 12 h

- [ ] Entidad `Asistencia`: `sesion_id`, `atleta_id`, `estado`, `observaciones`, `registrado_por`, `registrado_en` — `1h · Baja · Crítica`
- [ ] **Índice único `(sesion_id, atleta_id)`** — `30min · Baja · Crítica`
- [ ] `GET /sesiones/{id}/lista`: atletas del grupo con su estado, en una sola llamada — `3h · Media · Crítica`
- [ ] `PUT /sesiones/{id}/asistencia`: guardado en lote — `2h · Media · Crítica`
- [ ] La lista son los atletas con pertenencia activa **en la fecha de la sesión** — `3h · Alta · Crítica`
- [ ] Marcar la sesión como `REALIZADA` al guardar — `1h · Baja · Alta`
- [ ] Tests de concurrencia: dos entrenadores pasando lista a la vez — `2h · Alta · Alta`

> El endpoint `/lista` es el que consumirá el móvil. Diseñarlo ahora pensando en una sola llamada te ahorra rehacerlo en la Fase 3.

### 2.4 Informes — 9 h

- [ ] Porcentaje de asistencia por atleta en un rango — `2h · Media · Alta`
- [ ] Porcentaje de asistencia por grupo — `2h · Media · Alta`
- [ ] Detección de ausencias consecutivas — `2h · Media · Media`
- [ ] Exportación a CSV — `3h · Media · Media`

### 2.5 Frontend — 10 h

- [ ] Calendario de sesiones del grupo — `4h · Media · Alta`
- [ ] Pantalla de pasar lista con guardado en lote — `4h · Media · Crítica`
- [ ] Panel de informes con filtros — `2h · Media · Media`

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

- [ ] Roles definidos: `SUPERADMIN`, `ADMIN_CLUB`, `ENTRENADOR`, `TUTOR` — `2h · Media · Crítica`
- [ ] **Autorización a nivel de objeto, no solo de tenant** — `4h · Alta · Crítica`
- [ ] Test específico de IDOR: pedir un atleta de otro grupo del mismo club — `2h · Alta · Crítica`
- [ ] Denegar por defecto en cada endpoint — `1h · Media · Crítica`
- [ ] Los tutores solo ven a sus propios hijos — `1h · Media · Alta`

> El `club_id` no te protege del IDOR interno. Un entrenador del club A pidiendo la ficha de un atleta de otro grupo del club A pasa el filtro de tenant sin problema. Con datos de menores, es el fallo que peor sienta en una auditoría.

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
