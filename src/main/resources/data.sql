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
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- ASIGNACIÓN DE ROLES
-- =====================================================
INSERT INTO user_roles (user_id, role_id)
    SELECT '00000000-0000-0000-0000-000000000001', id FROM roles WHERE name = 'ROLE_ADMIN'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT '00000000-0000-0000-0000-000000000002', id FROM roles WHERE name = 'ROLE_EDITOR'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT '00000000-0000-0000-0000-000000000003', id FROM roles WHERE name = 'ROLE_TECHNICAL_STAFF'
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT '00000000-0000-0000-0000-000000000004', id FROM roles WHERE name = 'ROLE_USER'
    ON CONFLICT DO NOTHING;

-- =====================================================
-- ATLETAS
-- =====================================================
INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000001', 'Carlos',  'García López',      '2005-03-15', '12345678A', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT (id) DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000002', 'Laura',   'Martínez Sánchez',  '2007-06-22', '23456789B', g.id, now(), now() FROM genders g WHERE g.name = 'FEMALE'
    ON CONFLICT (id) DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000003', 'Miguel',  'Fernández Torres',  '2004-11-08', '34567890C', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT (id) DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000004', 'Ana',     'Ruiz Moreno',       '2006-09-30', '45678901D', g.id, now(), now() FROM genders g WHERE g.name = 'FEMALE'
    ON CONFLICT (id) DO NOTHING;

INSERT INTO athletes (id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)
    SELECT '10000000-0000-0000-0000-000000000005', 'Pablo',   'López Jiménez',     '2003-07-12', '56789012E', g.id, now(), now() FROM genders g WHERE g.name = 'MALE'
    ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- VÍNCULOS USUARIO-ATLETA  (UserAthleteType: ATHLETE, TUTOR)
-- usuario (ROLE_USER) es el propio atleta Carlos García
-- tecnico actúa como tutor de Laura Martínez
-- =====================================================
INSERT INTO user_athletes (id, user_id, athlete_id, type, created_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'ATHLETE', now()),
    ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'TUTOR',   now())
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
     '00000000-0000-0000-0000-000000000001',
     now(), now()),

    ('30000000-0000-0000-0000-000000000002',
     'Resultados Campeonato Regional 2025',
     '<p>Nuestros atletas han conseguido excelentes resultados en el Campeonato Regional. Enhorabuena a todos los participantes.</p>',
     'resultados-campeonato-regional-2025',
     'PUBLISHED', now(),
     '00000000-0000-0000-0000-000000000002',
     now(), now()),

    ('30000000-0000-0000-0000-000000000003',
     'Próxima competición - Liga Autonómica',
     '<p>Os informamos de los detalles de la próxima competición de liga autonómica.</p>',
     'proxima-competicion-liga-autonomica',
     'DRAFT', null,
     '00000000-0000-0000-0000-000000000002',
     now(), now()),

    ('30000000-0000-0000-0000-000000000004',
     'Noticia eliminada de ejemplo',
     '<p>Contenido eliminado.</p>',
     'noticia-eliminada-ejemplo',
     'DELETED', null,
     '00000000-0000-0000-0000-000000000001',
     now(), now())
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- DOCUMENTOS DE ATLETA  (AthleteDocumentType: MEDICAL, TRAINING, COMPETITION, CONSENT, IDENTIFICATION, OTHER)
-- =====================================================
INSERT INTO athlete_documents (id, title, type, filename, original_filename, athlete_id, uploaded_by_id, created_at)
VALUES
    ('50000000-0000-0000-0000-000000000001', 'Reconocimiento médico 2025',     'MEDICAL',        'doc-medico-carlos-2025.pdf',     'reconocimiento_medico_2025.pdf',  '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', now()),
    ('50000000-0000-0000-0000-000000000002', 'Plan de entrenamiento T1 2025',  'TRAINING',       'plan-entrenamiento-laura-t1.pdf', 'plan_entrenamiento_T1.pdf',       '10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', now()),
    ('50000000-0000-0000-0000-000000000003', 'Acta Campeonato Regional 2025',  'COMPETITION',    'acta-campeonato-regional-25.pdf', 'acta_campeonato_regional.pdf',    '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', now()),
    ('50000000-0000-0000-0000-000000000004', 'Consentimiento imagen menor',    'CONSENT',        'consentimiento-imagen-ana.pdf',   'consentimiento_imagen.pdf',       '10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', now()),
    ('50000000-0000-0000-0000-000000000005', 'DNI Carlos García',              'IDENTIFICATION', 'dni-carlos-garcia.pdf',           'dni_carlos_garcia.pdf',           '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', now()),
    ('50000000-0000-0000-0000-000000000006', 'Autorización desplazamiento',    'OTHER',          'autorizacion-desplazamiento.pdf', 'autorizacion_desplazamiento.pdf', '10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', now())
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- RESULTADOS DE COMPETICIÓN
-- =====================================================
INSERT INTO competition_results (id, athlete_id, competition_date, distance_meters, stroke, pool_length, result_time_millis, partial, created_at, updated_at)
VALUES
    ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '2025-03-10', 100, 'FREESTYLE',    25,  58320,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '2025-05-20', 200, 'BACKSTROKE',   50, 142500,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', '2025-03-10', 100, 'BUTTERFLY',    25,  67800,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', '2025-05-20',  50, 'BREASTSTROKE', 50,  41200,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003', '2025-06-15',  50, 'FREESTYLE',    50,  26100,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000004', '2025-06-15', 200, 'MEDLEY',       50, 158900,  false, now(), now()),
    ('40000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000005', '2025-07-01', 400, 'FREESTYLE',    50, 258000,  false, now(), now())
ON CONFLICT (id) DO NOTHING;