-- ============================================================
--  0.0 — El rol de la aplicacion, ANTES del primer arranque
-- ============================================================
--  Esta es la pieza que faltaba para levantar un entorno desde cero, y el
--  orden es lo unico que importa aqui:
--
--    1. La base existe pero esta vacia.
--    2. ESTE ARCHIVO, como superusuario. Crea el rol `cn_app` y le da permiso
--       para crear tablas en el esquema `public`.
--    3. Arranca la aplicacion. Hibernate crea el esquema conectado como
--       `cn_app`, asi que las tablas nacen siendo suyas.
--    4. `0.6-row-level-security.sql` y el resto de migraciones.
--
--  Sin el paso 2 no hay paso 3: la aplicacion se conecta como `cn_app`, que no
--  existe, y el contenedor entra en bucle de reinicios con un
--  "password authentication failed" que parece un problema de contrasena y no
--  lo es. Y aunque el rol existiera, desde PostgreSQL 15 el esquema `public` ya
--  no deja crear tablas a cualquiera: sin el GRANT de abajo, Hibernate no puede
--  crear ni una.
--
--  La 0.6 tambien crea el rol —es idempotente y no estorba—, pero no sirve para
--  esto: ademas enciende RLS sobre tablas que en el paso 2 todavia no existen.
--
--      psql -h localhost -U postgres -d cn_servicies -f migrations/0.0-bootstrap-rol.sql
--
--  Es idempotente. NO pone la contrasena del rol: eso lo hace
--  `scripts/preparar-base.sh`, que la pasa por la entrada estandar para que no
--  quede a la vista en la lista de procesos de la maquina.
-- ============================================================


-- ------------------------------------------------------------
--  1. El rol, sin poder saltarse sus propias policies
-- ------------------------------------------------------------
--  NOBYPASSRLS y NOSUPERUSER son el punto entero: un superusuario se salta Row
--  Level Security y el aislamiento entre clubes desaparece sin que nada falle
--  ni avise.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cn_app') THEN
        CREATE ROLE cn_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
        RAISE NOTICE 'Rol cn_app creado.';
    ELSE
        RAISE NOTICE 'El rol cn_app ya existia.';
    END IF;
END
$$;


-- ------------------------------------------------------------
--  2. Permiso para crear el esquema
-- ------------------------------------------------------------
--  CREATE es lo que necesita Hibernate en el primer arranque; USAGE es para
--  todo lo demas. Las dan tambien la 0.6, pero para entonces ya es tarde.
GRANT USAGE, CREATE ON SCHEMA public TO cn_app;


-- ------------------------------------------------------------
--  3. Comprobacion
-- ------------------------------------------------------------
SELECT rolname,
       rolsuper    AS es_superusuario,
       rolbypassrls AS se_salta_rls,
       has_schema_privilege('cn_app', 'public', 'CREATE') AS puede_crear_tablas
FROM pg_roles
WHERE rolname = 'cn_app';
