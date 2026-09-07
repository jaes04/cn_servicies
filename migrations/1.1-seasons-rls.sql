-- ============================================================
--  1.1 — Row Level Security para las temporadas
-- ============================================================
--  Septima tabla con club_id. Sin policy, un findById por clave primaria
--  devolveria la temporada de otro club, y de las temporadas colgaran los
--  grupos y la asistencia: es la raiz del historico deportivo.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/1.1-seasons-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON seasons TO cn_app;
ALTER TABLE seasons OWNER TO cn_app;

ALTER TABLE seasons ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON seasons;
CREATE POLICY club_isolation ON seasons
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE seasons FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM seasons;   -- solo las de ese club
--      RESET ROLE;
