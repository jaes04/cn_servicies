-- ============================================================
--  SCHEMA - cn_servicies
-- ============================================================
--
--  LEE ESTO ANTES DE ANADIR UNA TABLA
--  ----------------------------------
--  Con `defer-datasource-initialization=true`, este archivo se ejecuta DESPUES
--  de que Hibernate haya creado el esquema con `ddl-auto=update`. Cuando llega
--  aqui, las tablas YA EXISTEN, asi que:
--
--      *** TODO `CREATE TABLE IF NOT EXISTS` DE ESTE ARCHIVO NO HACE NADA. ***
--
--  Y con el se pierde en silencio todo lo que va DENTRO: las claves foraneas
--  con su `ON DELETE`, las restricciones `CONSTRAINT ... UNIQUE`, los `DEFAULT`.
--  Lo que queda en la base es lo que Hibernate deduce de las anotaciones, que no
--  lleva accion de borrado y nombra las restricciones con cadenas generadas
--  distintas en cada base.
--
--  Medido en septiembre de 2026: este archivo declara 24 `ON DELETE` y la base
--  tiene 8 —los de las cinco tablas mas antiguas, creadas cuando este archivo
--  todavia ganaba la carrera—; declara 25 `DEFAULT` y hay 11.
--
--  LO QUE SI SE APLICA son las sentencias sueltas, porque no dependen de que la
--  tabla exista o no: `CREATE INDEX IF NOT EXISTS` y `ALTER TABLE`. Por eso los
--  indices unicos de este archivo si estan en la base y las cascadas no.
--
--  REGLA, entonces: si algo tiene que existir de verdad, escribelo como
--  sentencia suelta despues del CREATE TABLE. Los `CREATE TABLE` se conservan
--  porque documentan la forma de la tabla y sirven para levantarla en una base
--  donde Hibernate no haya pasado, pero NO son la fuente de la verdad.
--
--  Recrear la base no cambia nada: en una base vacia Hibernate sigue yendo
--  primero. Esto se arregla escribiendo sentencias sueltas, o sacando el esquema
--  de `ddl-auto` (decision abierta en el roadmap).
-- ============================================================

-- Lo ejecuta la aplicacion al arrancar, con su propio usuario, sujeto a las
-- policies de Row Level Security. Los UPDATE del backfill de club_id no verian
-- ninguna fila sin fijar antes `app.club_id`. Se restablece al final.
SELECT set_config('app.club_id', 'public', false);

-- CLUBS  (tenant raiz: toda entidad acabara colgando de un club)
CREATE TABLE IF NOT EXISTS clubs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP
);

-- ROLES
CREATE TABLE IF NOT EXISTS roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- USERS
CREATE TABLE IF NOT EXISTS users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    profile_photo VARCHAR(255),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    deleted_at    TIMESTAMP
);

-- USER_ROLES (join table)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ATHLETES
CREATE TABLE IF NOT EXISTS athletes (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    birth_date DATE         NOT NULL,
    dni        VARCHAR(20),
    gender     VARCHAR(10)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

-- POSTS
CREATE TABLE IF NOT EXISTS posts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    slug         VARCHAR(255) UNIQUE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP,
    author_id    UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    deleted_at   TIMESTAMP
);

-- POST_IMAGES
CREATE TABLE IF NOT EXISTS post_images (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    filename          VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    post_id           UUID         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at        TIMESTAMP
);

