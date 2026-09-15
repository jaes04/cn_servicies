# Inventario de datos personales

Qué guarda el sistema, de quién, quién lo ve y dónde vive. Sacado del modelo real
—las entidades JPA, `SecurityConfig` y el guardián de acceso por objeto— y no de la
documentación, que en algunos puntos va por detrás del código.

> **Documento técnico de apoyo, no asesoramiento jurídico.** Sirve para que quien tenga
> que decidir la base legal y los plazos vea el material completo sin leer el código.
> Acompaña a `registro-actividades.md`, que es el documento del art. 30.

**Fecha de corte:** 15 de septiembre de 2026 · **Fuente:** rama `develop`

---

## 1. Resumen en una tabla

| Categoría de dato | Dónde | ¿De menores? | Sensibilidad |
|---|---|---|---|
| Identificación del deportista | `athletes` | **Sí, mayoritariamente** | Alta: incluye DNI y fecha de nacimiento |
| Identificación del tutor legal | `guardians` | No | Alta: DNI, email, teléfono |
| Consentimientos y su evidencia | `consents` | Referida a menores | **Crítica**: sostiene la licitud de todo lo demás |
| Aptitud médica | `medical_certificates` | Sí | **Art. 9** aunque minimizada a fechas |
| Papeles entregados al club | `document_deliveries` | Sí | Baja: solo tipo y fechas, sin el papel |
| Archivos subidos a la ficha | `athlete_documents` | Sí | **La más alta del sistema**, pero **la subida está apagada por defecto** |
| Asistencia a entrenamientos | `attendance` | Sí | Media: es un registro de presencia diaria de un menor |
| Marcas de competición | `competition_results` | Sí | Baja, pero identificable |
| Cuentas de acceso | `users` | Puede haberlas | Alta: credenciales |
| Contenido público | `posts`, `comments`, `post_images` | Posible | Variable: es lo único que sale del ámbito del club |

---

## 2. Ficha por entidad

Se marcan **en negrita** los campos que son dato personal. Los que no lo son se listan
igual, porque el conjunto es lo que identifica.

**Cómo se decide quién llega a qué**, y vale para todas las fichas de abajo:

- **Administrador del club:** a todo su club.
- **Entrenador:** a los grupos que lleva —como principal o como ayudante— y a sus
  sesiones; y a los deportistas que **hoy** están en alguno de esos grupos. Un nadador
  que dejó el grupo deja de ser visible en su ficha, aunque siga apareciendo en las
  listas de las sesiones en las que estuvo.
- **Tutor o cuenta de socio:** a los deportistas con los que tiene vínculo de acceso
  (`user_athletes`).
- Nadie llega a nada de otro club.

### 2.1 `athletes` — el deportista

| Campo | Tipo | Nota |
|---|---|---|
| `id` | UUID | Identificador interno, no derivado de ningún dato personal |
| `club_id` | UUID | Tenant. Con RLS activo |
| **`first_name`, `last_name`** | texto | |
| **`birth_date`** | fecha | Se usa para categoría deportiva, para saber si es menor de 14 y si necesita permiso de viaje |
| **`dni`** | texto (20) | Documento de identidad: DNI, NIE o pasaporte. **Opcional.** Único por club; sin documento, el duplicado se detecta por nombre, apellidos y fecha de nacimiento |
| `gender_id` | catálogo | `MALE` / `FEMALE` |
| `created_at`, `updated_at`, `deleted_at` | fecha/hora | `deleted_at` es **borrado lógico: la fila sigue con nombre y DNI** |

**Quién accede:** el administrador a todos; el entrenador, a los que hoy están en sus
grupos, y puede dar de alta fichas nuevas; solo el administrador da de baja. Un tutor no
llega a esta ruta: ve a los suyos por `/api/athlete-links/my-tutees`.

### 2.2 `guardians` y `athlete_guardians` — el tutor legal

| Campo | Tipo | Nota |
|---|---|---|
| **`first_name`, `last_name`, `dni`, `email`** | texto | `dni` y `email` obligatorios |
| **`phone`** | texto | Opcional |
| `user_id` | UUID | **Opcional**: el tutor es una persona, no necesariamente una cuenta |
| `relationship` | enum | En el vínculo, no en la persona: `MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` |

