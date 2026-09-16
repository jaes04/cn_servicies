# Contratos de la API para el frontend

**Todas las rutas de la API: las 104.** Cuerpos, respuestas, permisos y las reglas que no se
ven en la forma de la petición. Incluye también lo que cambia en pantallas que ya existen (§18).

Sacado del código real (controladores, DTOs, servicios y `SecurityConfig`) a fecha de
**16 de septiembre de 2026**, rama `develop`. **`API_DOCS.md`, en la raíz, es de mayo y está
obsoleto: no lo uses.** En varias cosas dice lo contrario de lo que hace hoy la API.

> **El login ya trae los cambios avisados** (§2): el refresco solo acepta refresh tokens y
> hay límite de intentos. **Los tokens emitidos antes dejan de valer**: en el primer despliegue
> todo el mundo vuelve a entrar con su contraseña. Ver §18.

---

## 1. Convenciones

**Base:** todas las rutas cuelgan de `/api`. El frontend la toma de `import.meta.env.VITE_API_URL`.

**Autenticación:** cabecera `Authorization: Bearer <accessToken>` en todas las rutas salvo
login, refresh, alta pública y blog publicado.

**Formatos en JSON:**

| Tipo | Ejemplo |
|---|---|
| Identificador | `"3f2a9c1e-…"` (UUID) |
| Fecha | `"2026-09-15"` |
| Hora | `"18:00:00"` |
| Fecha y hora | `"2026-09-15T10:30:00"` |
| Día de la semana | `"TUESDAY"` |
| Enumerados | siempre en texto: `"PRESENT"`, `"SWIMMING"`… Ver §3 |

Las fechas en parámetros de consulta van igual: `?from=2026-09-01&to=2026-09-30`.

**Errores.** Todos llevan `status`, `message`, `path` y `timestamp`. El `message` está en
español y se puede enseñar tal cual.

```json
{ "timestamp": "2026-09-15T10:30:00", "status": 400,
  "message": "Ya existe un grupo con ese nombre en esa temporada", "path": "/api/groups" }
```

Un error de validación de campos lleva además `errors`, con el mensaje de cada campo:

```json
{ "status": 400, "message": "Errores de validación en los campos enviados",
  "errors": { "seasonId": "<mensaje de validación del campo>" }, "path": "…", "timestamp": "…" }
```

Las claves de `errors` son los nombres de los campos del cuerpo. Los mensajes de los campos
que no tienen uno propio los genera el validador en el idioma del servidor: úsalos para
marcar el campo, no como texto definitivo.

**Qué significa cada código:**

| Código | Significa | Qué hacer en la interfaz |
|---|---|---|
| 400 | Datos no válidos o regla de negocio | Enseñar `message` |
| 401 | Sin token, o token caducado o inválido | Refrescar o volver al login |
| 403 | **Tu rol** no puede usar esa ruta, **la cuenta está bloqueada, o la ruta no existe** | No enseñar la acción a ese rol; en el login, enseñar `message` |
| 404 | **No existe, o no es tuyo** | Tratar como "no encontrado" |
| 429 | Demasiados intentos fallidos de login | Enseñar `message` y esperar `retryAfterSeconds` |

> **Una ruta mal escrita da 403, no 404** (y 401 si no llevas token). Desde que el servidor
> deniega por defecto, lo que no tiene regla no lo puede nadie, y una ruta que no existe no tiene
> regla. **Si te sale un 403 en algo que crees que existe, revisa la ruta antes que los permisos.**
> Solo dentro de una rama con regla general —por ejemplo `/api/medical-certificates/...` siendo
> administrador— una ruta inexistente sigue dando 404.

**Cabeceras de seguridad.** Todas las respuestas llevan `Content-Security-Policy: default-src
'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy:
no-referrer` y, por HTTPS, HSTS. **No afectan a las llamadas desde el frontend**: la CSP solo
manda sobre el documento que la recibe. La CSP de la propia aplicación React es otra y va en
Cloudflare Pages, en tu repositorio (archivo `_headers`).

**403 y 404 no son intercambiables.** Un 403 depende solo del rol, así que la interfaz puede
evitarlo escondiendo botones. Un 404 aparece también cuando el recurso existe pero
pertenece a otro club, a un grupo que el entrenador no lleva o a un atleta sin vínculo: el
sistema no confirma que exista. **No hay forma de distinguir los dos casos, y es a
propósito.**

**Paginación:** atletas, resultados, noticias y usuarios. Parámetros
`?page=0&size=20&sort=lastName`; la respuesta es la página estándar de Spring
(`content`, `totalElements`, `totalPages`, `number`, `size`…). La página empieza en **0**.

**El orden por defecto es ascendente**: las noticias salen de la más antigua a la más nueva. Para
lo contrario, pídelo: `sort=publishedAt,desc`.

---

## 2. Sesión

### `POST /api/auth/login` — público

```json
{ "username": "adminjaes", "password": "…" }
```

**200:**

```json
{ "accessToken": "eyJ…", "refreshToken": "eyJ…", "tokenType": "Bearer" }
```

**401:** `"Usuario o contraseña incorrectos"`. Es el mismo mensaje exista el usuario o no:
la pantalla no puede distinguir "ese usuario no existe" de "esa contraseña no es".

**403:** la cuenta existe pero el club la ha bloqueado, y ahí la contraseña da igual.
`"Esta cuenta está bloqueada. Ponte en contacto con el club"`. Enseña el mensaje: no ofrezcas
recuperar la contraseña, porque no arregla nada. **Antes esto daba un 500.**

**429:** demasiados intentos fallidos. Hay dos límites y cualquiera de los dos salta, con
mensajes distintos para que se puedan separar en la pantalla:

```json
{ "status": 429, "retryAfterSeconds": 300,
  "message": "Demasiados intentos fallidos con esta cuenta. Vuelve a intentarlo en 5 minutos.",
  "path": "/api/auth/login", "timestamp": "…" }
```

- **"con esta cuenta"**: 5 fallos seguidos con ese usuario.
- **"desde esta conexión"**: 30 fallos desde la misma IP, aunque sean de usuarios distintos.
  Sale cuando alguien prueba una contraseña en muchas cuentas.
- Llega también la cabecera estándar `Retry-After`, en segundos. Usa
  `retryAfterSeconds` para la cuenta atrás y **deshabilita el botón mientras corre**: seguir
  probando no comprueba nada.
- **Mientras dura, ni la contraseña correcta entra.** Un acierto anterior sí borra el
  contador, así que un despiste suelto no deja rastro.
- La espera **se dobla** cada vez que se vuelven a agotar los intentos: 5, 10, 20… hasta 60
  minutos. Merece la pena decirlo en el texto de ayuda de la pantalla.
- Una cuenta bloqueada por el club (403) **no gasta intentos**: por mucho que se insista,
  sigue contestando lo mismo.

El token lleva `sub` (el username), `club_id`, `roles` y `typ`, por ejemplo
`["ROLE_TECHNICAL_STAFF"]`. **Los roles del token son lo que la interfaz puede usar para
decidir qué enseñar.** La autorización real la hace siempre el servidor.

### `POST /api/auth/refresh` — público

```json
{ "refreshToken": "eyJ…" }
```

Devuelve lo mismo que el login.

**Solo acepta el refresh token.** Cada token dice para qué sirve en el claim `typ`
(`ACCESS` o `REFRESH`) y se comprueba en cada uso:

