-- Nota: los INSERT de este archivo usan `ON CONFLICT DO NOTHING` sin indicar
-- columna. Con `ON CONFLICT (id)` la clausula solo cubre la clave primaria y el
-- arranque revienta si la fila choca por otra restriccion unica (username,
-- email, dni, slug), que es justo lo que pasa cuando la base ya tiene datos.

-- =====================================================
-- CLUB POR DEFECTO
-- UUID fijo: es el club al que se asignaran las filas
-- existentes en el backfill de club_id (Fase 0.2)
-- =====================================================
INSERT INTO clubs (id, name, slug, active, created_at)
VALUES ('99999999-0000-0000-0000-000000000001', 'Club Natacion Sierra Oeste', 'sierra-oeste', true, now())
    ON CONFLICT DO NOTHING;

-- =====================================================
-- ROLES
-- =====================================================
INSERT INTO roles (name) VALUES ('ROLE_ADMIN')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_EDITOR')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_USER')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_TECHNICAL_STAFF')
    ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- GÉNEROS
-- =====================================================
INSERT INTO genders (name) VALUES ('MALE')
    ON CONFLICT (name) DO NOTHING;
INSERT INTO genders (name) VALUES ('FEMALE')
    ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- USUARIOS  (contraseña de todos: "Admin1234!")
