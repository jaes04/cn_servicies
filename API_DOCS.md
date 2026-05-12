# CN Servicies — API Documentation

**Base URL:** `http://localhost:8080`  
**Autenticación:** JWT Bearer Token  
**Algoritmo:** HS384

---

## Autenticación

Todos los endpoints protegidos requieren el header:
```
Authorization: Bearer <token>
```

---

## Roles

| Rol | Descripción |
|-----|-------------|
| `ROLE_ADMIN` | Acceso total |
| `ROLE_EDITOR` | Crear y editar posts |
| `ROLE_TECHNICAL_STAFF` | Gestión de atletas y resultados |
| `ROLE_USER` | Usuario estándar |

---

## Endpoints

### Auth — `/api/auth`

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `POST` | `/api/auth/signup` | No | Registro de usuario (rol USER por defecto) |
| `POST` | `/api/auth/signup/with-role` | No | Registro con rol específico |
| `POST` | `/api/auth/login` | No | Login, devuelve access + refresh token |
| `POST` | `/api/auth/refresh` | No | Renueva el access token |

#### POST `/api/auth/login`
```json
// Request
{
  "username": "admin",
  "password": "contraseña"
}

// Response 200
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer"
}
```

#### POST `/api/auth/signup`
```json
// Request
{
  "username": "usuario",
  "email": "usuario@email.com",
  "password": "minimo8chars"
}
```

#### POST `/api/auth/signup/with-role`
```json
// Request
{
  "username": "editor1",
  "email": "editor1@email.com",
  "password": "minimo8chars",
  "role": "ROLE_EDITOR"
}
```

#### POST `/api/auth/refresh`
```json
// Request
{
  "refreshToken": "eyJ..."
}
```

#### GET `/api/auth/hash` _(temporal — solo para pruebas)_
```
GET /api/auth/hash?raw=miContraseña
// Response: "$2a$10$..."  (hash BCrypt listo para insertar en BD)
```

---

### Users — `/api/users`

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `GET` | `/api/users/me` | Autenticado | Perfil del usuario actual |
| `GET` | `/api/users` | ADMIN | Listar usuarios (paginado, con filtros) |
| `GET` | `/api/users/{id}/comments` | Autenticado | Comentarios hechos por el usuario |
| `POST` | `/api/users` | ADMIN | Crear usuario |
| `PATCH` | `/api/users/{id}` | ADMIN | Actualizar roles del usuario |
| `PUT` | `/api/users/{id}/roles` | ADMIN | Cambiar rol del usuario |
| `POST` | `/api/users/{id}/profile-photo` | Autenticado | Subir foto de perfil |
| `PATCH` | `/api/users/{id}/block` | ADMIN | Bloquear usuario |
| `PATCH` | `/api/users/{id}/unblock` | ADMIN | Desbloquear usuario |
| `DELETE` | `/api/users/{id}` | ADMIN | Borrado lógico del usuario |

#### GET `/api/users/{id}/comments`
Devuelve todos los comentarios hechos por el usuario, ordenados del más reciente al más antiguo. Incluye comentarios bloqueados.

```json
// Response 200 — Array de CommentResponse
[
  {
    "id": "uuid",
    "content": "Texto del comentario",
    "authorUsername": "usuario1",
    "postId": "uuid",
    "blocked": false,
    "createdAt": "2026-04-29T10:00:00"
  }
]
```

#### PUT `/api/users/{id}/roles`
```json
// Request
{
  "role": "ROLE_TECHNICAL_STAFF"
}
// Valores: ROLE_ADMIN | ROLE_EDITOR | ROLE_USER | ROLE_TECHNICAL_STAFF
```

#### PATCH `/api/users/{id}`
```json
// Request (todos los campos son opcionales)
{
  "roles": ["ROLE_EDITOR"]
}
```