- Mandar aquí el **access token** es **401** `"Esta ruta solo acepta el refresh token, y has
  enviado el de acceso"`. El mensaje lo dice así de claro a propósito: quien pregunta ya tiene
  el token en la mano, no se filtra nada, y ahorra una tarde de depuración.
- Un token inválido o caducado es **401** `"El refresh token no es válido o ha caducado.
  Vuelve a iniciar sesión"`. Antes esto era un 400.
- **El refresh token ya no autentica peticiones normales.** Enviarlo en `Authorization` da
  401 en cualquier ruta. Si el frontend guarda los dos por separado y manda siempre el
  `accessToken`, no hay nada que tocar.
- **Los tokens emitidos antes de este cambio no valen para nada**, porque no llevan `typ`.
  Al desplegar, la sesión guardada en el navegador caduca y toca volver a entrar.

### `GET /api/users/me` — cualquier autenticado

El usuario actual: `id`, `username`, `email`, `roles`, `profilePhoto`, `blocked`, `createdAt`.

### `PUT /api/users/me/password` — cualquier autenticado

```json
{ "currentPassword": "…", "newPassword": "…" }
```

**204 sin cuerpo.** Errores, todos **400** con su `message`:

| Cuándo | `message` |
|---|---|
| La actual no es la que es | `"La contraseña actual no es correcta"` |
| La nueva es igual que la actual | `"La contraseña nueva tiene que ser distinta de la actual"` |
| La nueva no cumple la política | El motivo concreto, ver §2.b |

**Pide la actual aunque la petición vaya autenticada**, y es a propósito: si bastara el
token, una sesión olvidada en un ordenador compartido serviría para quedarse con la cuenta.

> ⚠️ **Cambiar la contraseña no cierra las sesiones abiertas.** Los tokens emitidos antes
> siguen valiendo hasta que caducan, porque no hay lista de revocación. Para echar a alguien
> de verdad hay que **bloquear la cuenta**, que sí tiene efecto inmediato.

### 2.b Qué contraseñas se aceptan

Vale para **todas** las vías: alta de usuario, alta pública, la propia y la del administrador.
Siempre llegan como **400** con el motivo en `message`, nunca en `errors`, porque la regla vive
en un sitio solo.

| Motivo | `message` |
|---|---|
| Menos de 12 caracteres | `"La contraseña debe tener al menos 12 caracteres"` |
| Más de 72 | `"La contraseña no puede pasar de 72 caracteres"` |
| Está en listas públicas | `"Esa contraseña es de las más usadas y está en listas públicas. Elige otra"` |
| Lleva dentro el usuario o el correo | `"La contraseña no puede llevar dentro tu usuario ni tu correo"` |
| Lleva dentro el nombre del club | `"La contraseña no puede llevar dentro el nombre del club"` |
| Es la misma cosa repetida | `"La contraseña no puede ser la misma cosa repetida. Alárgala con algo distinto"` |

**No se exige mayúscula, ni número, ni símbolo, y no lo hagas en la interfaz.** Esas reglas
producen `Password1!`, que es corta, está en todas las listas y encima hay que apuntarla. Lo
que se pide es longitud y que no sea conocida.

Enseña el mínimo de 12 **antes** de que el usuario envíe el formulario, y el resto de motivos
tal como lleguen: cada uno dice exactamente qué corregir. Las comparaciones se hacen en
minúsculas, sin acentos y sin signos, así que `Contraseña-123` cuenta como `contrasena123`.

> **El mínimo sube de 8 a 12.** Las cuentas que ya existen no cambian —su contraseña sigue
> valiendo para entrar— pero en cuanto alguien la cambie tendrá que cumplir lo nuevo.

### `PUT /api/users/{id}/password` — admin

```json
{ "password": "…" }
```

**204 sin cuerpo.** La salida a una contraseña olvidada mientras no haya recuperación por
correo: no pide la anterior, porque existe justo para cuando nadie la sabe. **404** si esa
cuenta no existe o es de otro club; **400** si la contraseña es corta.

**La acaba sabiendo el administrador**, así que la pantalla debería decirle al usuario que la
cambie él con la ruta de arriba.

### Bloquear una cuenta — `PATCH /api/users/{id}/block`, admin

Ya existía, pero **ahora corta la sesión en el acto**: el token de una cuenta bloqueada deja
de autenticar (**401** en cualquier ruta) y tampoco puede renovarse (**403** en `/refresh`).
Antes seguía trabajando hasta que su token caducaba, hasta un día después.

**Desbloquear:** `PATCH /api/users/{id}/unblock`, admin. Los dos devuelven la cuenta, sin cuerpo
en la petición. Desbloquear no le devuelve la sesión que tenía: vuelve a entrar con su
contraseña.

---

## 3. Catálogo de enumerados

| Enumerado | Valores |
|---|---|
| Roles | `ROLE_ADMIN`, `ROLE_TECHNICAL_STAFF`, `ROLE_EDITOR`, `ROLE_USER` |
| `Gender` | `MALE`, `FEMALE` |
| `GroupCategory` | `PREBENJAMIN`, `BENJAMIN`, `ALEVIN`, `INFANTIL`, `JUNIOR`, `ABSOLUTO`, `MASTER` |
| `GroupLevel` | `INICIACION`, `PERFECCIONAMIENTO`, `COMPETICION` |
| `LeaveReason` (baja de un grupo) | `END_OF_SEASON`, `GROUP_CHANGE`, `LEFT_CLUB`, `OTHER` |
| `TrainingModality` | `SWIMMING` (agua), `DRYLAND` (seco) |
| `SessionStatus` | `SCHEDULED`, `DONE`, `CANCELLED` |
| `CancellationReason` | `HOLIDAY`, `POOL_CLOSURE`, `WEATHER`, `COACH_UNAVAILABLE`, `COMPETITION`, `OTHER` |
| `AttendanceStatus` | `PRESENT`, `ABSENT`, `EXCUSED`, `LATE` |
| `ConsentType` | `DATA_PROCESSING`, `IMAGE`, `COMMUNICATIONS`, `HEALTH_DATA` |
| `ConsentEvidenceType` | `PAPER_FORM`, `ONLINE_FORM`, `EMAIL` |
| `GuardianRelationship` | `MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` |
| `MedicalCertificateStatus` | `VALID`, `EXPIRING_SOON`, `EXPIRED`, `MISSING` |
| `DocumentDeliveryType` | `LICENSE_APPLICATION`, `IDENTITY_DOCUMENT`, `TRAVEL_PERMIT` |
| `DocumentDeliveryStatus` | `VALID`, `EXPIRING_SOON`, `EXPIRED`, `MISSING`, `NOT_REQUIRED` |
| `UserAthleteType` | `TUTOR`, `ATHLETE` |
| `Stroke` (estilo) | `FREESTYLE`, `BACKSTROKE`, `BREASTSTROKE`, `BUTTERFLY`, `MEDLEY` |
| `PostStatus` | `DRAFT`, `PUBLISHED`, `DELETED` |

**No hay campos de texto libre** en asistencia, bajas, cancelaciones ni entregas. Es a
propósito: no añadas un campo de notas en la interfaz, porque no hay dónde guardarlo.

---

## 4. Quién ve qué

