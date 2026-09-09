-- ============================================================
--  2.2.b — Row Level Security para el calendario de excepciones
-- ============================================================
--  Decima tabla con club_id, y esta vez por la regla de siempre y no por una
--  excepcion: el cierre es una entidad raiz, del club y no de un grupo.
--
--  Sin policy, los festivos de un club tumbarian los entrenamientos de otro. El
--  generador consulta los cierres del rango sin nombrar el club —se lo pone el
--  filtro de tenancy— asi que una tabla sin aislar aqui se nota en el calendario
--  de todo el mundo.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/2.2-club-closures-rls.sql
--
--  En PowerShell, con la ruta completa del ejecutable:
--      & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U cn_app -d cn_test -f migrations\2.2-club-closures-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON club_closures TO cn_app;
ALTER TABLE club_closures OWNER TO cn_app;

ALTER TABLE club_closures ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON club_closures;
CREATE POLICY club_isolation ON club_closures
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE club_closures FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM club_closures;   -- solo los de ese club
--      RESET ROLE;