#### GET `/api/users`
```
Parámetros de paginación: ?page=0&size=20&sort=createdAt,desc
Filtros opcionales:
  ?q=jorge          → busca en username y email (case-insensitive)
  ?role=ROLE_EDITOR → filtra por rol (ROLE_ADMIN | ROLE_EDITOR | ROLE_USER | ROLE_TECHNICAL_STAFF)
  ?blocked=false    → filtra por estado bloqueado/desbloqueado

Ejemplo: GET /api/users?q=jorge&role=ROLE_EDITOR&blocked=false&page=0&size=20
```

#### PATCH `/api/users/{id}/block` y `/api/users/{id}/unblock`
Sin body. Devuelve el `UserResponse` actualizado.

#### Response Usuario
```json
{
  "id": "uuid",
  "username": "admin",
  "email": "admin@email.com",
  "blocked": false,
  "roles": ["ROLE_ADMIN"],
  "createdAt": "2026-04-29T10:00:00",
  "profilePhoto": "/api/images/foto.jpg"
}
```

---

### Athletes — `/api/athletes`

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `GET` | `/api/athletes` | TECHNICAL_STAFF | Listar atletas (paginado, con filtros) |
| `GET` | `/api/athletes/{id}` | TECHNICAL_STAFF | Obtener atleta |
| `POST` | `/api/athletes` | TECHNICAL_STAFF | Crear atleta |
| `PUT` | `/api/athletes/{id}` | TECHNICAL_STAFF | Actualizar atleta |
| `DELETE` | `/api/athletes/{id}` | ADMIN | Borrado lógico |

#### GET `/api/athletes`
```
Parámetros de paginación: ?page=0&size=20&sort=lastName,asc
Filtros opcionales:
  ?q=carlos   → busca en nombre, apellido y DNI (case-insensitive)

Ejemplo: GET /api/athletes?q=garcia&page=0&size=20
```

#### POST/PUT `/api/athletes`
```json
// Request
{
  "firstName": "Carlos",
  "lastName": "García",
  "birthDate": "2000-05-15",
  "dni": "12345678A"
}
```

#### Response Atleta
```json
{
  "id": "uuid",
  "firstName": "Carlos",
  "lastName": "García",
  "birthDate": "2000-05-15",
  "dni": "12345678A",
  "createdAt": "2026-04-29T10:00:00"
}
```

---

### Competition Results — `/api/competition-results`

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `GET` | `/api/competition-results/me` | Autenticado | Mis tiempos (atletas vinculados al usuario) |
| `GET` | `/api/competition-results` | ADMIN, TECHNICAL_STAFF | Listar resultados (paginado, con filtros) |
| `GET` | `/api/competition-results/{id}` | ADMIN, TECHNICAL_STAFF | Obtener resultado |
| `GET` | `/api/competition-results/athlete/{athleteId}` | ADMIN, TECHNICAL_STAFF | Resultados de un atleta |
| `POST` | `/api/competition-results` | ADMIN, TECHNICAL_STAFF | Crear resultado |
| `PUT` | `/api/competition-results/{id}` | ADMIN, TECHNICAL_STAFF | Actualizar resultado |
| `DELETE` | `/api/competition-results/{id}` | ADMIN | Borrado lógico |

#### GET `/api/competition-results/me`
Devuelve los tiempos de todos los atletas vinculados al usuario autenticado. Admite los mismos filtros que el listado general excepto `?q`.
```
Parámetros de paginación: ?page=0&size=20&sort=competitionDate,desc
Filtros opcionales:
  ?stroke=FREESTYLE    → filtra por estilo
  ?distanceMeters=100  → filtra por distancia
  ?poolLength=50       → filtra por longitud de piscina
  ?partial=false       → filtra por parciales o finales
```

#### GET `/api/competition-results`
```
Parámetros de paginación: ?page=0&size=20&sort=competitionDate,desc
Filtros opcionales:
  ?q=carlos            → busca en nombre y apellido del atleta (case-insensitive)
  ?stroke=FREESTYLE    → filtra por estilo (FREESTYLE | BACKSTROKE | BREASTSTROKE | BUTTERFLY | MEDLEY)
  ?distanceMeters=100  → filtra por distancia (50 | 100 | 200 | 400 | 800 | 1500)
  ?poolLength=50       → filtra por longitud de piscina (25 | 50)
  ?partial=false       → filtra por resultados parciales o finales

Ejemplo: GET /api/competition-results?q=garcia&stroke=FREESTYLE&distanceMeters=100&poolLength=50&page=0&size=20
```