| Área | Administrador | Entrenador | Socio o tutor |
|---|---|---|---|
| Temporadas | Todo | Consultar | — |
| Grupos, horarios y composición | Todo | **Consultar solo los suyos** | — |
| Sesiones y cierres | Todo | Consultar, cancelar y reactivar **en sus grupos**; consultar cierres | — |
| Pasar lista | Todo | **Solo en sus grupos** | — |
| Informes | Todo | **Solo sus grupos y sus atletas** | — |
| Fichas de atleta | Todo | **Atletas que hoy están en sus grupos**; dar de alta | — |
| Consentimientos | Todo | Estado, **de sus atletas** | — |
| Certificados médicos | Todo | Estado, **de sus atletas** | — |
| Entregas de papeles | Todo | Estado y permiso de viaje, **de sus atletas** | — |
| Claves de invitación | Todo | **Para sus atletas** | Canjear |

**"Sus grupos"** son aquellos en los que el usuario figura como entrenador principal o como
ayudante. **"Sus atletas"** son los que **hoy** están en alguno de esos grupos: si un nadador
dejó el grupo ayer, su ficha ya no está disponible para ese entrenador, pero sigue en las
listas de las sesiones pasadas.

Todo lo que queda fuera del alcance de un entrenador responde con **404**, y los listados le
llegan ya filtrados. **La interfaz no tiene que filtrar nada por su cuenta.**

---

## 5. Temporadas

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/seasons` | Admin, entrenador | Lista de temporadas |
| `GET /api/seasons/active` | Admin, entrenador | La activa. **404** `"No hay ninguna temporada activa"` si no hay |
| `GET /api/seasons/{id}` | Admin, entrenador | Una |
| `POST /api/seasons` | Admin | **201**. Nace **inactiva** |
| `PUT /api/seasons/{id}` | Admin | 200 |
| `POST /api/seasons/{id}/activation` | Admin | 200. Desactiva la anterior en la misma operación |

**Petición** (alta y edición):

```json
{ "name": "2026/2027", "startDate": "2026-09-01", "endDate": "2027-08-31" }
```

**Respuesta:**

```json
{ "id": "…", "name": "2026/2027", "startDate": "2026-09-01", "endDate": "2027-08-31", "active": true }
```

**Reglas:**

- Dos temporadas del mismo club no pueden solaparse, ni siquiera en un día: **400**.
- El nombre es único dentro del club: **400** `"Ya existe una temporada con ese nombre"`.
- No se pueden borrar.
- Activar no es un campo del `PUT`: es su propia ruta.

**Para el selector de temporada de la cabecera:** llamar a `/active` al entrar y guardar la
elegida. Casi todas las pantallas de grupos necesitan un `seasonId`.

---

## 6. Grupos

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/groups?seasonId=` | Admin; entrenador **solo los suyos** | Lista. Sin `seasonId`, todas las temporadas |
| `GET /api/groups/{id}` | Admin; entrenador si es suyo | Uno |
| `POST /api/groups` | Admin | 201 |
| `PUT /api/groups/{id}` | Admin | 200 |
| `DELETE /api/groups/{id}` | Admin | 204. Borrado lógico |
| `POST /api/groups/duplication` | Admin | 201. Copia los grupos de una temporada a otra |

**Petición:**

```json
{
  "seasonId": "…", "name": "Alevín A", "category": "ALEVIN", "level": "COMPETICION",
  "maxSlots": 12,
  "coachId": "…",
  "assistantCoachIds": ["…", "…"]
}
```

- `maxSlots`, `coachId` y `assistantCoachIds` son opcionales.
- **`assistantCoachIds` sustituye** a los ayudantes que hubiera: si no se envía, el grupo se
  queda sin ayudantes. En el formulario de edición hay que mandar siempre la lista completa.

**Respuesta:**

```json
{
  "id": "…", "seasonId": "…", "seasonName": "2026/2027",
  "name": "Alevín A", "category": "ALEVIN", "level": "COMPETICION",
  "maxSlots": 12, "memberCount": 11,
  "coachId": "…", "coachUsername": "entrenador1",
  "assistantCoaches": [ { "id": "…", "username": "ayudante1" } ]
}
```

`coachId` y `coachUsername` son `null` si no hay entrenador principal. `assistantCoaches`
nunca es `null`: si no hay ayudantes, llega vacío.

**Reglas:**

- El nombre es único dentro de la temporada: **400**.
- **El entrenador principal y los ayudantes tienen que tener rol técnico o de
  administrador**: si no, **400** `"Solo se puede asignar como entrenador a personal técnico del club"`.
- El principal no puede figurar también como ayudante: **400**.
- **`maxSlots` no bloquea altas.** Con `memberCount` la interfaz puede avisar de que el grupo
  está lleno, pero el alta pasa igual.

**Duplicar una temporada:**

```json
{ "fromSeasonId": "…", "toSeasonId": "…" }
```

Copia nombre, categoría, nivel, plazas, entrenador principal y ayudantes. **No copia los
atletas.** Si la temporada destino ya tiene grupos, devuelve **400**: es la protección contra
el doble clic.

---

## 7. Composición del grupo

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/groups/{groupId}/athletes?date=` | Admin; entrenador si es suyo | Miembros. Sin `date`, los de hoy |
| `POST /api/groups/{groupId}/athletes` | Admin | 201, lista de altas |
| `DELETE /api/groups/{groupId}/athletes/{athleteId}?reason=&leftOn=` | Admin | 200. Baja lógica |
| `GET /api/athletes/{athleteId}/group-history?seasonId=` | Admin; entrenador si es su atleta | Historial |

**Alta, individual o en lote:**

```json
{ "athleteIds": ["…", "…"], "joinedOn": "2026-09-15", "replacesGroupId": "…" }
```

- **Entera o nada:** si uno de la lista ya está en el grupo, no entra ninguno (**400**).
- `joinedOn` tiene que caer dentro de la temporada del grupo.
- Un atleta **puede estar en varios grupos a la vez**, por ejemplo agua y seco.
- **`replacesGroupId` es la forma de mover a alguien de grupo**: le da de baja en ese otro
  grupo el mismo día, con motivo `GROUP_CHANGE`. Sin él, el alta no toca los demás grupos.

**Baja:** `reason` es obligatorio (`LeaveReason`). `leftOn` es opcional y por defecto es
hoy. **`leftOn` es el último día que el atleta pertenece al grupo, incluido.**

**Respuesta** (en todas las rutas de esta sección):

```json
{
  "id": "…", "athleteId": "…", "athleteName": "Ana Pérez",
  "groupId": "…", "groupName": "Alevín A",
  "joinedOn": "2026-09-15", "leftOn": null, "leaveReason": null, "open": true
}
```

---

## 8. Horarios

Todas las rutas cuelgan del grupo.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/groups/{groupId}/schedules?date=` | Admin; entrenador si es suyo | Sin `date`, todos los vigentes; con `date`, los que lo estaban ese día |
| `POST /api/groups/{groupId}/schedules` | Admin | 201 |
| `PUT /api/groups/{groupId}/schedules/{scheduleId}` | Admin | 200. **Rehace las sesiones futuras** |
| `DELETE /api/groups/{groupId}/schedules/{scheduleId}` | Admin | 204. **Elimina las sesiones futuras** |

**Petición:**

```json
{
  "dayOfWeek": "TUESDAY", "startTime": "18:00:00", "endTime": "19:00:00",
  "modality": "SWIMMING", "validFrom": "2026-09-15", "validUntil": null
}
```

**Respuesta:** lo mismo, más `id`, `groupId`, `groupName` e `inForce` (si está vigente hoy).

**Reglas:**

- Dos horarios del mismo grupo no pueden pisarse el mismo día: **400** con "solapa". **Sí se
  pueden encadenar**, por ejemplo 17:00–18:00 y 18:00–19:00.
