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

-- Atletas
INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender, created_at, updated_at) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'Carlos',  'García López',   '2005-03-14', '12345678A', 'MALE',   NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000002', 'Laura',   'Martínez Ruiz',  '2006-07-22', '23456789B', 'FEMALE', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000003', 'Marcos',  'Fernández Gil',  '2004-11-05', '34567890C', 'MALE',   NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000004', 'Sofía',   'López Moreno',   '2007-01-30', '45678901D', 'FEMALE', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000005', 'Alejandro','Sánchez Vega',  '2005-09-18', '56789012E', 'MALE',   NOW(), NOW())
ON CONFLICT (dni) DO NOTHING;