#### POST/PUT `/api/competition-results`
```json
// Request
{
  "athleteId": "uuid-del-atleta",
  "competitionDate": "2026-03-20",
  "distanceMeters": 100,
  "stroke": "FREESTYLE",
  "poolLength": 50,
  "resultTimeMillis": 52340,
  "partial": false,
  "finalResultId": null
}
// stroke: FREESTYLE | BACKSTROKE | BREASTSTROKE | BUTTERFLY | MEDLEY
```

#### Response Resultado
```json
{
  "id": "uuid",
  "athleteId": "uuid",
  "athleteFullName": "Carlos García",
  "competitionDate": "2026-03-20",
  "distanceMeters": 100,
  "stroke": "FREESTYLE",
  "poolLength": 50,
  "resultTimeMillis": 52340,
  "partial": false,
  "finalResultId": null,
  "createdAt": "2026-04-29T10:00:00"
}
```

---

### Athlete Links — `/api/athlete-links`

Permite vincular usuarios a atletas mediante una key de invitación de un solo uso.  
Los tipos de vínculo son `ATHLETE` (el propio atleta) y `TUTOR` (tutor de un menor).

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `POST` | `/api/athlete-links/{athleteId}/key` | ADMIN, TECHNICAL_STAFF | Genera una key de invitación |
| `POST` | `/api/athlete-links/redeem` | Autenticado | Canjea la key y vincula al usuario actual |
| `GET` | `/api/athlete-links/my-athletes` | Autenticado | Atletas vinculados al usuario actual |
| `GET` | `/api/athlete-links/by-athlete/{athleteId}` | ADMIN, TECHNICAL_STAFF | Usuarios vinculados a un atleta |

#### POST `/api/athlete-links/{athleteId}/key`
Genera una key válida durante **72 horas** y de **un solo uso**.
```json
// Request
{
  "type": "TUTOR"
}
// type: ATHLETE | TUTOR

// Response 201
{
  "key": "550e8400-e29b-41d4-a716-446655440000",
  "athleteId": "uuid",
  "athleteFullName": "Carlos García",
  "type": "TUTOR",
  "expiresAt": "2026-05-11T10:00:00"
}
```

#### POST `/api/athlete-links/redeem`
Valida la key y crea el vínculo entre el usuario autenticado y el atleta.
```json
// Request
{
  "key": "550e8400-e29b-41d4-a716-446655440000"
}

// Response 201
{
  "id": "uuid",
  "userId": "uuid",
  "username": "jorge",
  "athleteId": "uuid",
  "athleteFullName": "Carlos García",
  "type": "TUTOR",
  "createdAt": "2026-05-08T10:00:00"
}
```

Posibles errores al canjear:
- `400` — Key no válida
- `409` — Key ya utilizada o expirada, o usuario ya vinculado al atleta

#### GET `/api/athlete-links/my-athletes`
Devuelve la lista de atletas vinculados al usuario autenticado (array de `UserAthleteResponse`).

---

### Posts — `/api/posts`

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `GET` | `/api/posts/published` | No | Listar posts publicados (paginado) |
| `GET` | `/api/posts/published/{slug}` | No | Obtener post publicado por slug |
| `GET` | `/api/posts` | Autenticado | Listar todos los posts (paginado) |
| `GET` | `/api/posts/{id}` | Autenticado | Obtener post por ID |
| `POST` | `/api/posts` | EDITOR | Crear post (multipart/form-data) |
| `PUT` | `/api/posts/{id}` | ADMIN, EDITOR | Actualizar post |
| `DELETE` | `/api/posts/{id}` | ADMIN | Borrado lógico |