- La vigencia tiene que caer dentro de la temporada.
- **Avisar en la interfaz antes de editar o borrar un horario:** afecta a todas las sesiones
  futuras que salían de él, incluidas las canceladas. Las ya celebradas no se tocan.

---

## 9. Sesiones

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/groups/{groupId}/sessions?from=&to=` | Admin; entrenador si es suyo | Calendario. **Las dos fechas son obligatorias** |
| `POST /api/groups/{groupId}/sessions/generation?from=&to=` | Admin | Genera sesiones desde los horarios |
| `POST /api/groups/{groupId}/sessions` | Admin | 201. Sesión suelta, fuera de horario |
| `GET /api/sessions/{id}` | Admin; entrenador si es de su grupo | Una |
| `POST /api/sessions/{id}/cancellation?reason=` | Admin; entrenador en su grupo | 200 |
| `POST /api/sessions/{id}/reactivation` | Admin; entrenador en su grupo | 200 |

**Respuesta de sesión:**

```json
{
  "id": "…", "groupId": "…", "groupName": "Alevín A",
  "date": "2026-09-15", "startTime": "18:00:00", "endTime": "19:00:00",
  "modality": "SWIMMING", "status": "SCHEDULED", "cancellationReason": null, "oneOff": false
}
```

`oneOff` es `true` en las sesiones sueltas.

**Generación.** Se puede lanzar tantas veces como haga falta, porque no duplica nada:

```json
{ "from": "2026-09-15", "to": "2026-10-26", "created": 12, "alreadyExisted": 0, "bornCancelled": 1 }
```

- `bornCancelled` cuenta las sesiones que caían en un cierre (§11) y se crearon ya canceladas.
- **Tope de 400 días** por petición.
- **No hace falta generar a mano en el día a día:** cada madrugada se mantienen generadas las
  próximas 6 semanas.

**Sesión suelta:**

```json
{ "date": "2026-10-03", "startTime": "10:00:00", "endTime": "13:00:00", "modality": "SWIMMING" }
```

**Cancelar:** `reason` es un `CancellationReason`, obligatorio y sin texto libre. La sesión
no se borra: queda `CANCELLED`. **Reactivar** la devuelve a `SCHEDULED`, y un cierre ya no
vuelve a cancelarla. Cancelar una sesión ya cancelada, o reactivar una que no lo está, es
**400**.

---

## 10. Pasar lista

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/sessions/{id}/roster` | Admin; entrenador principal o ayudante del grupo | Lista de la sesión |
| `PUT /api/sessions/{id}/attendance` | Igual | La lista ya actualizada |

**La lista son los atletas que pertenecían al grupo el día de la sesión**, no los de hoy. Para
una sesión pasada salen quienes estaban ese día, aunque ya no estén en el grupo.

**Respuesta de las dos rutas:**

```json
{
  "sessionId": "…", "date": "2026-09-15", "startTime": "18:00:00", "endTime": "19:00:00",
  "modality": "SWIMMING", "status": "SCHEDULED", "groupId": "…", "groupName": "Alevín A",
  "athletes": [
    { "athleteId": "…", "athleteName": "Ana Pérez", "status": "PRESENT", "registeredBy": "entrenador1" },
    { "athleteId": "…", "athleteName": "Bruno Gil", "status": null, "registeredBy": null }
  ]
}
```

`status` es `null` mientras no se haya marcado. **La lista solo lleva nombre**, ni DNI ni fecha
de nacimiento, y es a propósito.

**Guardar:**

```json
{ "entries": [ { "athleteId": "…", "status": "PRESENT" }, { "athleteId": "…", "status": "LATE" } ] }
```

- **No hace falta mandar a todos:** lo que no venga se queda como estaba. Se puede guardar a
  medida que se marca.
- **Es idempotente:** mandar lo mismo dos veces deja lo mismo, así que se puede reintentar sin
  miedo si se corta la conexión.
- **Entero o nada:** si un atleta de la lista no pertenecía al grupo ese día, no se guarda
  ninguno (**400**).
- **No se pasa lista de una sesión cancelada** (**400**).
- Al guardar, la sesión pasa a `DONE`, **salvo que sea futura**: se puede pasar lista por
  adelantado, pero no cuenta como celebrada hasta su día.
- **Si dos entrenadores guardan a la vez, gana el último**, y ninguno de los dos recibe error.
- `PRESENT` y `LATE` cuentan como asistencia en los informes. `EXCUSED` no guarda el motivo.

---

## 11. Calendario de cierres

Festivos y cierres de piscina.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/closures?from=&to=` | Admin, entrenador | Lista. Fechas obligatorias |
| `POST /api/closures` | Admin | 201 |
| `DELETE /api/closures/{id}` | Admin | 204 |

**Petición:**

```json
{ "startDate": "2026-12-07", "endDate": "2026-12-08", "reason": "HOLIDAY", "modality": null }
```

- `modality` a `null` afecta a todo. Con `SWIMMING`, solo cierra lo del agua, y el seco sigue.
- **Crear un cierre cancela las sesiones ya generadas desde hoy**, y la respuesta dice cuántas
  en `cancelledSessions`. **Enseña ese número:** un error en las fechas tumba muchas sesiones
  de golpe.
- **Borrar un cierre no reactiva nada**; se reactivan una a una.
- Tope de 400 días.

---

## 12. Informes de asistencia

Todas las fechas son obligatorias salvo en `pending`.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/reports/attendance/athlete/{athleteId}?from=&to=` | Admin; entrenador si es su atleta | Asistencia de un atleta, sumando todos sus grupos |
| `GET /api/reports/attendance/group/{groupId}?from=&to=` | Admin; entrenador si es su grupo | Asistencia del grupo |
| `GET /api/reports/attendance/group/{groupId}/export?from=&to=` | Igual | **Archivo CSV** |
| `GET /api/reports/attendance/group/{groupId}/gaps?from=&to=&threshold=` | Igual | Rachas de ausencias. `threshold` por defecto 3 |
| `GET /api/reports/attendance/pending?from=&to=` | Admin; entrenador **sus grupos** | Lo pendiente de registrar. Sin fechas, los últimos 30 días |

**Por atleta:**

```json
{
  "athleteId": "…", "athleteName": "Ana Pérez",
  "sessions": 20, "present": 16, "late": 1, "absent": 2, "excused": 1, "unrecorded": 0,
  "attendanceRate": 0.85,
  "incidents": [ { "sessionId": "…", "date": "2026-09-22", "athleteId": "…", "athleteName": "Ana Pérez" } ]
}
```

**Por grupo:**

```json
{
  "groupId": "…", "groupName": "Alevín A", "from": "…", "to": "…",
  "sessions": 20, "attendanceRate": 0.825,
  "athletes": [ /* un objeto como el de arriba por atleta */ ],
  "sessionsWithoutRoster": [ { "sessionId": "…", "date": "…", "athleteId": null, "athleteName": null } ]
}
```

**Cómo leer los números:**

- `sessions` son las sesiones **celebradas** en las que el atleta pertenecía al grupo. Las
  canceladas y las futuras no cuentan.
- **`attendanceRate` es una fracción de 0 a 1**, con cuatro decimales: `0.85` es un 85 %.
  Para pintarlo como porcentaje, multiplica por 100; el CSV ya viene multiplicado. **Puede
  llegar `null`** cuando no hay sesiones con las que calcularlo.