-- Hash BCrypt generado con strength 10
-- =====================================================
INSERT INTO users (id, username, email, password_hash, blocked, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin',   'admin@clubnatacion.es',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', false, now(), now()),
    ('00000000-0000-0000-0000-000000000002', 'editor',  'editor@clubnatacion.es',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', false, now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'tecnico', 'tecnico@clubnatacion.es', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', false, now(), now()),
    ('00000000-0000-0000-0000-000000000004', 'usuario', 'usuario@clubnatacion.es', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', false, now(), now())
ON CONFLICT DO NOTHING;

-- =====================================================
-- ASIGNACIÓN DE ROLES
-- =====================================================
-- El usuario se resuelve por `username`, no por el UUID de arriba: si la fila ya
-- existia con otro id, el INSERT anterior se salta y un UUID fijo aqui apuntaria
-- a un usuario inexistente, rompiendo la clave foranea.
INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.username = 'editor' AND r.name = 'ROLE_EDITOR'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.username = 'tecnico' AND r.name = 'ROLE_TECHNICAL_STAFF'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.username = 'usuario' AND r.name = 'ROLE_USER'
    ON CONFLICT DO NOTHING;

-- =====================================================
-- ATLETAS
-- =====================================================
INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000001', 'Carlos',  'García López',      '2005-03-15', '12345678A', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000002', 'Laura',   'Martínez Sánchez',  '2007-06-22', '23456789B', g.id, now(), now() FROM genders g WHERE g.name = 'FEMALE'
    ON CONFLICT DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000003', 'Miguel',  'Fernández Torres',  '2004-11-08', '34567890C', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000004', 'Ana',     'Ruiz Moreno',       '2006-09-30', '45678901D', g.id, now(), now() FROM genders g WHERE g.name = 'FEMALE'
    ON CONFLICT DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000005', 'Pablo',   'López Jiménez',     '2003-07-12', '56789012E', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT DO NOTHING;

-- =====================================================
-- VÍNCULOS USUARIO-ATLETA  (UserAthleteType: ATHLETE, TUTOR)
-- usuario (ROLE_USER) es el propio atleta Carlos García
-- tecnico actúa como tutor de Laura Martínez
-- =====================================================
INSERT INTO user_athletes (id, user_id, athlete_id, type, created_at)
    SELECT '20000000-0000-0000-0000-000000000001', u.id, a.id, 'ATHLETE', now()
    FROM users u, athletes a WHERE u.username = 'usuario' AND a.dni = '12345678A'
    ON CONFLICT DO NOTHING;

INSERT INTO user_athletes (id, user_id, athlete_id, type, created_at)
    SELECT '20000000-0000-0000-0000-000000000002', u.id, a.id, 'TUTOR', now()
    FROM users u, athletes a WHERE u.username = 'tecnico' AND a.dni = '23456789B'
    ON CONFLICT DO NOTHING;

-- =====================================================
-- POSTS
-- =====================================================
INSERT INTO posts (id, title, content, slug, status, published_at, author_id, created_at, updated_at)
VALUES
    ('30000000-0000-0000-0000-000000000001',
     'Bienvenidos al Club de Natación',
     '<p>¡Bienvenidos a la nueva web del club! Aquí encontraréis noticias, resultados de competiciones y toda la información del club.</p>',
     'bienvenidos-club-natacion',
     'PUBLISHED', now(),
     (SELECT id FROM users WHERE username = 'admin'),
     now(), now()),

    ('30000000-0000-0000-0000-000000000002',
     'Resultados Campeonato Regional 2025',
     '<p>Nuestros atletas han conseguido excelentes resultados en el Campeonato Regional. Enhorabuena a todos los participantes.</p>',
     'resultados-campeonato-regional-2025',
     'PUBLISHED', now(),
     (SELECT id FROM users WHERE username = 'editor'),
     now(), now()),

    ('30000000-0000-0000-0000-000000000003',
     'Próxima competición - Liga Autonómica',
     '<p>Os informamos de los detalles de la próxima competición de liga autonómica.</p>',
     'proxima-competicion-liga-autonomica',
     'DRAFT', null,
     (SELECT id FROM users WHERE username = 'editor'),
     now(), now()),

    ('30000000-0000-0000-0000-000000000004',
     'Noticia eliminada de ejemplo',
     '<p>Contenido eliminado.</p>',
     'noticia-eliminada-ejemplo',
     'DELETED', null,
     (SELECT id FROM users WHERE username = 'admin'),
     now(), now())
ON CONFLICT DO NOTHING;

-- =====================================================
-- DOCUMENTOS DE ATLETA  (AthleteDocumentType: MEDICAL, TRAINING, COMPETITION, CONSENT, IDENTIFICATION, OTHER)
-- =====================================================
INSERT INTO athlete_documents (id, title, type, filename, original_filename, athlete_id, uploaded_by_id, created_at)
VALUES
    ('50000000-0000-0000-0000-000000000001', 'Reconocimiento médico 2025',     'MEDICAL',        'doc-medico-carlos-2025.pdf',     'reconocimiento_medico_2025.pdf',  (SELECT id FROM athletes WHERE dni = '12345678A'), (SELECT id FROM users WHERE username = 'tecnico'), now()),
    ('50000000-0000-0000-0000-000000000002', 'Plan de entrenamiento T1 2025',  'TRAINING',       'plan-entrenamiento-laura-t1.pdf', 'plan_entrenamiento_T1.pdf',       (SELECT id FROM athletes WHERE dni = '23456789B'), (SELECT id FROM users WHERE username = 'tecnico'), now()),
    ('50000000-0000-0000-0000-000000000003', 'Acta Campeonato Regional 2025',  'COMPETITION',    'acta-campeonato-regional-25.pdf', 'acta_campeonato_regional.pdf',    (SELECT id FROM athletes WHERE dni = '12345678A'), (SELECT id FROM users WHERE username = 'admin'), now()),
    ('50000000-0000-0000-0000-000000000004', 'Consentimiento imagen menor',    'CONSENT',        'consentimiento-imagen-ana.pdf',   'consentimiento_imagen.pdf',       (SELECT id FROM athletes WHERE dni = '45678901D'), (SELECT id FROM users WHERE username = 'admin'), now()),
    ('50000000-0000-0000-0000-000000000005', 'DNI Carlos García',              'IDENTIFICATION', 'dni-carlos-garcia.pdf',           'dni_carlos_garcia.pdf',           (SELECT id FROM athletes WHERE dni = '12345678A'), (SELECT id FROM users WHERE username = 'admin'), now()),
    ('50000000-0000-0000-0000-000000000006', 'Autorización desplazamiento',    'OTHER',          'autorizacion-desplazamiento.pdf', 'autorizacion_desplazamiento.pdf', (SELECT id FROM athletes WHERE dni = '34567890C'), (SELECT id FROM users WHERE username = 'tecnico'), now())
ON CONFLICT DO NOTHING;

-- =====================================================
-- RESULTADOS DE COMPETICIÓN
-- =====================================================
INSERT INTO competition_results (id, athlete_id, competition_date, distance_meters, stroke, pool_length, result_time_millis, partial, created_at, updated_at)
VALUES
    ('40000000-0000-0000-0000-000000000001', (SELECT id FROM athletes WHERE dni = '12345678A'), '2025-03-10', 100, 'FREESTYLE',    25,  58320,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000002', (SELECT id FROM athletes WHERE dni = '12345678A'), '2025-05-20', 200, 'BACKSTROKE',   50, 142500,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000003', (SELECT id FROM athletes WHERE dni = '23456789B'), '2025-03-10', 100, 'BUTTERFLY',    25,  67800,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000004', (SELECT id FROM athletes WHERE dni = '23456789B'), '2025-05-20',  50, 'BREASTSTROKE', 50,  41200,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000005', (SELECT id FROM athletes WHERE dni = '34567890C'), '2025-06-15',  50, 'FREESTYLE',    50,  26100,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000006', (SELECT id FROM athletes WHERE dni = '45678901D'), '2025-06-15', 200, 'MEDLEY',       50, 158900,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000007', (SELECT id FROM athletes WHERE dni = '56789012E'), '2025-07-01', 400, 'FREESTYLE',    50, 258000,  false, now(), now())
ON CONFLICT DO NOTHING;