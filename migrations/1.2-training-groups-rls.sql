-- ============================================================
--  1.2 — Row Level Security para los grupos de entrenamiento
-- ============================================================
--  Octava tabla con club_id. Sin policy, un findById por clave primaria
--  devolveria el grupo de otro club, y de los grupos colgara la pertenencia de
--  los atletas (1.3) y toda la asistencia (Fase 2).
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/1.2-training-groups-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON training_groups TO cn_app;
ALTER TABLE training_groups OWNER TO cn_app;

ALTER TABLE training_groups ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON training_groups;
CREATE POLICY club_isolation ON training_groups
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE training_groups FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM training_groups;   -- solo los de ese club
--      RESET ROLE;
