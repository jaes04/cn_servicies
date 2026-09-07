-- ============================================================
--  S.1.b — Row Level Security para los certificados medicos
-- ============================================================
--  Sexta tabla con club_id. Sin esto nace sin policy y un findById por clave
--  primaria devolveria el certificado de un atleta de otro club: los filtros de
--  Hibernate no se aplican a las cargas por id.
--
--  La tabla no guarda veredicto ni diagnostico —solo fechas—, pero saber que
--  un menor tiene o no tiene certificado en plazo sigue siendo informacion que
--  no puede cruzar clubes.
--
--  SE PUEDE EJECUTAR CON EL ROL DE LA APLICACION mientras el dueño de las
--  tablas sea cn_app, que es quien las crea con ddl-auto:
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/S.1-medical-certificates-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON medical_certificates TO cn_app;
ALTER TABLE medical_certificates OWNER TO cn_app;

ALTER TABLE medical_certificates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON medical_certificates;
CREATE POLICY club_isolation ON medical_certificates
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE medical_certificates FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM medical_certificates;   -- solo los de ese club
--      RESET ROLE;
