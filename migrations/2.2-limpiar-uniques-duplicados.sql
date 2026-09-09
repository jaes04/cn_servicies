-- ============================================================
--  2.2 — Limpieza de restricciones unicas duplicadas
-- ============================================================
--  `training_groups` y `training_sessions` tenian su unico declarado DOS veces:
--  en `schema.sql` con nombre explicito, y otra vez en la anotacion @Table de la
--  entidad. De la segunda, Hibernate crea una restriccion equivalente con un
--  nombre generado —`ukof1fhv6qlrfln33gjxr2lcjw4`, `uk5a01qd9seiex615ick5rt6obp`—
--  que es distinto en cada base y no se puede nombrar en una migracion ni en un
--  ON CONFLICT.
--
--  Las anotaciones ya se han quitado, pero `ddl-auto=update` no borra lo que
--  dejo de estar declarado: solo añade. Hay que quitarlas a mano, una vez por
--  entorno.
--
--      psql -h localhost -U cn_app -d cn_test -f migrations/2.2-limpiar-uniques-duplicados.sql
--
--  Es idempotente: si ya se paso, no encuentra nada que borrar.
--
--  QUE SE BORRA Y QUE NO: solo las restricciones unicas cuyo nombre NO empieza
--  por `uk_`, que es el prefijo de las que declaramos nosotros en schema.sql. Es
--  decir, se van las generadas y se queda la nuestra. Si por lo que sea no
--  existiera la nuestra, el bloque avisa y no borra nada, para no dejar la tabla
--  sin proteccion.
-- ============================================================

DO $$
DECLARE
    tabla   text;
    generada record;
    nuestras int;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['training_groups', 'training_sessions'] LOOP

        SELECT count(*) INTO nuestras
        FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.contype = 'u' AND t.relname = tabla AND c.conname LIKE 'uk\_%';

        -- El indice con nombre propio puede estar creado como CREATE UNIQUE
        -- INDEX en vez de como restriccion; cuenta igual.
        IF nuestras = 0 THEN
            SELECT count(*) INTO nuestras
            FROM pg_indexes
            WHERE tablename = tabla AND indexname LIKE 'uk\_%';
        END IF;

        IF nuestras = 0 THEN
            RAISE WARNING
                'Saltando %: no se encuentra el unico con nombre propio, no se borra nada',
                tabla;
            CONTINUE;
        END IF;

        FOR generada IN
            SELECT c.conname
            FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.contype = 'u' AND t.relname = tabla AND c.conname NOT LIKE 'uk\_%'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', tabla, generada.conname);
            RAISE NOTICE 'Borrada la restriccion generada %.%', tabla, generada.conname;
        END LOOP;

    END LOOP;
END $$;


-- ------------------------------------------------------------
--  Comprobacion: cada tabla con un solo unico, y con nuestro nombre
-- ------------------------------------------------------------
--      SELECT tablename, indexname FROM pg_indexes
--      WHERE tablename IN ('training_groups','training_sessions')
--        AND indexdef LIKE '%UNIQUE%' ORDER BY 1, 2;
--
--  NO SE TOCAN `user_athletes` NI `athlete_guardians`, que tienen el problema
--  contrario: su unico existe SOLO con el nombre generado por Hibernate, porque
--  el de schema.sql va dentro de un CREATE TABLE IF NOT EXISTS que no llego a
--  ejecutarse. Borrarlas las dejaria sin restriccion. Arreglar eso pide sacar
--  esos unicos del CREATE TABLE a sentencias propias, y es trabajo aparte.
