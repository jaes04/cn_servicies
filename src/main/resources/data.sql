-- Este script lo ejecuta la aplicacion al arrancar, con su propio usuario, que
-- esta sujeto a las policies de Row Level Security. Sin fijar `app.club_id`,
-- el WITH CHECK rechaza cada INSERT y el arranque falla. `public` es el estado
-- que significa "todos los clubes". Se restablece al final del archivo: dejarlo
-- puesto lo heredaria la conexion, que vuelve al pool.
SELECT set_config('app.club_id', 'public', false);

-- =====================================================
-- SOLO CATALOGO. NADA DE DATOS DE EJEMPLO.
-- =====================================================
-- `spring.sql.init.mode=always`: este archivo corre en CUALQUIER base al
-- arrancar, la de produccion incluida. Todo lo que se ponga aqui aparece en la
-- base de un club de verdad.
--
-- Hasta septiembre de 2026 sembraba tambien cuatro cuentas —`admin` con
-- ROLE_ADMIN, `editor`, `tecnico` y `usuario`—, cinco atletas con DNI, seis
-- documentos, resultados y noticias. Cada despliegue nuevo nacia con una cuenta
-- de administrador que nadie habia creado ni gestionaba, con el hash de su
-- contrasena publicado en el repositorio. Se quito todo.
--
-- Los datos de desarrollo se crean por la API, con un administrador de verdad
-- (el de ADMIN_USERNAME), no desde aqui.
--
-- Nota: los INSERT usan `ON CONFLICT DO NOTHING` sin indicar columna. Con
-- `ON CONFLICT (id)` la clausula solo cubre la clave primaria y el arranque
-- revienta si la fila choca por otra restriccion unica.

-- =====================================================
-- CLUB POR DEFECTO
-- UUID fijo: es el club al que se asignaron las filas
-- existentes en el backfill de club_id (Fase 0.2).
-- No lleva datos personales ni da acceso a nada: es el
-- tenant en el que caen el arranque y el alta publica
-- mientras solo haya un club.
-- =====================================================
INSERT INTO clubs (id, name, slug, active, created_at)
VALUES ('99999999-0000-0000-0000-000000000001', 'CN demo', 'cn-demo', true, now())
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

-- Se deja sin valor: la conexion vuelve al pool y no debe llevar "public"
-- pegado. Cada transaccion fija el suyo desde ClubFilterAspect.
SELECT set_config('app.club_id', '', false);