- `unrecorded` son sesiones celebradas en las que ese atleta quedó sin marcar. **Cuentan como
  falta**, y cada una sale en `incidents` para poder ir a corregirla.
- `sessionsWithoutRoster` son sesiones pasadas **a las que nadie pasó lista**. **No cuentan
  como falta de nadie**; hay que enseñarlas aparte.
- La media del grupo pondera por sesiones posibles: no es la media de los porcentajes.

**CSV:** llega con `Content-Disposition: attachment` y nombre de archivo. Está preparado para
abrirse en Excel en español: separador `;`, coma decimal y codificación UTF-8 con BOM.
Descárgalo como archivo, no lo muestres como texto.

**Rachas:**

```json
[ { "athleteId": "…", "athleteName": "Bruno Gil", "consecutiveAbsences": 4, "lastAttendedOn": "2026-09-01" } ]
```

**Pendientes** (para el aviso de la cabecera):

```json
{
  "from": "…", "to": "…", "total": 3,
  "sessionsWithoutRoster": [ { "sessionId": "…", "date": "…", "groupId": "…", "groupName": "…", "unrecorded": null } ],
  "incompleteRosters":     [ { "sessionId": "…", "date": "…", "groupId": "…", "groupName": "…", "unrecorded": 2 } ]
}
```

`total` es el número a enseñar en el aviso.

### 12.b Documentación pendiente

`GET /api/reports/documents/pending` — admin; entrenador, **solo sus grupos**.

Quién tiene algo sin resolver de la documentación que el club pide cada temporada:
certificado médico, solicitud de licencia y documento de identidad. Es el aviso del
principio de temporada, y sustituye a `/api/medical-certificates/expiring`.

```json
{
  "seasonId": "…", "seasonName": "2026/2027", "total": 2,
  "athletes": [
    { "athleteId": "…", "athleteName": "Bruno Gil", "groups": ["Alevín A"],
      "medicalCertificate": "MISSING", "licenseApplication": "MISSING", "identityDocument": "EXPIRING_SOON" }
  ]
}
```

- **Solo los atletas que hoy están en algún grupo de la temporada activa.** Una ficha que no
  está en ningún grupo no aparece: en septiembre, primero hay que meter a cada nadador en su
  grupo. Así no salen como pendientes las fichas antiguas de quien ya no está.
- Aparece un atleta si **alguno** de los tres no está en regla: `MISSING`, `EXPIRED` o
  `EXPIRING_SOON`. `NOT_REQUIRED` cuenta como en regla.
- Ordenados por nombre. `groups` son los grupos de la temporada en los que está hoy; para un
  entrenador, solo los suyos.
- Sin temporada activa, `seasonId` y `seasonName` llegan `null` y la lista vacía.
- El permiso de viaje no está: no se pide hasta que hay un viaje (§16).

---

## 13. Atletas

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/athletes?q=&gender=&page=&size=&sort=` | Admin; entrenador **sus atletas** | Página |
| `GET /api/athletes/{id}` | Admin; entrenador si es su atleta | Ficha |
| `POST /api/athletes` | Admin, entrenador | 201 |
| `PUT /api/athletes/{id}` | Admin; entrenador si es su atleta | 200 |
| `DELETE /api/athletes/{id}` | Admin | 204. Borrado lógico |
| `GET /api/athletes/my-tutees` | Admin, entrenador | Las fichas de sus tutelados. **Un tutor recibe 403**: para él, §17 |

`q` busca por nombre, apellidos o documento.

**Petición:**

```json
{
  "firstName": "Ana", "lastName": "Pérez", "birthDate": "2015-04-17", "gender": "FEMALE",
  "dni": "X1234567L",
  "guardianConsent": { /* obligatorio si es menor de 14; ver abajo */ }
}
```

**Respuesta:** `id`, `firstName`, `lastName`, `birthDate`, `dni`, `gender`, `createdAt`.

### ⚠️ Cambios del bloque 3b

- **`dni` es opcional** y admite DNI, NIE o pasaporte: letras y números, entre 5 y 20. Se
  guarda en mayúsculas y sin espacios. **En la respuesta puede llegar `null`**, así que la ficha
  y los listados tienen que soportarlo.
- **Duplicados sin documento:** si ya existe en el club alguien con el mismo nombre, apellidos
  y fecha de nacimiento, el alta devuelve **400** con el mensaje
  `"Ya existe un atleta con el mismo nombre, apellidos y fecha de nacimiento. Si es otra persona, indica su documento de identidad"`.
  Conviene enseñarlo tal cual: dice al usuario cómo resolverlo.
- El mismo documento en dos fichas sigue siendo **400** `"Ya existe un atleta con ese DNI"`.

### Alta de un menor de 14 años

**`guardianConsent` es obligatorio.** Sin él, el alta devuelve **400** y la ficha no se crea.

```json
"guardianConsent": {
  "guardian": { "firstName": "Marta", "lastName": "Pérez", "dni": "12345678Z",
                "email": "marta@…", "phone": "600…" },
  "relationship": "MOTHER",
  "evidenceType": "PAPER_FORM",
  "decisionDate": "2026-09-15",
  "evidenceRef": "Ficha inscripción 2026-041",
  "dataProcessing": true,
  "image": false
}
```

- **`dataProcessing` tiene que ser `true`**; si no, **400**.
- `image` puede ser `false`: una negativa es una respuesta válida y queda registrada.
- **El tutor se reutiliza por DNI dentro del club**: el segundo hermano no crea un tutor nuevo.
- **En el `PUT` no se acepta `guardianConsent`** (**400**). Los consentimientos se gestionan en §14.
- **No uses `evidenceType: ONLINE_FORM` desde el panel del club**: registraría la IP de quien
  teclea, no la de quien consiente.

**El documento del tutor es obligatorio** y admite DNI, NIE o pasaporte, con el mismo
formato que el del atleta. Se guarda en mayúsculas y sin espacios, así que `x1234567l` y
`X1234567L` son el mismo tutor, y el segundo hijo no crea una ficha nueva.

---

## 14. Consentimientos

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/consents/athlete/{athleteId}/status` | Admin; entrenador si es su atleta | Qué ampara hoy cada finalidad |
| `GET /api/consents/athlete/{athleteId}` | Admin | Historial completo |
| `POST /api/consents/athlete/{athleteId}` | Admin | 201. Registra una decisión |
| `POST /api/consents/{consentId}/revocation` | Admin | 200. Revoca |

**Estado.** Las cuatro finalidades llegan siempre, y las que no se han contestado vienen en `false`:

```json
{ "DATA_PROCESSING": true, "IMAGE": false, "COMMUNICATIONS": false, "HEALTH_DATA": false }
```

**Es lo que tiene que mirar la interfaz antes de permitir publicar una foto (`IMAGE`).**

**Registrar:**

```json
{
  "guardianId": "…", "type": "IMAGE", "granted": true,
  "decisionDate": "2026-09-15", "evidenceType": "PAPER_FORM", "evidenceRef": "…"
}
```

**Historial:**

```json
[ { "id": "…", "type": "IMAGE", "granted": true, "decisionDate": "…", "evidenceType": "PAPER_FORM",
    "evidenceRef": "…", "revokedAt": null, "active": true, "guardianId": "…", "guardianName": "Marta Pérez" } ]
```

**Reglas:**