#### GET `/api/posts/published`
```
Parámetros de paginación: ?page=0&size=10&sort=publishedAt,desc
Filtros opcionales:
  ?q=natación       → busca en título y contenido (case-insensitive)
  ?author=editor1   → filtra por nombre de usuario del autor

Ejemplo: GET /api/posts/published?q=natación&author=editor1&page=0&size=10
```

#### GET `/api/posts`
```
Parámetros de paginación: ?page=0&size=10&sort=createdAt,desc
Filtros opcionales:
  ?q=natación         → busca en título y contenido (case-insensitive)
  ?author=editor1     → filtra por nombre de usuario del autor
  ?status=DRAFT       → filtra por estado (DRAFT | PUBLISHED)

Ejemplo: GET /api/posts?q=campeonato&status=PUBLISHED&page=0&size=5
```

#### POST `/api/posts` — multipart/form-data
```
Part "data" (application/json):
{
  "title": "Título del post",
  "content": "Contenido...",
  "slug": "titulo-del-post",
  "status": "DRAFT"
}
// status: DRAFT | PUBLISHED | DELETED

Part "images" (opcional): archivos de imagen (máx. 5MB c/u, total 55MB)
```

#### Response Post
```json
{
  "id": "uuid",
  "title": "Título del post",
  "content": "Contenido...",
  "slug": "titulo-del-post",
  "status": "PUBLISHED",
  "publishedAt": "2026-04-29T10:00:00",
  "authorUsername": "editor1",
  "images": ["/api/images/archivo.jpg"],
  "createdAt": "2026-04-29T10:00:00"
}
```

---

### Comments — `/api/posts/{postId}/comments`

| Método | Ruta | Rol requerido | Descripción |
|--------|------|---------------|-------------|
| `POST` | `/api/posts/{postId}/comments` | Autenticado | Crear comentario |
| `GET` | `/api/posts/{postId}/comments` | Autenticado | Listar comentarios del post |
| `DELETE` | `/api/posts/{postId}/comments/{id}` | Autenticado (solo autor) | Borrado lógico del comentario |
| `PATCH` | `/api/posts/{postId}/comments/{id}/block` | ADMIN, EDITOR | Bloquear comentario |
| `PATCH` | `/api/posts/{postId}/comments/{id}/unblock` | ADMIN, EDITOR | Desbloquear comentario |

#### POST `/api/posts/{postId}/comments`
```json
// Request
{
  "content": "Texto del comentario"
}
```

#### DELETE `/api/posts/{postId}/comments/{id}`
Borrado lógico: marca el comentario con `deletedAt`. Solo puede ejecutarlo el autor del comentario.
- `204 No Content` — eliminado correctamente
- `403 Forbidden` — el usuario autenticado no es el autor
- `404 Not Found` — comentario no encontrado

#### PATCH `/api/posts/{postId}/comments/{id}/block` y `/api/posts/{postId}/comments/{id}/unblock`
Sin body. Devuelve el `CommentResponse` actualizado.

#### Response Comentario
```json
{
  "id": "uuid",
  "content": "Texto del comentario",
  "authorUsername": "usuario1",
  "postId": "uuid",
  "blocked": false,
  "createdAt": "2026-04-29T10:00:00"
}
```

---

### Images — `/api/images`

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/api/images/{filename}` | No | Obtener imagen por nombre de archivo |

---

## Errores

```json
// 401 — No autenticado
{
  "status": 401,
  "message": "Debes autenticarte para acceder a este recurso. Incluye un token válido en la cabecera Authorization",
  "path": "/api/...",
  "timestamp": "2026-04-29T10:00:00"
}

// 403 — Sin permisos
{
  "status": 403,
  "message": "No tienes los permisos necesarios para acceder a este recurso",
  "path": "/api/...",
  "timestamp": "2026-04-29T10:00:00"
}
```

---

## Notas

- El borrado de usuarios, atletas y resultados es **lógico** (`deleted_at`). Los registros no se eliminan de la BD.
- Los posts se marcan como `DELETED` en el campo `status`.
- El token de acceso expira en **24 horas**. Usa el refresh token para renovarlo.
- El refresh token expira en **7 días**.