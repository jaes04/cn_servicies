# Convenciones de código

Reglas inferidas del repositorio real. Cuando el código contradiga este documento,
**sigue el código** y avisa de la discrepancia al terminar.

---

## Organización del código

### Monolito modular: paquetes por funcionalidad

Base: `es.jaes.cn_servicies`

**Todo lo de una funcionalidad vive junto en el mismo paquete**: entidad, controlador,
repositorio, servicio, DTOs y enums. No hay paquetes `entity/`, `dto/`, `service/`
separados.

Esto no es preferencia estética: cada paquete es una **frontera de módulo**, pensada para
que uno pueda extraerse como servicio independiente si necesita escalar aparte. Ver
`arquitectura.md` §1.

```
es.jaes.cn_servicies
├── athlete/              Athlete, Controller, Repository, Request,
│                         Response, Service, Specification, Gender…
├── athlete_document/     + DocumentStorageService
├── athlete_link/         UserAthlete, AthleteInviteKey y sus DTOs
├── auth/                 JWT, login, signup, filtros
├── comment/
├── competition_result/   + Stroke
├── post/                 Post, PostImage, ImageController, ImageStorageService
├── user/                 User, Role, RoleName
└── config/               Security, CORS, GlobalExceptionHandler, AdminInitializer
```

Los nombres de paquete usan **snake_case** (`athlete_document`, `competition_result`).
No es la convención habitual de Java, pero es la del proyecto: mantenla.

`config/` es la única excepción a "por funcionalidad": ahí va la configuración
transversal. No lo uses como cajón para utilidades sueltas.

### La regla de la frontera

**Un módulo no usa el repositorio de otro módulo.** Si necesitas datos de otro módulo,
pasas por su `Service`.

```
PostService  →  UserService        ✔
PostService  →  UserRepository     ✘
```

Sin esto, los paquetes son carpetas y la separación no significa nada. Es la única regla
de modularidad que se aplica siempre y sin excepciones.

Para efectos secundarios entre módulos (notificar, registrar, reaccionar), prefiere
eventos de Spring a llamadas directas.

Las relaciones JPA entre módulos **sí están permitidas** (`AthleteDocument → Athlete`,
`Post → User`). No las elimines por purismo: el modelo de extracción previsto mantiene la
base de datos compartida, así que esas relaciones seguirían funcionando. Ver
`arquitectura.md` §1.

Si una tarea te obliga a cruzar una frontera de forma nueva, **dilo en el reporte final**
en lugar de hacerlo en silencio.

---

## Naming

Todo el dominio está **en inglés**. La documentación y los comentarios, en español.

| Elemento | Patrón real | Ejemplo |
|---|---|---|
| Entidad | Singular, PascalCase | `Athlete`, `CompetitionResult` |
| Repositorio | `<Entity>Repository` | `AthleteRepository` |
| Servicio | `<Entity>Service` | `AthleteService` |
| Controlador | `<Entity>Controller` | `AthleteController` |
| DTO entrada (CRUD) | `<Entity>Request` | `AthleteRequest`, `PostRequest` |
| DTO entrada (acción) | `<Acción>Request` | `GenerateKeyRequest`, `ChangeRoleRequest` |
| DTO salida | `<Entity>Response` | `AthleteResponse` |
| Filtros dinámicos | `<Entity>Specification` | `AthleteSpecification` |
| Enum | PascalCase | `Stroke`, `PostStatus`, `RoleName` |

**No uses `Create<Entity>Request` ni `Update<Entity>Request`.** El proyecto usa un único
`<Entity>Request` para crear y actualizar, salvo cuando la acción es específica
(`UserUpdateRequest`, `SignupWithRoleRequest`, `RedeemKeyRequest`).

---

## Specifications — cuidado especial

`AthleteSpecification`, `PostSpecification`, `UserSpecification` y
`CompetitionResultSpecification` implementan el filtrado dinámico con la Criteria API.

**Referencian los campos como cadenas de texto** (`root.get("birthDate")`). Consecuencia
directa:

- **Renombrar un campo de entidad no las actualiza y el proyecto compila igual.** El fallo
  aparece en tiempo de ejecución, solo cuando alguien usa ese filtro.
- Cualquier cambio en el nombre de un campo obliga a revisar la Specification de esa
  entidad **a mano**. No basta con el refactor del IDE ni con un grep del nombre Java.
- Al añadir un campo filtrable, añádelo también aquí.

Con un solo archivo de test en el proyecto, nada va a detectar una Specification rota
antes que un usuario.

---

## Identificadores y auditoría

- **UUID** en las entidades de dominio. `Role` usa `Long`, por ser catálogo sembrado.
- Campos de auditoría habituales: `createdAt`, `updatedAt`, y borrado lógico.
- No todas las entidades tienen borrado lógico. `AthleteDocument`, `PostImage`,
  `UserAthlete` y `AthleteInviteKey` solo llevan `createdAt`: su borrado es físico.
  Comprueba la entidad antes de asumirlo.

### Borrado lógico

- Un registro borrado lógicamente **no debe aparecer en consultas normales**. Filtra
  explícitamente en repositorios y Specifications nuevas.