- **Nada se edita ni se borra.** Cambiar de opinión es revocar el vigente y registrar uno nuevo.
- Revocar no hace desaparecer la fila: queda con `revokedAt` y `active: false`.
- **Una negativa (`granted: false`) se registra igual** que una concesión.
- El tutor tiene que estar vinculado al atleta; si no, **400**.
- Revocar una negativa, o un consentimiento ya revocado, es **400**.

---

## 15. Certificados médicos

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/medical-certificates/athlete/{athleteId}/status` | Admin; entrenador si es su atleta | `{ "status": "VALID" }` |
| `GET /api/medical-certificates/athlete/{athleteId}` | Admin | Historial |
| `POST /api/medical-certificates/athlete/{athleteId}` | Admin | 201 |
| `PUT /api/medical-certificates/{id}` | Admin | 200, el certificado corregido |
| `DELETE /api/medical-certificates/{id}` | Admin | 204 |

### ⚠️ Cambio del bloque 3b: un certificado por temporada

**Petición:**

```json
{ "issuedOn": "2026-09-10", "seasonId": "…" }
```

- **Ya no se envía `expiresOn`.** La caducidad es el último día de la temporada y la pone el
  servidor.
- **La temporada va explícita.** No la preselecciones sin enseñarla: en agosto es fácil
  registrar el certificado en el curso equivocado.
- `issuedOn` no puede ser futura ni posterior al final de la temporada: **400**.

**Respuesta:**

```json
{
  "id": "…", "athleteId": "…", "seasonId": "…", "seasonName": "2026/2027",
  "issuedOn": "2026-09-10", "expiresOn": "2027-08-31",
  "status": "VALID", "validatedBy": "adminjaes", "validatedAt": "…"
}
```

**Estado de un atleta,** medido contra la temporada activa:

| Estado | Significa | En la interfaz |
|---|---|---|
| `VALID` | Tiene el de la temporada activa | En regla |
| `EXPIRING_SOON` | Lo tiene, pero la temporada acaba en 30 días o menos | Aviso; sigue cubierto |
| `EXPIRED` | El último que trajo es de otro curso | "Pedir renovación" |
| `MISSING` | Nunca ha traído ninguno | "Pedir certificado" |

**No hay diagnóstico, observaciones ni campo de apto:** no los pidas en el formulario.

### Corregir y borrar

- **`PUT`** lleva el mismo cuerpo que el alta y pasa **las mismas reglas**: 400 si la fecha es
  futura o posterior al final de la temporada.
- **No cambia de atleta.** Un certificado anotado en el nadador equivocado se borra y se vuelve
  a anotar en el bueno.
- Al corregir, **quien corrige pasa a ser el validador**, con la hora de la corrección.
- **`DELETE` borra de verdad.** No queda rastro de lo que había, ni de quién lo cambió: eso
  llegará con la auditoría. Pide confirmación en la interfaz.
- Uno que no existe o es de otro club: **404**.

**`/api/medical-certificates/expiring` ya no existe** y responde 404. Lo sustituye el informe
de documentación pendiente (§12.b).

---

## 16. Entregas de papeles

Sustituye a la subida de documentos (§18). **El papel se queda en el club**; aquí solo se
apunta que se entregó.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/document-deliveries/athlete/{athleteId}/status` | Admin; entrenador si es su atleta | Licencia y documento de identidad |
| `GET /api/document-deliveries/athlete/{athleteId}/travel-permit?from=&to=` | Igual | Si puede viajar esas fechas |
| `GET /api/document-deliveries/athlete/{athleteId}` | Admin | Historial |
| `POST /api/document-deliveries/athlete/{athleteId}` | Admin | 201 |
| `PUT /api/document-deliveries/{id}` | Admin | 200, la entrega corregida |
| `DELETE /api/document-deliveries/{id}` | Admin | 204 |

**Corregir:** mismo cuerpo que el alta y **mismas reglas**. **Se puede cambiar el tipo** —anotar
la licencia como documento de identidad tiene arreglo—, pero con el tipo nuevo tienen que venir
sus campos y ningún otro. No cambia de atleta, y quien corrige pasa a ser quien lo anotó.
**Borrar** es de verdad y sin rastro: pide confirmación. Una de otro club o inexistente: 404.

**Petición.** Cada tipo lleva **solo** sus campos; mandar de más es **400**:

```json
{ "type": "LICENSE_APPLICATION", "deliveredOn": "2026-09-15", "seasonId": "…" }
{ "type": "IDENTITY_DOCUMENT",   "deliveredOn": "2026-09-15", "validUntil": "2031-05-01" }
{ "type": "TRAVEL_PERMIT",       "deliveredOn": "2026-09-15", "validFrom": "2026-10-10", "validUntil": "2026-10-13" }
```

| Tipo | Campos | Nota |
|---|---|---|
| Solicitud de licencia | `seasonId` | Una por temporada |
| Documento de identidad | `validUntil` = caducidad del documento | |
| Permiso de viaje | `validFrom` = salida, `validUntil` = vuelta | **Solo para menores** el día de salida; con un adulto, 400 |

- `deliveredOn` es obligatorio y no puede ser futuro.
- **No hay número de documento, destino ni notas:** no los pidas en el formulario.
- El certificado médico no va aquí (§15), y el derecho de imagen tampoco: es un
  consentimiento `IMAGE` en papel (§14).

**Estado:**

```json
{ "LICENSE_APPLICATION": "VALID", "IDENTITY_DOCUMENT": "NOT_REQUIRED" }
```

- La licencia se mide contra la temporada activa: `EXPIRED` si solo trajo la de otro curso,
  `MISSING` si ninguna.
- El documento de identidad manda el que más tarde caduca. **`NOT_REQUIRED`** si la ficha del
  atleta no tiene documento.

**Permiso de viaje** (para la pantalla de convocatoria de una competición):

```json
{ "from": "2026-10-10", "to": "2026-10-13", "required": true, "covered": false }
```

**Puede viajar si `required` es `false` o `covered` es `true`.** Van separados para que la
interfaz pueda decir por qué no puede.

**Historial:** cada entrega con `id`, `athleteId`, `type`, `seasonId`, `seasonName`,
`validFrom`, `validUntil`, `deliveredOn`, `status` (calculado hoy), `registeredBy` y
`registeredAt`. No se puede corregir ni borrar una entrega.

---

## 17. Vínculos y claves de invitación

Así se da acceso a un tutor o a un deportista con cuenta a los datos de un atleta.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `POST /api/athlete-links/{athleteId}/key` | Admin; entrenador para sus atletas | 201, la clave |
| `GET /api/athlete-links/by-athlete/{athleteId}` | Admin; entrenador para sus atletas | Quién tiene acceso |
| `POST /api/athlete-links/redeem` | Cualquier autenticado | 201, el vínculo creado |
| `GET /api/athlete-links/my-athletes` | Cualquier autenticado | Mis vínculos |
| `GET /api/athlete-links/my-tutees` | Cualquier autenticado | Mis vínculos de tutor |

**Generar:** `{ "type": "TUTOR" }` →

```json
{ "key": "…", "athleteId": "…", "athleteFullName": "Ana Pérez", "type": "TUTOR", "expiresAt": "…" }
```

**Canjear:** `{ "key": "…" }`.

- La clave caduca a las **72 horas** y es de un solo uso.
- Una clave que no existe, ya usada o caducada, o un usuario que ya tiene vínculo con ese
  atleta: **400** con su mensaje.
- **La clave da acceso a los datos de un menor:** enséñala una vez para copiarla, no la
  guardes ni la pongas en una URL.

