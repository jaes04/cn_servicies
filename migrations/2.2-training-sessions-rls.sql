-- ============================================================
--  2.2 — Row Level Security para las sesiones de entrenamiento
-- ============================================================
--  Novena tabla con club_id, y la primera que lo lleva SIENDO TABLA HIJA por
--  este motivo: es la primera cuyo id viaja solo en la API.
--
--  /api/sessions/{id} —y en la 2.3 el /roster que consume el movil— se resuelve
--  con un findById por clave primaria, que es exactamente donde el filtro de
--  Hibernate NO se aplica. Sin esta policy, pedir el id de una sesion de otro
--  club la devuelve entera, y con ella la lista de menores que entrenaron ese
--  dia.
--
--  La alternativa era anidarlo todo bajo el grupo, como se hizo con los
--  horarios de la 2.1, y obligar al movil a una ruta de cuatro segmentos para
--  pasar lista. Se decidio esto.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/2.2-training-sessions-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON training_sessions TO cn_app;
ALTER TABLE training_sessions OWNER TO cn_app;

ALTER TABLE training_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON training_sessions;
CREATE POLICY club_isolation ON training_sessions
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE training_sessions FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM training_sessions;   -- solo las de ese club
--
--  Y la que importa, la de clave primaria:
--      SELECT * FROM training_sessions WHERE id = '<uuid de una sesion ajena>';
--      -- cero filas. Sin la policy, devuelve la sesion.
--      RESET ROLE;
