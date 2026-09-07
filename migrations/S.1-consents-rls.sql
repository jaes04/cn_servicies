-- ============================================================
--  S.1 — Row Level Security para la tabla de consentimientos
-- ============================================================
--  `consents` lleva club_id, asi que entra en el mismo esquema de policies que
--  users, athletes, posts y guardians. Sin esto, un findById por clave primaria
--  devolveria el consentimiento de otro club: los filtros de Hibernate no se
--  aplican a las cargas por id.
--
--  SE PUEDE EJECUTAR CON EL ROL DE LA APLICACION. No hace falta superusuario:
--  ENABLE ROW LEVEL SECURITY, CREATE POLICY y FORCE los ejecuta el dueño de la
--  tabla, y hoy el dueño es cn_app porque las tablas las crea Hibernate con
--  ddl-auto.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/S.1-consents-rls.sql
--
--  Cuando el esquema salga de Hibernate y la propiedad pase al rol de
--  migraciones, esto volvera a necesitar ese rol. Es lo correcto: que la
--  aplicacion no pueda tocar sus propias policies es el objetivo.
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON consents TO cn_app;
ALTER TABLE consents OWNER TO cn_app;

ALTER TABLE consents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON consents;
CREATE POLICY club_isolation ON consents
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE consents FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM consents;   -- solo los de ese club
--      RESET ROLE;