-- COMMENTS
CREATE TABLE IF NOT EXISTS comments (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    content    TEXT      NOT NULL,
    post_id    UUID      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id  UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- COMPETITION_RESULTS
CREATE TABLE IF NOT EXISTS competition_results (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id          UUID        NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    competition_date    DATE        NOT NULL,
    distance_meters     INTEGER     NOT NULL,
    stroke              VARCHAR(20) NOT NULL,
    pool_length         INTEGER     NOT NULL,
    result_time_millis  BIGINT      NOT NULL,
    partial             BOOLEAN     NOT NULL DEFAULT FALSE,
    final_result_id     UUID        REFERENCES competition_results(id) ON DELETE SET NULL,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- ATHLETE_DOCUMENTS
CREATE TABLE IF NOT EXISTS athlete_documents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    filename          VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    athlete_id        UUID         NOT NULL,
    -- SIN ON DELETE, y es deliberado (tarea 2.6). Este archivo declaraba CASCADE,
    -- que habria borrado los documentos de un atleta al dar de baja al usuario
    -- que los subio: el documento es del atleta, no de quien lo subio. Tampoco
    -- vale SET NULL porque la columna es NOT NULL. Arreglarlo de verdad es
    -- hacerla nullable, y eso es un cambio de modelo que hay que decidir aparte.
    uploaded_by_id    UUID         NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
--  TUTORES (tarea S.1.a)
-- ============================================================
--  Nacen con club_id NOT NULL: la regla es que toda entidad nueva lo lleve
--  desde el principio, asi que aqui no hay backfill que hacer.
--
--  guardians es entidad raiz —un tutor pertenece al club, no cuelga de un
--  atleta, porque puede tener varios hijos en el mismo club— y por eso lleva
--  club_id y policy de RLS propia. athlete_guardians es tabla hija y llega a
--  su club por cualquiera de sus dos padres.
-- ============================================================

CREATE TABLE IF NOT EXISTS guardians (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID         NOT NULL REFERENCES clubs(id),
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    dni        VARCHAR(20)  NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(255),
    user_id    UUID,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_guardians_club_id ON guardians (club_id);

-- El dni del tutor es unico por club, no global: la misma persona puede ser
-- tutora en dos clubes y cada uno tiene su ficha.
CREATE UNIQUE INDEX IF NOT EXISTS uk_guardians_club_dni ON guardians (club_id, dni);

-- El documento del tutor admite NIE y pasaporte, como el del atleta (bloque 3c).
-- Sigue siendo obligatorio. ddl-auto no cambia la longitud de una columna que ya
-- existe, asi que va aqui.
ALTER TABLE guardians ALTER COLUMN dni TYPE VARCHAR(20);

-- Una cuenta corresponde a una sola ficha de tutor. El indice unico de Postgres
-- ignora los nulos, asi que esto no estorba a los tutores sin cuenta, que son
-- la mayoria al principio.
CREATE UNIQUE INDEX IF NOT EXISTS uk_guardians_user ON guardians (user_id);

CREATE TABLE IF NOT EXISTS athlete_guardians (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id   UUID        NOT NULL,
    guardian_id  UUID        NOT NULL,
    relationship VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP
);

-- Un atleta y un tutor se vinculan una sola vez. Estaba declarado dentro del
-- CREATE TABLE y por tanto no existia: en la base solo habia el unico que genero
-- Hibernate, con nombre aleatorio. Como indice suelto si se crea.
CREATE UNIQUE INDEX IF NOT EXISTS uk_athlete_guardians
    ON athlete_guardians (athlete_id, guardian_id);

CREATE INDEX IF NOT EXISTS idx_athlete_guardians_athlete ON athlete_guardians (athlete_id);
CREATE INDEX IF NOT EXISTS idx_athlete_guardians_guardian ON athlete_guardians (guardian_id);

-- CONSENTIMIENTOS
--  Registro append-only: ni se actualiza ni se borra. Revocar es escribir
--  revoked_at, y volver a consentir es una fila nueva. Por eso no hay unico
--  sobre (athlete_id, type): el historial completo es la prueba.
--
--  Lleva club_id aunque se llegue por el atleta, apartandose del criterio de
--  la 0.2 para tablas hijas. Es lo que sostiene la licitud del tratamiento de
--  un menor: que el aislamiento lo imponga Postgres y no la confianza en que
--  toda consulta futura pase por el atleta.
CREATE TABLE IF NOT EXISTS consents (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID        NOT NULL REFERENCES clubs(id),
    athlete_id    UUID        NOT NULL,
    guardian_id   UUID        NOT NULL REFERENCES guardians(id),
    type          VARCHAR(30) NOT NULL,
    granted       BOOLEAN     NOT NULL,
    decision_date DATE        NOT NULL,
    evidence_type VARCHAR(20) NOT NULL,
    evidence_ref  VARCHAR(100),
    source_ip     VARCHAR(45),
    revoked_at    TIMESTAMP,
    created_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_consents_club_id ON consents (club_id);

-- La consulta que mas se hace es "¿tiene este atleta consentimiento vigente
-- para esta finalidad?", y va por las dos columnas a la vez.
CREATE INDEX IF NOT EXISTS idx_consents_athlete_type ON consents (athlete_id, type);
CREATE INDEX IF NOT EXISTS idx_consents_guardian ON consents (guardian_id);

-- guardian_id sin ON DELETE CASCADE, a proposito y a diferencia de athlete_id:
-- el tutor tiene borrado logico y no se borra nunca fisicamente, asi que la
-- restriccion es la red que avisaria si alguien lo intentara.

-- CERTIFICADOS MEDICOS (tarea S.1.b, opcion A: solo metadatos)
--  Sin veredicto, sin diagnostico, sin campo `apto` y sin texto libre: si hay
--  certificado en plazo, esa es la aptitud. El estado no se almacena, se
--  calcula desde expires_on.
--
--  Por temporada desde el bloque 3b: el club pide un certificado cada curso,
--  asi que lleva season_id y expires_on es el final de la temporada. La columna
--  se añade y se rellena con sentencias sueltas, junto a sus claves foraneas.
--
--  Lleva club_id y policy propia, misma decision explicita que en consents.
CREATE TABLE IF NOT EXISTS medical_certificates (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID      NOT NULL REFERENCES clubs(id),
    athlete_id       UUID      NOT NULL,
    season_id        UUID      NOT NULL,
    issued_on        DATE      NOT NULL,
    expires_on       DATE      NOT NULL,
    validated_by_id  UUID      NOT NULL REFERENCES users(id),
    validated_at     TIMESTAMP NOT NULL,
    created_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_medical_certificates_club_id ON medical_certificates (club_id);

-- Las dos consultas reales: el vigente de un atleta —el que mas lejos caduca—
-- y los que vencen pronto en todo el club.
CREATE INDEX IF NOT EXISTS idx_medical_certificates_athlete
    ON medical_certificates (athlete_id, expires_on DESC);
CREATE INDEX IF NOT EXISTS idx_medical_certificates_expires
    ON medical_certificates (expires_on);

-- TEMPORADAS (tarea 1.1)
--  Sin borrado: el historico por temporada es el motivo de que la tabla exista.
--  Una temporada terminada se queda con active = false.
CREATE TABLE IF NOT EXISTS seasons (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID        NOT NULL REFERENCES clubs(id),
    name       VARCHAR(50) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_seasons_club_id ON seasons (club_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_seasons_club_name ON seasons (club_id, name);

-- Solo una activa por club, impuesto por la base y no solo por el servicio.
-- Indice unico PARCIAL: la restriccion aplica a las filas con active = true, y
-- las apagadas pueden ser tantas como haga falta. Un unique normal sobre
-- (club_id, active) dejaria tener una sola temporada pasada, que es absurdo.
CREATE UNIQUE INDEX IF NOT EXISTS uk_seasons_club_active
    ON seasons (club_id) WHERE active;

-- GRUPOS DE ENTRENAMIENTO (tarea 1.2)
--  La tabla NO se llama `group`: es palabra reservada de SQL. Tampoco `groups`,
--  que aunque Postgres lo admite arrastra problemas en la gramatica de HQL.
--
--  Cuelga de la temporada porque entrenador, horario y composicion cambian cada
--  año: "Alevin A" de este curso y el del anterior son dos filas distintas, y
--  eso es lo que conserva el historico.
CREATE TABLE IF NOT EXISTS training_groups (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID         NOT NULL REFERENCES clubs(id),
    season_id  UUID         NOT NULL REFERENCES seasons(id),
    coach_id   UUID,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(20)  NOT NULL,
    level      VARCHAR(20)  NOT NULL,
    max_slots  INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_training_groups_club_id ON training_groups (club_id);
CREATE INDEX IF NOT EXISTS idx_training_groups_season ON training_groups (season_id);

-- Dos grupos con el mismo nombre en la misma temporada son un error. Entre
-- temporadas distintas se repite siempre, que es justo el caso normal.
CREATE UNIQUE INDEX IF NOT EXISTS uk_training_groups_season_name
    ON training_groups (season_id, name);

-- coach_id con ON DELETE SET NULL: dar de baja a un entrenador no puede
-- llevarse por delante el grupo ni su historico. El grupo se queda sin
-- entrenador, que es un estado valido.

-- PERTENENCIA A GRUPO (tarea 1.3)
--  Historico: las filas no se borran nunca. Dar de baja es escribir left_on, y
--  left_on es el ULTIMO DIA DE PERTENENCIA, incluido.
--
--  Sin club_id: tabla hija, llega a su club por athlete_id o por group_id, las
--  dos filtradas. Es la regla de la 0.2, que ya nombraba esta tabla.
CREATE TABLE IF NOT EXISTS athlete_groups (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id   UUID        NOT NULL,
    group_id     UUID        NOT NULL,
    joined_on    DATE        NOT NULL,
    left_on      DATE,
    leave_reason VARCHAR(20),
    created_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_athlete_groups_athlete ON athlete_groups (athlete_id);

-- La consulta de la Fase 2 —miembros de un grupo en una fecha— va por estas
-- tres columnas.
CREATE INDEX IF NOT EXISTS idx_athlete_groups_group_fechas
    ON athlete_groups (group_id, joined_on, left_on);

-- No dos pertenencias ABIERTAS del mismo atleta al mismo grupo. Parcial, igual
-- que el de la temporada activa: las cerradas pueden repetirse tantas veces
-- como el atleta entre y salga del grupo a lo largo de los años, que es un
-- historico legitimo y no un duplicado.
--
-- Ojo: esto NO impide pertenecer a varios grupos a la vez, que es un caso real
-- (natacion y preparacion fisica). La restriccion es por grupo, no por atleta.
CREATE UNIQUE INDEX IF NOT EXISTS uk_athlete_groups_abierta
    ON athlete_groups (athlete_id, group_id) WHERE left_on IS NULL;

-- HORARIO RECURRENTE DEL GRUPO (tarea 2.1)
--  Plantilla, no entrenamiento: no tiene fecha, tiene dia de la semana. De aqui
--  materializa la 2.2 las sesiones.
--
--  Varios por grupo: lunes, miercoles y viernes son tres filas, y el martes de
--  seco es una cuarta. La modalidad vive en el horario y no en el grupo para no
--  tener que partir "Alevin A" en dos grupos con los mismos nadadores.
--
--  Sin club_id: tabla hija, llega a su club por group_id, que si esta bajo
--  policy. La contrapartida es que un SELECT por id de esta tabla no lo tapa
--  nada, y por eso la API solo la expone colgada del grupo.
--
--  valid_until es el ULTIMO DIA de vigencia, incluido. Mismo criterio que
--  athlete_groups.left_on, y por el mismo motivo: en la 2.2 el error de un dia
--  se multiplica por cada semana generada.
CREATE TABLE IF NOT EXISTS group_schedules (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID        NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    modality    VARCHAR(20) NOT NULL,
    valid_from  DATE        NOT NULL,
    valid_until DATE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- El generador de sesiones de la 2.2 pregunta por grupo y por dia de la semana.
CREATE INDEX IF NOT EXISTS idx_group_schedules_group ON group_schedules (group_id);
CREATE INDEX IF NOT EXISTS idx_group_schedules_group_dia
    ON group_schedules (group_id, day_of_week);

-- No hay indice unico que impida el solape de dos horarios del mismo grupo:
-- solaparse no es coincidir, y comprobarlo en la base pediria una restriccion de
-- exclusion con btree_gist. Una extension que exige superusuario en cada
-- despliegue es desproporcionada para prevenir un error de tecleo — misma
-- decision que con el solape de temporadas (1.1). Lo valida el servicio.

-- SESIONES DE ENTRENAMIENTO (tarea 2.2)
--  Lo que el horario materializa: el martes 14 de octubre de 18:00 a 19:00.
--
--  LLEVA club_id AUNQUE SEA TABLA HIJA, y es una excepcion deliberada a la regla,
--  como consents. Es la primera tabla hija cuyo id viaja solo en la API
--  (/api/sessions/{id}, y en la 2.3 el /roster que consume el movil): un id
--  suelto se resuelve por clave primaria, que es donde el filtro de Hibernate no
--  llega y RLS si. Sin policy, un findById devolveria la sesion de otro club.
--
--  La hora y la modalidad estan COPIADAS del horario, no leidas de el: si el
--  horario cambia en marzo, las sesiones de febrero tienen que seguir diciendo
--  la hora a la que se entreno de verdad.
--
--  Sin zona horaria: DATE y TIME sueltos. Un entrenamiento a las 18:00 es a las
--  18:00 tambien el fin de semana en que cambia la hora.
CREATE TABLE IF NOT EXISTS training_sessions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id             UUID        NOT NULL REFERENCES clubs(id),
    group_id            UUID        NOT NULL,
    schedule_id         UUID,
    session_date        DATE        NOT NULL,
    start_time          TIME        NOT NULL,
    end_time            TIME        NOT NULL,
    modality            VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancellation_reason VARCHAR(30),
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_training_sessions_club_id ON training_sessions (club_id);

-- El calendario de un grupo en un rango: es la consulta de todos los listados y
-- la que usara el generador para saber que hay ya.
CREATE INDEX IF NOT EXISTS idx_training_sessions_group_fecha
    ON training_sessions (group_id, session_date);

-- IDEMPOTENCIA. Es lo que impide que el job, al pasar dos veces por la misma
-- semana, deje dos sesiones donde hay una. Si el generador duplica, la
-- asistencia queda inconsistente y el club deja de fiarse del sistema entero.
--
-- Con schedule_id NULL no aplica, y eso es lo que se quiere: en Postgres dos
-- nulos no son iguales, asi que las sesiones puntuales —una competicion y un
-- entrenamiento extra el mismo dia— se pueden repetir libremente.
CREATE UNIQUE INDEX IF NOT EXISTS uk_training_sessions_horario_fecha
    ON training_sessions (schedule_id, session_date);

-- schedule_id con ON DELETE SET NULL: borrar un horario no puede llevarse por
-- delante las sesiones que ya ocurrieron. La sesion se queda huerfana de
-- horario, que es exactamente lo que paso.

-- ASISTENCIA (tarea 2.3)
--  Que hizo cada atleta en cada sesion. Sin club_id: se entra siempre por la
--  sesion, que si tiene policy, igual que los horarios se entra por su grupo.
--
--  SIN CAMPO DE OBSERVACIONES, y es deliberado. El roadmap lo pedia; un texto
--  libre en el registro de un menor, visible para todo el personal tecnico,
--  acaba guardando datos de salud sin base legal. Ver docs/rgpd.md §9.
--
--  OJO: las restricciones de esta tabla van FUERA del CREATE TABLE, como
--  sentencias sueltas. Dentro no se aplicarian —lee la cabecera del archivo— y
--  aqui no es cosmetico: la clave foranea hacia las sesiones NECESITA su
--  cascada, porque la regeneracion de la 2.2.b borra sesiones futuras de verdad
--  y sin ella ese borrado empieza a fallar en cuanto tengan lista pasada.
CREATE TABLE IF NOT EXISTS attendance (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID        NOT NULL,
    athlete_id       UUID        NOT NULL,
    status           VARCHAR(20) NOT NULL,
    registered_by_id UUID,
    registered_at    TIMESTAMP   NOT NULL
);

-- Un atleta, una fila por sesion. Es lo que sostiene el upsert con ON CONFLICT
-- del guardado en lote: sin este indice, dos entrenadores pasando lista a la vez
-- dejarian dos filas del mismo nadador con estados distintos.
CREATE UNIQUE INDEX IF NOT EXISTS uk_attendance_sesion_atleta
    ON attendance (session_id, athlete_id);

-- El roster pregunta por sesion; los informes de la 2.4, por atleta.
CREATE INDEX IF NOT EXISTS idx_attendance_session ON attendance (session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_athlete ON attendance (athlete_id);

-- Claves foraneas como ALTER sueltos, que es la unica forma de que existan de
-- verdad. El par DROP IF EXISTS + ADD las hace idempotentes.
--
-- ON DELETE CASCADE hacia la sesion: al regenerar un horario se borran sus
-- sesiones futuras, y la lista de una sesion que ya no existe no significa nada.
-- Lo mismo hacia el atleta.
--
-- registered_by_id con SET NULL: que un entrenador deje el club no puede
-- llevarse por delante el registro de asistencia que hizo.
ALTER TABLE attendance DROP CONSTRAINT IF EXISTS fk_attendance_session;
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_session
    FOREIGN KEY (session_id) REFERENCES training_sessions (id) ON DELETE CASCADE;

ALTER TABLE attendance DROP CONSTRAINT IF EXISTS fk_attendance_athlete;
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE attendance DROP CONSTRAINT IF EXISTS fk_attendance_registered_by;
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_registered_by
    FOREIGN KEY (registered_by_id) REFERENCES users (id) ON DELETE SET NULL;

-- CALENDARIO DE EXCEPCIONES (tarea 2.2.b)
--  Los dias en que no se entrena: festivos, piscina cerrada, la semana de
--  Navidad.
--
--  Entidad raiz —el cierre es del club, no de un grupo— asi que club_id y policy
--  propia, esta vez sin excepcion que justificar.
--
--  UN CIERRE NO IMPIDE GENERAR: hace que la sesion nazca cancelada. Un dia sin
--  nada en el calendario no distingue un festivo de un job que no llego a pasar
--  por esa semana.
--
--  modality NULL afecta a todo; con valor, solo a esa. Es lo que evita que
--  cerrar la piscina cancele el entrenamiento del gimnasio.
CREATE TABLE IF NOT EXISTS club_closures (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID        NOT NULL REFERENCES clubs(id),
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    reason     VARCHAR(30) NOT NULL,
    modality   VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_club_closures_club_id ON club_closures (club_id);

-- El generador pregunta por rango en cada pasada.
CREATE INDEX IF NOT EXISTS idx_club_closures_fechas
    ON club_closures (club_id, start_date, end_date);

-- Sin unico: dos cierres solapados no son un error. Declarar el puente y ademas
-- la semana entera es una forma legitima de decirlo, y cancelar dos veces la
-- misma sesion no hace nada la segunda.


-- Un usuario se vincula una sola vez con un atleta. La tabla la crea Hibernate y
-- este archivo no la declaraba, asi que el unico solo existia con el nombre que
-- Hibernate le puso. Como indice suelto tiene nombre estable.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_athletes
    ON user_athletes (user_id, athlete_id);


-- ============================================================
--  CLAVES FORANEAS CON ACCION DE BORRADO (tarea 2.6)
-- ============================================================
--  Van aqui, como sentencias sueltas, y NO dentro del CREATE TABLE, porque ahi
--  no se aplicarian nunca: lee la cabecera del archivo. Durante mucho tiempo
--  este archivo declaro 24 `ON DELETE` de los que la base solo tenia 8.
--
--  Las entidades correspondientes llevan
--  `@JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))` para
--  que Hibernate no cree ADEMAS la suya: con dos claves foraneas sobre la misma
--  columna manda la mas restrictiva, y la cascada se quedaria de adorno.
--
--  Las que Hibernate ya creo en bases existentes hay que quitarlas a mano:
--      migrations/2.6-limpiar-fks-generadas.sql
--
--  El par DROP IF EXISTS + ADD hace que esto sea idempotente.
-- ============================================================

ALTER TABLE athlete_documents DROP CONSTRAINT IF EXISTS fk_athlete_documents_athlete;
ALTER TABLE athlete_documents ADD CONSTRAINT fk_athlete_documents_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE guardians DROP CONSTRAINT IF EXISTS fk_guardians_user;
ALTER TABLE guardians ADD CONSTRAINT fk_guardians_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE athlete_guardians DROP CONSTRAINT IF EXISTS fk_athlete_guardians_athlete;
ALTER TABLE athlete_guardians ADD CONSTRAINT fk_athlete_guardians_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE athlete_guardians DROP CONSTRAINT IF EXISTS fk_athlete_guardians_guardian;
ALTER TABLE athlete_guardians ADD CONSTRAINT fk_athlete_guardians_guardian
    FOREIGN KEY (guardian_id) REFERENCES guardians (id) ON DELETE CASCADE;

ALTER TABLE consents DROP CONSTRAINT IF EXISTS fk_consents_athlete;
ALTER TABLE consents ADD CONSTRAINT fk_consents_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE medical_certificates DROP CONSTRAINT IF EXISTS fk_medical_certificates_athlete;
ALTER TABLE medical_certificates ADD CONSTRAINT fk_medical_certificates_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

-- CERTIFICADO MEDICO POR TEMPORADA (bloque 3b)
--  La columna la añade Hibernate sin NOT NULL, porque la anotacion no lo
--  declara: añadirla obligatoria a una tabla con filas fallaria antes del
--  relleno. Aqui se rellena y despues se hace obligatoria.
--
--  Cada certificado que ya existia va a la temporada que cubre su fecha de
--  emision. Si ninguna la cubre, a la activa del club. Si el club no tiene
--  activa, a la mas reciente. Solo un club sin ninguna temporada dejaria un
--  certificado sin asignar, y entonces el SET NOT NULL falla y la aplicacion
--  no arranca: es a proposito, un certificado sin temporada no se puede medir.
ALTER TABLE medical_certificates ADD COLUMN IF NOT EXISTS season_id UUID;

UPDATE medical_certificates mc SET season_id = (
    SELECT s.id FROM seasons s
    WHERE s.club_id = mc.club_id AND mc.issued_on BETWEEN s.start_date AND s.end_date
    ORDER BY s.start_date DESC LIMIT 1)
WHERE mc.season_id IS NULL;

UPDATE medical_certificates mc SET season_id = (
    SELECT s.id FROM seasons s WHERE s.club_id = mc.club_id AND s.active LIMIT 1)
WHERE mc.season_id IS NULL;

UPDATE medical_certificates mc SET season_id = (
    SELECT s.id FROM seasons s WHERE s.club_id = mc.club_id
    ORDER BY s.start_date DESC LIMIT 1)
WHERE mc.season_id IS NULL;

ALTER TABLE medical_certificates ALTER COLUMN season_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_medical_certificates_athlete_season
    ON medical_certificates (athlete_id, season_id);

ALTER TABLE medical_certificates DROP CONSTRAINT IF EXISTS fk_medical_certificates_season;
ALTER TABLE medical_certificates ADD CONSTRAINT fk_medical_certificates_season
    FOREIGN KEY (season_id) REFERENCES seasons (id);

-- ENTREGAS DE PAPELES (bloque 3a)
--  Constancia de que la familia entrego un papel al club y hasta cuando vale:
--  licencia (por temporada), documento de identidad (hasta su caducidad) y
--  permiso de viaje (fechas del viaje). El papel se queda en el club; aqui no
--  hay archivo, ni numero de documento, ni notas.
--
--  Lleva club_id y policy propia: migrations/S.1-document-deliveries-rls.sql.
--
--  El CREATE TABLE documenta la forma; la tabla la crea Hibernate antes. Lo que
--  tiene que existir de verdad son las sentencias sueltas de debajo.
CREATE TABLE IF NOT EXISTS document_deliveries (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID        NOT NULL,
    athlete_id       UUID        NOT NULL,
    type             VARCHAR(30) NOT NULL,
    season_id        UUID,
    valid_from       DATE,
    valid_until      DATE,
    delivered_on     DATE        NOT NULL,
    registered_by_id UUID        NOT NULL,
    registered_at    TIMESTAMP   NOT NULL,
    created_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_deliveries_club_id ON document_deliveries (club_id);

-- El estado se pregunta por atleta y por tipo, en cada ficha y en cada roster.
CREATE INDEX IF NOT EXISTS idx_document_deliveries_athlete_type
    ON document_deliveries (athlete_id, type);

ALTER TABLE document_deliveries DROP CONSTRAINT IF EXISTS fk_document_deliveries_athlete;
ALTER TABLE document_deliveries ADD CONSTRAINT fk_document_deliveries_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE training_groups DROP CONSTRAINT IF EXISTS fk_training_groups_coach;
ALTER TABLE training_groups ADD CONSTRAINT fk_training_groups_coach
    FOREIGN KEY (coach_id) REFERENCES users (id) ON DELETE SET NULL;

-- ENTRENADORES AYUDANTES (tarea S.3.3.b)
--  El principal sigue en training_groups.coach_id; aqui van los demas. Los
--  permisos son los mismos: estar en cualquiera de los dos sitios da acceso al
--  grupo, a sus sesiones y a los atletas que hoy estan en el.
--
--  Tabla hija sin club_id: se llega a ella siempre por el grupo, que si esta
--  bajo policy, y su contenido nunca viaja con un id propio en la API.
--
--  El CREATE TABLE documenta la forma; la tabla la crea Hibernate antes. Lo que
--  tiene que existir de verdad son las sentencias sueltas de debajo.
CREATE TABLE IF NOT EXISTS group_assistant_coaches (
    group_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

-- "¿Que grupos lleva este usuario?" se pregunta en cada peticion de un
-- entrenador, y la clave primaria empieza por group_id: no sirve para eso.
CREATE INDEX IF NOT EXISTS idx_group_assistant_coaches_user
    ON group_assistant_coaches (user_id);

ALTER TABLE group_assistant_coaches DROP CONSTRAINT IF EXISTS fk_group_assistant_coaches_group;
ALTER TABLE group_assistant_coaches ADD CONSTRAINT fk_group_assistant_coaches_group
    FOREIGN KEY (group_id) REFERENCES training_groups (id) ON DELETE CASCADE;

-- Borrar a un usuario lo quita de ayudante sin tocar el grupo.
ALTER TABLE group_assistant_coaches DROP CONSTRAINT IF EXISTS fk_group_assistant_coaches_user;
ALTER TABLE group_assistant_coaches ADD CONSTRAINT fk_group_assistant_coaches_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE athlete_groups DROP CONSTRAINT IF EXISTS fk_athlete_groups_athlete;
ALTER TABLE athlete_groups ADD CONSTRAINT fk_athlete_groups_athlete
    FOREIGN KEY (athlete_id) REFERENCES athletes (id) ON DELETE CASCADE;

ALTER TABLE athlete_groups DROP CONSTRAINT IF EXISTS fk_athlete_groups_group;
ALTER TABLE athlete_groups ADD CONSTRAINT fk_athlete_groups_group
    FOREIGN KEY (group_id) REFERENCES training_groups (id) ON DELETE CASCADE;

ALTER TABLE group_schedules DROP CONSTRAINT IF EXISTS fk_group_schedules_group;
ALTER TABLE group_schedules ADD CONSTRAINT fk_group_schedules_group
    FOREIGN KEY (group_id) REFERENCES training_groups (id) ON DELETE CASCADE;

ALTER TABLE training_sessions DROP CONSTRAINT IF EXISTS fk_training_sessions_group;
ALTER TABLE training_sessions ADD CONSTRAINT fk_training_sessions_group
    FOREIGN KEY (group_id) REFERENCES training_groups (id) ON DELETE CASCADE;

ALTER TABLE training_sessions DROP CONSTRAINT IF EXISTS fk_training_sessions_schedule;
ALTER TABLE training_sessions ADD CONSTRAINT fk_training_sessions_schedule
    FOREIGN KEY (schedule_id) REFERENCES group_schedules (id) ON DELETE SET NULL;

-- ============================================================
--  MULTI-TENANCY — club_id en las entidades raiz (tarea 0.2)
-- ============================================================
--  Idempotente y en este orden: la columna nace nullable, se rellena con el
--  club por defecto y solo despues pasa a NOT NULL. Anadirla NOT NULL de golpe
--  falla en cuanto la tabla tiene una sola fila.
--
--  Solo llevan club_id las entidades raiz: users, athletes y posts. Las hijas
--  (comments, post_images, competition_results, athlete_documents,
--  user_athletes, athlete_invite_keys) llegan a su club por el padre.
-- ============================================================

-- USERS
ALTER TABLE users ADD COLUMN IF NOT EXISTS club_id UUID;
UPDATE users SET club_id = (SELECT id FROM clubs WHERE slug = 'sierra-oeste')
    WHERE club_id IS NULL;
ALTER TABLE users ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_club_id ON users (club_id);

-- El username pasa a ser unico por club: cada club necesita poder tener su
-- propio 'admin'. El email sigue siendo unico global (decision abierta).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_club_username ON users (club_id, username);

-- ATHLETES
ALTER TABLE athletes ADD COLUMN IF NOT EXISTS club_id UUID;
UPDATE athletes SET club_id = (SELECT id FROM clubs WHERE slug = 'sierra-oeste')
    WHERE club_id IS NULL;
ALTER TABLE athletes ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_athletes_club_id ON athletes (club_id);

-- El dni pasa a ser unico por club, igual que el username: el mismo nadador
-- puede estar en dos clubes, y cada uno tiene su propia ficha. Dentro de un
-- mismo club dos fichas con el mismo dni siguen siendo un error.
ALTER TABLE athletes DROP CONSTRAINT IF EXISTS athletes_dni_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_athletes_club_dni ON athletes (club_id, dni);

-- El documento de identidad pasa a ser opcional y admite NIE y pasaporte
-- (bloque 3b): hay menores sin DNI y extranjeros sin DNI. ddl-auto no quita un
-- NOT NULL ni cambia la longitud de una columna que ya existe, asi que va aqui.
-- El indice unico de arriba sigue sirviendo: Postgres no considera iguales dos
-- nulos, y dos fichas sin documento no chocan.
ALTER TABLE athletes ALTER COLUMN dni DROP NOT NULL;
ALTER TABLE athletes ALTER COLUMN dni TYPE VARCHAR(20);

-- POSTS
ALTER TABLE posts ADD COLUMN IF NOT EXISTS club_id UUID;
UPDATE posts SET club_id = (SELECT id FROM clubs WHERE slug = 'sierra-oeste')
    WHERE club_id IS NULL;
ALTER TABLE posts ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_posts_club_id ON posts (club_id);

-- El slug deja de ser unico: es descriptivo y puede repetirse entre clubes. El
-- post se identifica por su id. No se sustituye por un unico (club_id, slug):
-- ni siquiera dentro de un club se exige que no se repita.
ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_slug_key;

-- Claves foraneas hacia club. Van al final, cuando las columnas ya estan
-- rellenas. ADD CONSTRAINT no admite IF NOT EXISTS, asi que se borra antes: el
-- par DROP IF EXISTS + ADD es idempotente y vale para las dos situaciones.
--
-- Sin bloque DO a proposito: Spring parte estos scripts por `;` y no entiende
-- el entrecomillado con $$, aunque psql lo ejecute sin problema.
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_club;
ALTER TABLE users ADD CONSTRAINT fk_users_club FOREIGN KEY (club_id) REFERENCES clubs (id);

ALTER TABLE athletes DROP CONSTRAINT IF EXISTS fk_athletes_club;
ALTER TABLE athletes ADD CONSTRAINT fk_athletes_club FOREIGN KEY (club_id) REFERENCES clubs (id);

ALTER TABLE posts DROP CONSTRAINT IF EXISTS fk_posts_club;
ALTER TABLE posts ADD CONSTRAINT fk_posts_club FOREIGN KEY (club_id) REFERENCES clubs (id);

-- ============================================================
--  DATOS INICIALES
-- ============================================================

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_EDITOR'),
    ('ROLE_USER'),
    ('ROLE_TECHNICAL_STAFF')
ON CONFLICT (name) DO NOTHING;
-- La conexion vuelve al pool: no debe llevar 'public' pegado.
SELECT set_config('app.club_id', '', false);
