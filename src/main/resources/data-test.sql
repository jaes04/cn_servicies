-- ============================================================
-- Datos de prueba para cn_servicies
-- Ejecutar sobre la BD: cn_test
-- Passwords hasheados con BCrypt (valor: "password123")
-- ============================================================

-- Roles
INSERT INTO roles (name) VALUES ('ROLE_ADMIN')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_EDITOR')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_USER')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_TECHNICAL_STAFF')
    ON CONFLICT (name) DO NOTHING;

-- Usuarios
-- password: password123  →  BCrypt hash
INSERT INTO users (id, username, email, password_hash, enabled, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    'admin@test.com',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOe2jfBo2Bn4GIBuP.J.NqXbXgQLcPMuG',
    true,
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, enabled, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'editor',
    'editor@test.com',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOe2jfBo2Bn4GIBuP.J.NqXbXgQLcPMuG',
    true,
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, enabled, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000003',
    'reader',
    'reader@test.com',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOe2jfBo2Bn4GIBuP.J.NqXbXgQLcPMuG',
    true,
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;

-- Asignar roles
INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-000000000001', id FROM roles WHERE name = 'ROLE_ADMIN'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-000000000002', id FROM roles WHERE name = 'ROLE_EDITOR'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-000000000003', id FROM roles WHERE name = 'ROLE_USER'
    ON CONFLICT DO NOTHING;