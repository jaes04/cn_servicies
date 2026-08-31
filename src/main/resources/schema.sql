-- ============================================================
--  SCHEMA - cn_servicies
-- ============================================================

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

-- POSTS
ALTER TABLE posts ADD COLUMN IF NOT EXISTS club_id UUID;
UPDATE posts SET club_id = (SELECT id FROM clubs WHERE slug = 'sierra-oeste')
    WHERE club_id IS NULL;
ALTER TABLE posts ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_posts_club_id ON posts (club_id);

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