**Vínculo:** `id`, `userId`, `username`, `athleteId`, `athleteFullName`, `type`, `createdAt`.

> **Usa `/api/athlete-links/my-tutees`, no `/api/athletes/my-tutees`.** La segunda existe, pero
> cae bajo una regla que exige rol de administrador o entrenador, así que un tutor recibe 403.

---

## 17.b Resultados de competición

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/competition-results?q=&stroke=&distanceMeters=&poolLength=&partial=&gender=` | Admin; entrenador **sus atletas** | Página |
| `GET /api/competition-results/{id}` | Admin; entrenador si es su atleta | El resultado |
| `GET /api/competition-results/athlete/{athleteId}` | Admin; entrenador si es su atleta | Lista, sin paginar |
| `POST /api/competition-results` | Admin; entrenador para sus atletas | 201 |
| `PUT /api/competition-results/{id}` | Admin; entrenador si es su atleta **y** el del cuerpo también | 200 |
| `DELETE /api/competition-results/{id}` | Admin | 204. Borrado lógico: desaparece de todo |
| `GET /api/competition-results/me?stroke=&distanceMeters=&poolLength=&partial=&gender=` | Cualquier autenticado | Página: los de **sus** atletas vinculados |

`q` busca en nombre y apellidos del atleta. `/me` devuelve los resultados de los atletas con los
que la cuenta tiene vínculo, como tutor o como deportista (§17); sin vínculos, página vacía.

**Petición (`POST` y `PUT`, igual):**

```json
{ "athleteId": "…", "competitionDate": "2026-03-10", "distanceMeters": 100,
  "stroke": "FREESTYLE", "poolLength": 25, "resultTimeMillis": 58320,
  "partial": false, "finalResultId": null }
```

- **`resultTimeMillis` va en milisegundos**: 58320 son 58,32 s. Conviértelo en la interfaz.
- `poolLength`: **25 o 50**. Otra cosa, 400.
- **Resultado final** (`partial: false`): `distanceMeters` solo **50, 100, 200, 400, 800 o 1500**.
- **Parcial** (`partial: true`): múltiplo de 50 entre 50 y 1450, y **`finalResultId`
  obligatorio**, apuntando a un resultado que no sea a su vez un parcial. **No se comprueba que
  el final sea del mismo atleta**: ofrece en el selector solo los finales de ese nadador.
- En `PUT`, el entrenador no puede mover un resultado suyo a un atleta que no lleva: 404.

**Respuesta:** `id`, `athleteId`, `athleteFullName`, `competitionDate`, `distanceMeters`,
`stroke`, `poolLength`, `resultTimeMillis`, `partial`, `finalResultId`, `createdAt`.

---

## 17.c Noticias, comentarios e imágenes

### Noticias

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/posts/published?q=&author=` | **Público**, sin token | Página de publicadas |
| `GET /api/posts/published/{id}` | **Público** | La noticia; 404 si no está publicada |
| `GET /api/posts?q=&author=&status=` | Admin, editor | Página: publicadas **y borradores** |
| `GET /api/posts/{id}` | Admin, editor | La noticia, esté como esté |
| `POST /api/posts` | **Solo editor** | 201. Siempre nace como borrador |
| `PUT /api/posts/{id}` | Admin, editor | 200 |
| `DELETE /api/posts/{id}` | Admin, editor | 204 |

**Ojo: crear es solo de `ROLE_EDITOR`, no de administrador.** Un administrador sin rol de
editor recibe 403 al crear, aunque sí puede editar y borrar. Si el club quiere que el
administrador escriba noticias, dale también el rol de editor.

**Estados:** `DRAFT` (borrador), `PUBLISHED`, `DELETED`. **Una noticia borrada no sale por
ninguna ruta**, ni por id ni filtrando `status=DELETED`: no hay papelera ni forma de
recuperarla desde la API.

**`q`** busca en título y contenido; **`author`** es el username exacto.

**Crear — multipart, no JSON.** Dos partes: `data`, con el JSON, e `images`, opcional, con
hasta **10** imágenes. Así en el navegador:

```js
const fd = new FormData();
fd.append('data', new Blob([JSON.stringify({ title, content })], { type: 'application/json' }));
imagenes.forEach(archivo => fd.append('images', archivo));
await fetch(`${API}/api/posts`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd });
```

- **La parte `data` tiene que ir como `application/json`**, con el `Blob` de arriba. Como texto
  plano, el servidor la rechaza.
- **No pongas tú el `Content-Type` de la petición:** lo pone el navegador con el separador.
- `title` y `content` obligatorios. **`status` se ignora al crear**: nace en borrador.
- Imágenes: JPEG, PNG, WEBP o GIF, **5 MB cada una y 55 MB en total**. Otro tipo, 400.

**Editar — JSON:** `{ "title": "…", "content": "…", "status": "PUBLISHED" }`.

- **`title` y `content` son obligatorios siempre**, también para solo publicar: manda los
  actuales.
- `status` es opcional. Pasar a `PUBLISHED` pone `publishedAt` **la primera vez**; volver a
  borrador y republicar no la cambia.
- **Las imágenes no se pueden cambiar después de crear la noticia.**

**Borrar** la pasa a `DELETED`: deja de existir para toda la API.

**Respuesta:** `id`, `title`, `content`, `slug`, `status`, `authorUsername`, `imageUrls`,
`publishedAt`, `createdAt`, `updatedAt`. El `slug` se genera del título y **no identifica
nada**: se puede repetir. Las rutas van siempre por `id`.

> ⚠️ **`content` se guarda tal cual, sin sanear.** Si lo pintas como HTML
> (`dangerouslySetInnerHTML`), sanéalo antes en el frontend: si no, una cuenta de editor puede
> meter un script que se ejecute a cada visitante de la web pública.

### Comentarios

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/posts/{postId}/comments` | Cualquier autenticado | Lista, del más antiguo al más nuevo |
| `POST /api/posts/{postId}/comments` | Cualquier autenticado | 201 |
| `DELETE /api/posts/{postId}/comments/{id}` | **Solo su autor** | 204. Cualquier otro, 403, **también un administrador** |
| `PATCH /api/posts/{postId}/comments/{id}/block` | Admin, editor | 200, el comentario |
| `PATCH /api/posts/{postId}/comments/{id}/unblock` | Admin, editor | 200, el comentario |
| `GET /api/users/{id}/comments` | Cualquier autenticado | Lista de los comentarios de esa cuenta |

**Petición:** `{ "content": "…" }`, obligatorio.

- **La lista de una noticia no trae los bloqueados ni los borrados.** Para moderar, el
  administrador o el editor **bloquea**: no puede borrar un comentario ajeno.
- **`/api/users/{id}/comments` sí incluye los bloqueados** (no los borrados). Si lo enseñas en
  un perfil público, filtra por `blocked`.
- No se comprueba que la noticia esté publicada para comentar: no enseñes el formulario en
  borradores.

**Respuesta:** `id`, `content`, `authorUsername`, `postId`, `postTitle`, `blocked`, `createdAt`.

### Imágenes

`GET /api/images/{filename}` — **público**. 404 si no existe.

**Las URLs que devuelve la API son relativas** —`imageUrls` en noticias y `profilePhoto` en
usuarios, del tipo `/api/images/xxxx.png`—: **antepón la base de la API** (`VITE_API_URL`). Con
el frontend en otro dominio, una URL relativa apunta al del frontend y la imagen no carga.

---

## 17.d Gestión de usuarios

Lo de la sesión, la contraseña y el bloqueo está en §2. Aquí, el resto.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/users?q=&role=&blocked=` | Admin | Página |
| `POST /api/users` | Admin | 201, la cuenta |
| `PATCH /api/users/{id}` | Admin | 200. Sustituye **todos** los roles |
| `PUT /api/users/{id}/roles` | Admin | 200. Deja **un solo** rol |
| `DELETE /api/users/{id}` | Admin | 204. Borrado lógico |
| `POST /api/users/{id}/profile-photo` | La propia cuenta, o admin | 200, la cuenta |
| `POST /api/auth/signup` | **Público** | 201, tokens de la cuenta creada |
| `POST /api/auth/signup/with-role` | Admin | 201, tokens **de la cuenta creada** |

