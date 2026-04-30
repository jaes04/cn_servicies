-- ============================================================
--  SCHEMA - cn_servicies
-- ============================================================

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

-- ============================================================
--  DATOS INICIALES
-- ============================================================

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_EDITOR'),
    ('ROLE_USER'),
    ('ROLE_TECHNICAL_STAFF')
ON CONFLICT (name) DO NOTHING;