- `Post` tiene además `status = DELETED`. Dos mecanismos solapados: verifica cuál manda
  antes de escribir lógica que dependa de ello, y pregunta si no queda claro.

---

## DTOs

- **Las entidades JPA no cruzan la frontera del controlador.** Nunca se serializan a JSON
  directamente. El proyecto ya tiene `Response` para todas las entidades expuestas: úsalos.
- Los DTOs de respuesta exponen solo lo necesario. En `AthleteResponse` esto importa:
  `dni` y la fecha de nacimiento no van en listados ni en respuestas que no los requieran.
- `passwordHash` (de `User`) y `keyValue` (de `AthleteInviteKey`) no aparecen en ningún
  DTO de salida. Nunca.
- Validación con Bean Validation en el DTO de entrada, no en el controlador.

---

## Errores

- `GlobalExceptionHandler` en `config/` es el punto único. **No añadas manejo de
  excepciones en controladores**; extiende el handler existente.
- Formato de error uniforme: mira el que ya devuelve antes de introducir otro.
- Los mensajes no filtran detalles internos: nada de excepciones originales, nombres de
  tabla ni SQL.
- Recurso que existe pero no pertenece al solicitante → **404**, nunca 403.

---

## Almacenamiento de archivos

Hay dos servicios separados: `ImageStorageService` (imágenes de posts) y
`DocumentStorageService` (documentos de atletas). **Mantenlos separados**: uno sirve
contenido público, el otro documentación de menores. No los unifiques ni compartas su
lógica de acceso.

Reglas:

- Los archivos se guardan con nombre interno opaco (UUID), no con el nombre original.
  Mantén ese patrón.
- **Nunca uses el nombre original como parte de una ruta.** Es entrada de usuario: path
  traversal directo.
- Toda descarga pasa por comprobación de permisos. Los documentos de atleta **nunca** por
  una URL pública directa.
- Valida tipo MIME y tamaño en la subida.

**`uploads/` está actualmente bajo control de versiones.** No debería estarlo. No añadas
archivos nuevos ahí mientras no se resuelva.

---

## Configuración

Coexisten `application.properties` y `application.yml`. **Antes de añadir una propiedad,
comprueba en cuál de los dos está la configuración equivalente** y no la dupliques: si la
misma clave aparece en ambos, la resolución de precedencia no es evidente y produce fallos
difíciles de diagnosticar.

Scripts SQL en `src/main/resources/`:

- `schema.sql` — esquema
- `data.sql` — catálogos estáticos, inserts idempotentes
- `data-test.sql` — datos de prueba, nunca en producción
- `seed.sh` en la raíz

Requiere `spring.jpa.defer-datasource-initialization=true`.

`AdminInitializer` crea el usuario administrador al arrancar. Si tocas roles o
autenticación, revisa que sigue funcionando.

---

## Logs

- Sin datos personales. Identifica entidades por UUID, no por nombre. Ver `rgpd.md` §4.
- Nunca `keyValue`, tokens JWT, `passwordHash` ni nombres de archivo originales.
- Nada de `System.out.println`.

---

## Frontend

**Está en un repositorio aparte.** Este repo es solo backend.

Lo que hay que tener presente desde aquí: **cualquier cambio en un DTO de respuesta es un
cambio de contrato de API** y rompe el frontend en silencio. Si modificas, renombras o
eliminas un campo de un `*Response`, dilo explícitamente en el reporte final para que se
actualice el otro repo.

Convenciones del frontend cuando se trabaje en él: React + Vite, archivos `.jsx` (no
conviertas a TypeScript), `allowJs: true` en `tsconfig.app.json`, y la URL de la API desde
`import.meta.env.VITE_API_URL`, nunca hardcodeada.

---

## Tests

**Estado real: solo existe `CnServiciesApplicationTests`.** No hay cobertura. Todo lo que
sigue es el objetivo, no una descripción de lo que hay.

- Lógica de negocio nueva: test unitario del servicio, como mínimo.
- Endpoints que devuelven datos de atletas: test de que un usuario sin vínculo
  `UserAthlete` no accede a ellos.
- **Specifications: test por cada campo filtrable.** Es donde los fallos se cuelan sin
  romper la compilación.
- Cuando exista multi-tenancy, todo endpoint necesitará su test de aislamiento entre
  clubes.
- Sin credenciales ni datos personales reales en fixtures.

Si una tarea añade lógica y no propones ningún test, dilo explícitamente en lugar de
omitirlo en silencio.

---

## Git

- Mensajes en español, imperativo, con contexto. No `fix`.
- Un commit por unidad lógica de cambio.
- No commitees `.env`, `target/`, ni archivos subidos por usuarios.

---

## Cosas que no se hacen sin preguntar

- Añadir una dependencia
- Cambiar el esquema de la base de datos
- Renombrar campos de entidad (arrastra Specifications y contrato de API)
- Modificar `SecurityConfig`, `JwtAuthFilter`, roles o autenticación
- Tocar `AthleteDocument` o `DocumentStorageService` más allá de lo pedido
- Cambiar cualquier `*Response` existente
- Refactorizar código fuera del alcance de la tarea
- Tocar Docker, `schema.sql`, `data.sql` o `seed.sh`