**Listado:** `q` busca en username y correo; `role` es el nombre completo (`ROLE_ADMIN`…);
`blocked`, `true` o `false`.

**Crear (`POST /api/users`):**

```json
{ "username": "entrenadora1", "email": "ana@club.es", "password": "…", "roles": ["ROLE_TECHNICAL_STAFF"] }
```

- Sin `roles`, la cuenta nace con `ROLE_USER`.
- La contraseña pasa la política de §2.b.
- **400** `"El username ya está en uso"` (único en el club) o `"El email ya está en uso"`. **El
  correo es único en todo el sistema, no por club**: la misma persona no puede tener cuenta en
  dos clubes con el mismo correo.

**Roles, dos rutas que no hacen lo mismo:**

- `PATCH /api/users/{id}` con `{ "roles": ["ROLE_ADMIN", "ROLE_EDITOR"] }` **sustituye todos**.
  Con la lista vacía o sin `roles`, no cambia nada.
- `PUT /api/users/{id}/roles` con `{ "role": "ROLE_EDITOR" }` deja **solo ese**.

**Borrar** la cuenta es lógico, pero en la práctica desaparece: deja de salir en el listado, **no
puede entrar** y su sesión abierta deja de valer.

**Foto de perfil:** multipart con **un campo `file`**. JPEG, PNG, WEBP o GIF. Otra cuenta que
no sea la tuya, siendo no administrador: 404. Sustituye la anterior.

> ⚠️ **Con más de 5 MB la petición no recibe respuesta:** el servidor corta la subida y el
> cliente ve un error de conexión, sin código ni mensaje. **Comprueba el tamaño en el frontend
> antes de subir**, en fotos de perfil y en imágenes de noticias.

**Respuesta de una cuenta:** `id`, `username`, `email`, `blocked`, `roles`, `createdAt`,
`profilePhoto` (URL relativa, ver §17.c).

**Alta pública** (`POST /api/auth/signup`): `{ "username", "email", "password" }`. Crea una
cuenta `ROLE_USER` **en el club por defecto** y devuelve sus tokens, como un login. Con un solo
club es correcto; en qué club cae cuando haya varios sigue sin decidir.

**Alta con rol** (`POST /api/auth/signup/with-role`): `{ "username", "email", "password",
"roles": [...] }`. **Devuelve los tokens de la cuenta nueva, no los tuyos: no los guardes**, o
la sesión del administrador pasa a ser la de la cuenta que acaba de crear. **Usa
`POST /api/users`**, que hace lo mismo y devuelve la cuenta.

---

## 17.e Documentos subidos (apagados)

| Método y ruta |
|---|
| `POST /api/athlete-documents/athlete/{athleteId}` |
| `GET /api/athlete-documents/athlete/{athleteId}` |
| `GET /api/athlete-documents/my` |
| `GET /api/athlete-documents/{documentId}/file` |
| `DELETE /api/athlete-documents/{documentId}` |

**Las cinco responden 404 a todo el mundo** mientras `app.documents.upload.enabled` esté a
`false`, que es como viene. No hagas pantallas contra ellas: el club registra que le entregaron
los papeles con las entregas de §16, y los papeles no se suben. Antes de encenderla harían
falta cifrado, un directorio aparte y registro de accesos.

---

## 18. Qué rompe en pantallas que ya existen

| Pantalla | Qué cambia | Qué hacer |
|---|---|---|
| **Mis documentos** y subida de documentos en la ficha | `/api/athlete-documents/**` responde **404 a todo el mundo**: la subida está apagada | Quitar o esconder la pantalla y pasar a entregas de papeles (§16) |
| Gestión de atletas | `dni` opcional y puede llegar `null`; nuevo 400 por duplicado | §13 |
| Alta de certificado, si existe | Pide `seasonId`, ya no `expiresOn` | §15 |
| Aviso de certificados que caducan, si existe | `/api/medical-certificates/expiring` responde 404 | Pasar al informe de documentación pendiente (§12.b) |
| Todo lo que use un entrenador | Fuera de sus grupos, **404**; listados filtrados | Tratar 404 como "no disponible", no como fallo |
| Foto de perfil | Solo la propia, o cualquiera siendo administrador; si no, 404 | |
| Grupos | La respuesta añade `assistantCoaches`; la petición admite `assistantCoachIds` | §6 |
| **Cualquier sesión abierta** | Los tokens de antes no llevan `typ` y dejan de valer | Al recibir 401, borrar los tokens guardados y mandar al login |
| Login | Aparecen **429** (demasiados intentos) y **403** (cuenta bloqueada, antes 500) | §2 |
| Refresco | El access token en `/refresh` pasa a 401; el refresh token deja de autenticar | §2 |
| Gestión de usuarios | Rutas nuevas para cambiar la contraseña, propia y de administrador | §2 |
| Alta de usuario y alta pública | El mínimo sube de 8 a 12 y hay más motivos de rechazo; el error llega en `message`, ya no en `errors.password` | §2.b |
| Cualquier pantalla | Si el club bloquea una cuenta, sus peticiones pasan a 401 en el acto | Tratar el 401 como sesión terminada y volver al login |
| Certificados y entregas | Rutas nuevas para corregir (`PUT`) y borrar (`DELETE`) por id | §15 y §16 |
| Entornos nuevos | Ya no traen las cuentas `admin`, `editor`, `tecnico` y `usuario` ni datos de ejemplo | Entrar con el administrador de `ADMIN_USERNAME` |
| **Noticias: listado y lectura por id** | `GET /api/posts` y `GET /api/posts/{id}` pasan a ser **solo de administrador o editor**: devuelven también los borradores. Antes los leía cualquier cuenta | Para cualquier otra cuenta, y en la web pública, usar `/api/posts/published` y `/api/posts/published/{id}` |
| Cualquier llamada con la ruta mal escrita | 403 en vez de 404 | Revisar la ruta (§1) |
| Alta con rol | El mínimo de contraseña de esa ruta seguía en 8 y el error llegaba en `errors.password`; ahora 12 y en `message`, como las demás | §2.b |
| **Lo que salga de `API_DOCS.md`** | Es de mayo: permisos, mínimos de contraseña y documentos subidos ya no son así | Usar este documento |

---

## 19. Pendiente que puede tocar estos contratos

- **Recuperación por correo**: no existe. Un "he olvidado mi contraseña" no tiene hoy a dónde
  llamar; la salida es que el administrador la fije (§2) y avise por su cuenta. Cuando entre,
  será una ruta pública nueva y no cambiará las que ya hay.
- **Cerrar sesiones a distancia**: hoy solo se consigue bloqueando la cuenta. Una lista de
  revocación permitiría cerrar la sesión de un dispositivo perdido sin bloquear al usuario.
