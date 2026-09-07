-- ============================================================
--  SCHEMA - cn_servicies
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
    dni        VARCHAR(9)   NOT NULL UNIQUE,
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
    athlete_id        UUID         NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    uploaded_by_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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
    dni        VARCHAR(9)   NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(255),
    user_id    UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_guardians_club_id ON guardians (club_id);

-- El dni del tutor es unico por club, no global: la misma persona puede ser
-- tutora en dos clubes y cada uno tiene su ficha.
CREATE UNIQUE INDEX IF NOT EXISTS uk_guardians_club_dni ON guardians (club_id, dni);

-- Una cuenta corresponde a una sola ficha de tutor. El indice unico de Postgres
-- ignora los nulos, asi que esto no estorba a los tutores sin cuenta, que son
-- la mayoria al principio.
CREATE UNIQUE INDEX IF NOT EXISTS uk_guardians_user ON guardians (user_id);

CREATE TABLE IF NOT EXISTS athlete_guardians (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id   UUID        NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    guardian_id  UUID        NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    relationship VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT uk_athlete_guardians UNIQUE (athlete_id, guardian_id)
);

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
    athlete_id    UUID        NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
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
--  Sin season_id: la vigencia la definen sus fechas, y `seasons` no existe
--  hasta la Fase 1.
--
--  Lleva club_id y policy propia, misma decision explicita que en consents.
CREATE TABLE IF NOT EXISTS medical_certificates (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID      NOT NULL REFERENCES clubs(id),
    athlete_id       UUID      NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
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
