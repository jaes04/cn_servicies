-- ============================================================
--  0.6 — Row Level Security
-- ============================================================
--  Segunda capa de aislamiento. La primera es el filtro de Hibernate (0.5),
--  que tiene agujeros conocidos: no se aplica a las cargas por clave primaria,
--  asi que un findById devuelve la fila aunque sea de otro club. Esto lo cierra,
--  porque el filtrado pasa a imponerlo Postgres y da igual por que via pida
--  Hibernate la fila.
--
--  SE EJECUTA CON EL ROL DE MIGRACIONES (postgres), no con el de la aplicacion:
--
--      psql -h localhost -U postgres -d cn_test -f migrations/0.6-row-level-security.sql
--
--  Es idempotente: se puede volver a lanzar sin efecto.
-- ============================================================


-- ------------------------------------------------------------
--  1. Rol de aplicacion, SIN BYPASSRLS
-- ------------------------------------------------------------
--  Hoy la aplicacion se conecta como `postgres`, superusuario, que se salta RLS
--  por completo. Mientras siga asi, todo lo de abajo es decorativo.
--
--  Se crea SIN contrasena a proposito: no puede conectarse hasta que se le
--  ponga una, y esa la pone quien despliega, no este script.
--
--      \password cn_app
--
--  El rol de migraciones sigue siendo `postgres`: es superusuario, asi que se
--  salta las policies y puede seguir creando esquema y sembrando datos.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cn_app') THEN
        CREATE ROLE cn_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO cn_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cn_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO cn_app;

-- Que las tablas y secuencias futuras nazcan con los mismos permisos.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cn_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO cn_app;


-- ------------------------------------------------------------
--  2. Policies de aislamiento
-- ------------------------------------------------------------
--  `app.club_id` tiene tres estados, y el orden importa:
--
--    - un UUID    -> solo las filas de ese club
--    - 'public'   -> todas las filas; es lo que se pone en las peticiones
--                    anonimas, que hoy no tienen club: login, alta de usuario
--                    y blog publico
--    - sin poner  -> NINGUNA fila
--
--  El tercero es el que importa. Si un dia el aspecto deja de ejecutarse, o
--  alguien abre una transaccion por otra via, la variable no esta y no se ve
--  nada. Falla cerrado: el sintoma es "no aparecen datos", no "aparecen los de
--  otro club".
--
--  El doble NULLIF evita que 'public' llegue al cast a uuid. Postgres no
--  garantiza evaluacion perezosa del OR, asi que sin eso la policy reventaria
--  con "invalid input syntax for type uuid" en cada peticion anonima.

CREATE OR REPLACE FUNCTION app_club_visible(fila_club_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT current_setting('app.club_id', true) = 'public'
        OR fila_club_id = NULLIF(NULLIF(current_setting('app.club_id', true), ''), 'public')::uuid;
$$;

ALTER TABLE users    ENABLE ROW LEVEL SECURITY;
ALTER TABLE athletes ENABLE ROW LEVEL SECURITY;
ALTER TABLE posts    ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON users;
CREATE POLICY club_isolation ON users
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

DROP POLICY IF EXISTS club_isolation ON athletes;
CREATE POLICY club_isolation ON athletes
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

DROP POLICY IF EXISTS club_isolation ON posts;
CREATE POLICY club_isolation ON posts
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));


-- ------------------------------------------------------------
--  3. Comprobacion
-- ------------------------------------------------------------
--  Las policies no se aplican a superusuarios. Para verlas actuar hay que
--  adoptar el rol de aplicacion dentro de la sesion:
--
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM athletes;   -- solo los de ese club
--      RESET ROLE;
