-- ============================================================
--  2.6 — Quitar las claves foraneas generadas por Hibernate
-- ============================================================
--  `schema.sql` declaraba 24 `ON DELETE` de los que la base solo tenia 8: todo
--  lo que va dentro de un `CREATE TABLE` se pierde, porque Hibernate crea las
--  tablas antes de que ese archivo se ejecute. Lo que quedaba eran las claves
--  foraneas que Hibernate deduce de las anotaciones: sin accion de borrado y con
--  nombres como `fk629dnuhbjn2lukkl8aj4b25mi`, distintos en cada base.
--
--  Ya se han sacado a `ALTER TABLE` sueltos con nombre propio (`fk_<tabla>_<col>`)
--  y las entidades llevan `ConstraintMode.NO_CONSTRAINT` para que Hibernate no
--  vuelva a crear las suyas. Pero `ddl-auto=update` solo añade y nunca borra lo
--  que deja de estar declarado, asi que las viejas siguen ahi.
--
--  Y NO son inofensivas: con dos claves foraneas sobre la misma columna manda la
--  MAS RESTRICTIVA, asi que mientras la generada exista, la cascada nueva no
--  sirve de nada. Hay que pasar esto una vez por entorno.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/2.6-limpiar-fks-generadas.sql
--
--  En PowerShell, con la ruta completa del ejecutable:
--      & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U cn_app -d cn_test -f migrations\2.6-limpiar-fks-generadas.sql
--
--  Es idempotente: si ya se paso, no encuentra nada que borrar.
--
--  QUE SE BORRA: solo las claves foraneas cuyo nombre NO empieza por `fk_`, que
--  es el prefijo de las nuestras, y solo en las tablas listadas. Si en una tabla
--  no estuviera la nuestra, el bloque avisa y no toca nada, para no dejarla sin
--  integridad referencial.
-- ============================================================

DO $$
DECLARE
    objetivo record;
    generada  record;
    nuestras  int;
BEGIN
    FOR objetivo IN
        SELECT * FROM (VALUES
            ('athlete_documents',    'athlete_id'),
            ('guardians',            'user_id'),
            ('athlete_guardians',    'athlete_id'),
            ('athlete_guardians',    'guardian_id'),
            ('consents',             'athlete_id'),
            ('medical_certificates', 'athlete_id'),
            ('training_groups',      'coach_id'),
            ('athlete_groups',       'athlete_id'),
            ('athlete_groups',       'group_id'),
            ('group_schedules',      'group_id'),
            ('training_sessions',    'group_id'),
            ('training_sessions',    'schedule_id')
        ) AS t(tabla, columna)
    LOOP
        SELECT count(*) INTO nuestras
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN unnest(c.conkey) AS k(attnum) ON true
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
        WHERE c.contype = 'f'
          AND t.relname = objetivo.tabla
          AND a.attname = objetivo.columna
          AND c.conname LIKE 'fk\_%';

        IF nuestras = 0 THEN
            RAISE WARNING 'Saltando %.%: no encuentro la clave foranea con nombre propio',
                objetivo.tabla, objetivo.columna;
            CONTINUE;
        END IF;

        FOR generada IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN unnest(c.conkey) AS k(attnum) ON true
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
            WHERE c.contype = 'f'
              AND t.relname = objetivo.tabla
              AND a.attname = objetivo.columna
              AND c.conname NOT LIKE 'fk\_%'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', objetivo.tabla, generada.conname);
            RAISE NOTICE 'Borrada la clave foranea generada %.%', objetivo.tabla, generada.conname;
        END LOOP;
    END LOOP;
END $$;


-- ------------------------------------------------------------
--  Y los indices unicos generados de las mismas dos tablas
-- ------------------------------------------------------------
--  `athlete_guardians` y `user_athletes` tenian el problema contrario al de la
--  2.2: su unico existia SOLO con el nombre que le puso Hibernate. Ahora estan
--  declarados en schema.sql como `uk_athlete_guardians` y `uk_user_athletes`, asi
--  que la generada sobra.

DO $$
DECLARE
    tabla    text;
    generada record;
    nuestras int;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['athlete_guardians', 'user_athletes'] LOOP
        SELECT count(*) INTO nuestras FROM pg_indexes
        WHERE tablename = tabla AND indexname LIKE 'uk\_%';

        IF nuestras = 0 THEN
            RAISE WARNING 'Saltando %: no encuentro el unico con nombre propio', tabla;
            CONTINUE;
        END IF;

        FOR generada IN
            SELECT c.conname
            FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.contype = 'u' AND t.relname = tabla AND c.conname NOT LIKE 'uk\_%'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', tabla, generada.conname);
            RAISE NOTICE 'Borrado el unico generado %.%', tabla, generada.conname;
        END LOOP;
    END LOOP;
END $$;


-- ------------------------------------------------------------
--  LO QUE ESTO NO TOCA, Y POR QUE
-- ------------------------------------------------------------
--  athlete_documents.uploaded_by_id declara ON DELETE CASCADE y se ha dejado
--  fuera a proposito: borrar un USUARIO se llevaria por delante los documentos
--  que subio, que son del atleta y no suyos. Y no se puede cambiar a SET NULL
--  porque la columna es NOT NULL. Arreglarlo de verdad es hacerla nullable, y
--  eso es un cambio de modelo que hay que decidir aparte.
--
--  Los DEFAULT que `schema.sql` declara y la base no tiene tampoco se tocan: son
--  casi todos `gen_random_uuid()` en claves primarias que Hibernate genera desde
--  Java, asi que no llegan a usarse nunca. Añadirlos ahora solo serviria para que
--  un INSERT al que le falta una columna deje de fallar, que es lo contrario de
--  lo que interesa.


-- ------------------------------------------------------------
--  Comprobacion
-- ------------------------------------------------------------
--      SELECT t.relname, a.attname, c.conname,
--             CASE c.confdeltype WHEN 'a' THEN 'NO ACTION'
--                  WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL' END
--      FROM pg_constraint c
--      JOIN pg_class t ON t.oid = c.conrelid
--      JOIN unnest(c.conkey) AS k(attnum) ON true
--      JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
--      WHERE c.contype = 'f' AND t.relname = 'athlete_groups';
--      -- una sola por columna, llamada fk_..., y con CASCADE.
