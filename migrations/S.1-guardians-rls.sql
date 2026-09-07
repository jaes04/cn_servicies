-- ============================================================
--  S.1 — Row Level Security para la tabla de tutores
-- ============================================================
--  La 0.6 dejo con policy a las tres tablas raiz que habia entonces: users,
--  athletes y posts. `guardians` es la cuarta, y sin esto nace fuera del
--  aislamiento: el filtro de Hibernate la taparia en las consultas normales,
--  pero un findById por clave primaria seguiria devolviendo la fila de otro
--  club, que es exactamente el agujero que la 0.6 vino a cerrar.
--
--  Toda entidad raiz nueva necesita su bloque aqui. No es opcional.
--
--  SE EJECUTA CON EL ROL DE MIGRACIONES (postgres), no con el de la aplicacion:
--
--      psql -h localhost -U postgres -d cn_test -f migrations/S.1-guardians-rls.sql
--
--  Va DESPUES de arrancar la aplicacion al menos una vez, porque quien crea las
--  tablas sigue siendo Hibernate con ddl-auto. Es idempotente.
--
--  Depende de `app_club_visible(uuid)`, que crea la 0.6.
-- ============================================================


-- ------------------------------------------------------------
--  1. Permisos y propiedad
-- ------------------------------------------------------------
--  Las ALTER DEFAULT PRIVILEGES de la 0.6 solo alcanzan a las tablas que crea
--  el mismo rol que las declaro. Si guardians la crea Hibernate conectado como
--  cn_app, ya es suya y esto no cambia nada; si la creo otro rol, esto lo
--  arregla. En los dos casos es seguro repetirlo.

GRANT SELECT, INSERT, UPDATE, DELETE ON guardians         TO cn_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON athlete_guardians TO cn_app;

ALTER TABLE guardians         OWNER TO cn_app;
ALTER TABLE athlete_guardians OWNER TO cn_app;


-- ------------------------------------------------------------
--  2. Policy de aislamiento
-- ------------------------------------------------------------
--  Misma funcion y mismos tres estados de `app.club_id` que en la 0.6: un uuid
--  ve su club, 'public' lo ve todo, y sin fijar no se ve nada.

ALTER TABLE guardians ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS club_isolation ON guardians;
CREATE POLICY club_isolation ON guardians
    USING (app_club_visible(club_id))
    WITH CHECK (app_club_visible(club_id));

-- Tambien al dueño, o ser dueño equivaldria a BYPASSRLS.
ALTER TABLE guardians FORCE ROW LEVEL SECURITY;


-- ------------------------------------------------------------
--  3. Por que athlete_guardians no lleva policy
-- ------------------------------------------------------------
--  No tiene club_id: es tabla hija, y llega a su club por athletes o por
--  guardians, las dos con policy propia. Es el mismo criterio que ya siguen
--  user_athletes, athlete_documents y competition_results.
--
--  Lo que eso implica, y conviene tener presente: una consulta que arranque en
--  athlete_guardians y no toque a ninguno de sus padres no esta protegida por
--  Postgres. Hoy no existe ninguna —el repositorio solo busca por athlete_id y
--  por guardian_id, que son ids que la aplicacion ya obtuvo pasando por una
--  tabla filtrada— pero una consulta nativa futura si podria abrirla.


-- ------------------------------------------------------------
--  4. Comprobacion
-- ------------------------------------------------------------
--      SET ROLE cn_app;
--      SELECT set_config('app.club_id', '<uuid del club>', false);
--      SELECT count(*) FROM guardians;   -- solo los de ese club
--      RESET ROLE;