**Quién accede:** solo el administrador.

### 2.3 `consents` — el consentimiento

Es la pieza que sostiene la licitud del tratamiento de un menor.

| Campo | Tipo | Nota |
|---|---|---|
| `type` | enum | `DATA_PROCESSING`, `IMAGE`, `COMMUNICATIONS`, `HEALTH_DATA`. **Separados y revocables por separado** |
| `granted` | booleano | **Una negativa se guarda igual que una concesión** |
| `decision_date` | fecha | La edad se mide aquí, no en la fecha de hoy |
| `evidence_type` | enum | `PAPER_FORM`, `ONLINE_FORM`, `EMAIL` |
| `evidence_ref` | texto (100) | Referencia al soporte |
| **`source_ip`** | texto (45) | **Solo se rellena con `ONLINE_FORM`**: en papel o correo no prueba nada y es dato personal |
| `revoked_at` | fecha/hora | Revocar es escribir aquí |

**Registro append-only:** nada se actualiza ni se borra. Volver a consentir es una fila
nueva. El historial completo es la prueba del art. 7.1.

**El derecho de imagen firmado al darse de alta se registra aquí**, como consentimiento
`IMAGE` con evidencia en papel, y no como papel entregado.

**Quién accede:** el **estado** por finalidad lo ve también el entrenador de los
deportistas de sus grupos —lo necesita antes de publicar una foto—; el **historial**,
quién firmó y con qué papel, solo el administrador.

### 2.4 `medical_certificates` — la aptitud

| Campo | Tipo | Nota |
|---|---|---|
| `season_id` | UUID | La temporada que cubre: el club pide uno por curso |
| `issued_on`, `expires_on` | fecha | La caducidad es el final de la temporada, no se teclea |
| `validated_by_id`, `validated_at` | UUID / fecha | Quién dio el papel por bueno |

**No hay campo `apto`, ni diagnóstico, ni observaciones.** La existencia de un certificado
en plazo *es* la aptitud. El estado (`VALID`, `EXPIRING_SOON`, `EXPIRED`, `MISSING`) **se
calcula, no se almacena**, y se mide contra la temporada activa: el certificado de otro
curso no cubre este.

**Quién accede:** el entrenador ve el **estado** de los deportistas de sus grupos, porque
lo necesita antes de meterlos al agua; las fechas y quién validó, solo el administrador.

### 2.5 `athlete_documents` — archivos subidos, apagados por defecto

| Campo | Tipo | Nota |
|---|---|---|
| **`title`** | texto | **Texto libre.** Vía directa a que alguien escriba un diagnóstico donde no debe |
| `type` | enum | `MEDICAL`, `TRAINING`, `COMPETITION`, `CONSENT`, `IDENTIFICATION`, `OTHER` |
| `filename` | texto | Nombre interno opaco (UUID) |
| **`original_filename`** | texto | El nombre que puso quien lo subió. Suele ser descriptivo: `informe-cardiologia-maria.pdf` |
| `uploaded_by_id` | UUID | |

**Desde el bloque 3a la subida está apagada por defecto** (`app.documents.upload.enabled`).
Apagada, subir, listar y descargar contestan 404 a todo el mundo, administrador incluido.
Para el despliegue inicial el club no sube papeles: registra su entrega en
`document_deliveries` (§2.12).

**No se ha eliminado, se ha apagado**, para que un club que lo necesite pueda
encenderlo. Pero encendido vuelve a ser el punto más expuesto del sistema: el archivo
vive en disco, **sin cifrado en reposo**, en el mismo directorio que las imágenes del
blog y **sin registro de accesos**. Con tipo `MEDICAL` puede contener datos de salud de
menores. Eso hay que resolverlo antes de encenderlo.

**Quién accede, si está encendida:** subir y abrir el archivo, el administrador, el
entrenador de un grupo en el que hoy está el deportista, o quien tenga vínculo con él;
borrar, solo el administrador.

### 2.6 `attendance` y `training_sessions` — la asistencia

| Campo | Tipo | Nota |
|---|---|---|
| `status` | enum | `PRESENT`, `ABSENT`, `EXCUSED`, `LATE` |
| `registered_by_id`, `registered_at` | UUID / fecha-hora | Quién pasó lista y cuándo |

