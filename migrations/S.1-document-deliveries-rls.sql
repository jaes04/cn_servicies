-- ============================================================
--  Bloque 3a — Row Level Security para las entregas de papeles
-- ============================================================
--  Tabla nueva con club_id. Sin esto nace sin policy y un findById por clave
--  primaria devolveria la entrega de un atleta de otro club: los filtros de
--  Hibernate no se aplican a las cargas por id.
--
--  La tabla no guarda el papel —solo que se entrego y hasta cuando vale—, pero
--  saber que un menor tiene o no tiene licencia, documento en regla o permiso
--  para un viaje sigue siendo informacion que no puede cruzar clubes.
--
--  SE PUEDE EJECUTAR CON EL ROL DE LA APLICACION mientras el dueño de las
--  tablas sea cn_app, que es quien las crea con ddl-auto:
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/S.1-document-deliveries-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez. Es idempotente.
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON document_deliveries TO cn_app;
ALTER TABLE document_deliveries OWNER TO cn_app;

ALTER TABLE document_deliveries ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON document_deliveries;
CREATE POLICY club_isolation ON document_deliveries
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

ALTER TABLE document_deliveries FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM document_deliveries;   -- solo los de ese club
--      RESET ROLE;