**No hay campo de observaciones, y es deliberado:** un texto libre en el registro de
asistencia de un menor acaba conteniendo *"no vino, está con gastroenteritis"*. `EXCUSED`
tampoco guarda el motivo.

**Genera un registro diario de presencia de un menor durante toda la temporada.** Es el
dato de mayor volumen del sistema y el que más tiempo cubre.

**Quién accede:** el administrador, y el entrenador principal o ayudante **del grupo de esa
sesión**. Solo ellos pasan lista.

### 2.7 `athlete_groups` y `group_assistant_coaches` — pertenencia y entrenadores

`athlete_groups`: `joined_on`, `left_on` y `leave_reason` (`END_OF_SEASON`,
`GROUP_CHANGE`, `LEFT_CLUB`, `OTHER`). **No hay valor de lesión, y es una decisión de
protección de datos**, no una constante que falte. Nunca se borra: la baja se marca.

`group_assistant_coaches` y la columna `training_groups.coach_id` dicen quién lleva cada
grupo. **No son datos de menores, pero deciden quién accede a ellos:** estar ahí da acceso
a las sesiones, a la asistencia y a las fichas de los deportistas del grupo. Por eso solo
se puede asignar a personal técnico o a un administrador.

### 2.8 `competition_results` — las marcas

Fecha, distancia, estilo, longitud de vaso y tiempo. Sin datos personales propios más allá
del vínculo con el atleta, pero **identifica a un menor y tiene valor histórico para el
club**, lo que choca con el derecho de supresión.

**Quién accede:** el administrador a todas; el entrenador, a las de los deportistas de sus
grupos; el propio usuario ve las suyas por `/me`.

### 2.9 `users` — las cuentas

| Campo | Tipo | Nota |
|---|---|---|
| **`username`** | texto | Único **por club** |
| **`email`** | texto | **Único global**: hoy una persona no puede usar el mismo correo en dos clubes |
| `password_hash` | texto | BCrypt. **Factor de coste por defecto (10); subirlo a ≥12 está pendiente** |
| `blocked` | booleano | |
| **`profile_photo`** | texto | Archivo en el mismo directorio que los documentos. Solo lo cambia su dueño o el administrador |
| `roles` | N:M | `ROLE_ADMIN`, `ROLE_EDITOR`, `ROLE_USER`, `ROLE_TECHNICAL_STAFF` |

### 2.10 `posts`, `comments`, `post_images` — lo público

**Es lo único del sistema accesible sin autenticación** (`GET /api/posts/published/**` y
las imágenes). El contenido lo teclea el club: si publica el nombre o la foto de un menor,
el dato sale del ámbito interno y ahí es donde importa el consentimiento de `IMAGE`.

`comments` tiene `blocked`, lo que implica moderación, y su autor puede ser un menor con
cuenta.

### 2.11 `athlete_invite_keys` — el vínculo

`key_value` es un UUID con caducidad de 72 h y un solo uso. **Un fallo aquí da acceso a los
datos de un menor a quien no debe.** No aparece en logs, listados ni URLs registradas. La
genera el administrador, o el entrenador para los deportistas de sus grupos.

### 2.12 `document_deliveries` — papeles entregados al club

Constancia de que la familia entregó un papel y de hasta cuándo vale. **El papel se queda
en el club**, que es quien lo custodia como responsable del tratamiento.

| Campo | Tipo | Nota |
|---|---|---|
| `type` | enum | `LICENSE_APPLICATION`, `IDENTITY_DOCUMENT`, `TRAVEL_PERMIT` |
| `season_id` | UUID | Solo en la solicitud de licencia, que vale por temporada |
| **`valid_from`** | fecha | Solo en el permiso de viaje: el día de salida |
| **`valid_until`** | fecha | Caducidad del documento de identidad, o día de vuelta del viaje |
| `delivered_on` | fecha | Día en que se entregó |
| `registered_by_id`, `registered_at` | UUID / fecha-hora | Quién lo anotó y cuándo |

**Sin número de documento, sin destino del viaje y sin campo de notas.** El número ya está
en la ficha del deportista, y para saber si un permiso cubre unas fechas no hace falta
saber adónde se viaja.

**Faltan dos tipos a propósito.** El certificado médico no está aquí: es dato de salud y
tiene su propia tabla, con sus propios permisos (§2.4). Y el derecho de imagen tampoco:
es un consentimiento (§2.3).

**El permiso de viaje solo se admite para menores de edad el día de salida.** El de un
adulto no sirve para nada, y guardarlo sería guardar sus fechas de viaje sin motivo.

El estado (`VALID`, `EXPIRING_SOON`, `EXPIRED`, `MISSING`, `NOT_REQUIRED`) **se calcula,
no se almacena**. La licencia se mide contra la temporada activa y el documento de
identidad contra la fecha de hoy. El permiso de viaje no tiene estado general: se pregunta
si cubre las fechas de un viaje concreto.

**Quién accede:** el entrenador ve el estado de los deportistas de sus grupos y si pueden
viajar; las fechas, el historial y el registro de entregas son del administrador.

---

## 3. Dónde vive todo

| Soporte | Contenido | Cifrado |
|---|---|---|
| PostgreSQL | Todo lo anterior salvo archivos | **No, hoy** |
| Directorio `app.upload.dir` | Fotos de perfil e imágenes del blog. Documentos de atleta solo si se enciende la subida | **No** |
| Logs de aplicación | Identificadores (UUID), nunca nombres. Queda rastro de quién exporta un CSV | n/a |
| Exportaciones CSV | Nombre del atleta y números de asistencia. Ni DNI ni fecha de nacimiento | **Sale del sistema y no vuelve** |
| Archivo en papel del club | Licencias, copias del documento de identidad, permisos de viaje, certificados | Fuera del sistema: custodia del club |

**Los documentos de atleta y las imágenes públicas del blog comparten directorio.** Con la
subida apagada no hay documentos nuevos, pero si algún día se enciende hay que separarlos
antes.

---

## 4. Quién ve qué, en una tabla

| | Administrador | Entrenador | Tutor / socio | Anónimo |
|---|---|---|---|---|
| Ficha del atleta (con DNI) | Sí | Solo de sus grupos, hoy | Solo los suyos, sin DNI | No |
| Documentos subidos (apagado por defecto) | Sí | Solo de sus grupos, hoy | Solo los de los suyos | No |
| Papeles entregados | Sí, con fechas | Solo el estado y el permiso de viaje, de sus grupos | No | No |
| Estado de consentimiento | Sí | Solo de sus grupos, hoy | No | No |
| Historial de consentimiento | Sí | No | No | No |
| Certificado médico | Sí, con fechas | Solo el estado, de sus grupos | No | No |
| Asistencia, listas e informes | Sí | Solo de sus grupos | No | No |
| Cuentas de usuario | Sí | La propia | La propia | No |
| Blog publicado | Sí | Sí | Sí | **Sí** |

---

## 5. Puntos abiertos con consecuencia legal

Cada uno necesita una decisión que no es técnica:

1. **Los `athlete_documents` que ya existan.** La subida está apagada, así que no entran
   más, pero los que ya estuvieran siguen en disco y en la base. Hay que decidir si se
   borran, y si algún día se vuelve a encender la subida.
2. **Documento de identidad opcional: hecho.** Admite DNI, NIE o pasaporte y puede quedar
   vacío. Sin documento, el duplicado se detecta por nombre, apellidos y fecha de
   nacimiento.
3. **Nada se borra automáticamente.** No hay ningún plazo de conservación definido ni
   aplicado. El borrado lógico deja nombre y DNI en la fila. Incluye ahora los papeles
   entregados: ¿cuánto se conserva la constancia de un permiso de viaje ya pasado?
4. **No hay registro de auditoría.** Hoy no se puede responder quién abrió la ficha de un
   menor. Solo queda rastro en el log de quién exportó un CSV.
5. **No hay registro de eventos de autenticación**, así que tampoco se detecta un acceso
   indebido a posteriori.
6. **La IP del consentimiento online se conserva sin plazo.**
7. **Fotocopias del documento de identidad.** Si el club las archiva en papel, conviene
   confirmar si hace falta guardarlas o basta con comprobarlas: el sistema solo registra
   que se vieron y hasta cuándo valen.
8. **Custodia del papel.** El club guarda los originales. Conviene que el contrato de
   encargo deje claro que ese archivo es del club y no de la plataforma.
