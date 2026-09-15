package es.jaes.cn_servicies.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardia de la tarea 2.6: que el esquema real sea el que dice
 * {@code schema.sql}.
 *
 * <p>Existe por un fallo que estuvo meses sin verse. Con
 * {@code defer-datasource-initialization=true}, ese archivo se ejecuta
 * <b>despues</b> de que Hibernate haya creado las tablas, asi que todos sus
 * {@code CREATE TABLE IF NOT EXISTS} no hacen nada — y con ellos se perdian en
 * silencio las claves foraneas con su {@code ON DELETE} y las restricciones
 * {@code UNIQUE}. El archivo declaraba 24 acciones de borrado y la base tenia 8.
 *
 * <p>Nada fallaba: la aplicacion arrancaba, los tests pasaban y las cascadas
 * simplemente no estaban el dia que hicieran falta.
 */
@SpringBootTest
class SchemaIntegrityTest {

    @Autowired private JdbcTemplate jdbc;

    /**
     * <b>La comprobacion estructural, y la que de verdad protege</b>: ninguna
     * columna puede tener dos claves foraneas.
     *
     * <p>Es exactamente la forma en que esto se rompe. Si alguien declara una
     * clave foranea en {@code schema.sql} y olvida poner
     * {@code ConstraintMode.NO_CONSTRAINT} en la entidad, Hibernate crea la suya
     * en paralelo — y con dos sobre la misma columna <b>manda la mas
     * restrictiva</b>, asi que la cascada se queda de adorno sin que nada avise.
     *
     * <p>No lleva lista de tablas a proposito: una lista hay que acordarse de
     * actualizarla, y este test existe precisamente para lo que se olvida.
     */
    @Test
    @DisplayName("ninguna columna tiene dos claves foráneas: nadie ha duplicado con Hibernate")
    void sinClavesForaneasDuplicadas() {
        List<String> duplicadas = jdbc.queryForList(
                "SELECT t.relname || '.' || a.attname"
                        + "     || ' tiene ' || count(*) || ': ' || string_agg(c.conname, ', ')"
                        + " FROM pg_constraint c"
                        + " JOIN pg_class t ON t.oid = c.conrelid"
                        + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                        + " JOIN unnest(c.conkey) AS k(attnum) ON true"
                        + " JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum"
                        + " WHERE n.nspname = 'public' AND c.contype = 'f'"
                        + " GROUP BY t.relname, a.attname"
                        + " HAVING count(*) > 1",
                String.class);

        assertThat(duplicadas)
                .as("una columna con dos claves foráneas obedece a la más restrictiva:"
                        + " falta ConstraintMode.NO_CONSTRAINT en la entidad,"
                        + " o pasar migrations/2.6-limpiar-fks-generadas.sql en este entorno")
                .isEmpty();
    }

    /** Lo mismo para los únicos, que es como empezó todo en la tarea 2.2. */
    @Test
    @DisplayName("ninguna tabla tiene dos restricciones únicas equivalentes")
    void sinUnicosDuplicados() {
        List<String> duplicados = jdbc.queryForList(
                "SELECT t.relname || ' -> ' || string_agg(c.conname, ', ')"
                        + " FROM pg_constraint c"
                        + " JOIN pg_class t ON t.oid = c.conrelid"
                        + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                        + " WHERE n.nspname = 'public' AND c.contype = 'u'"
                        + " GROUP BY t.relname, c.conkey"
                        + " HAVING count(*) > 1",
                String.class);

        assertThat(duplicados)
                .as("dos únicos sobre las mismas columnas: uno lo puso Hibernate y sobra")
                .isEmpty();
    }

    /**
     * El contrato de borrado del sistema. Esta lista <b>si</b> es explicita,
     * porque no es una regla estructural sino una decision: que se lleva por
     * delante cada borrado.
     *
     * <p>Son las que se ejercitan de verdad hoy — la regeneracion de horarios
     * borra sesiones futuras, y de ellas cuelga la asistencia. Las demas viven
     * bajo borrado logico y no llegan a probarse nunca.
     */
    @Test
    @DisplayName("las cascadas que el sistema ejercita están puestas")
    void lasCascadasQueImportan() {
        Map<String, String> esperado = Map.of(
                "attendance.session_id", "CASCADE",
                "attendance.athlete_id", "CASCADE",
                "attendance.registered_by_id", "SET NULL",
                "training_sessions.group_id", "CASCADE",
                "training_sessions.schedule_id", "SET NULL",
                "group_schedules.group_id", "CASCADE",
                "athlete_groups.athlete_id", "CASCADE",
                "athlete_groups.group_id", "CASCADE",
                "group_assistant_coaches.group_id", "CASCADE",
                "group_assistant_coaches.user_id", "CASCADE");

        esperado.forEach((columna, accion) -> {
            String[] partes = columna.split("\\.");
            String real = jdbc.queryForObject(
                    "SELECT CASE c.confdeltype WHEN 'a' THEN 'NO ACTION'"
                            + "      WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL' END"
                            + " FROM pg_constraint c"
                            + " JOIN pg_class t ON t.oid = c.conrelid"
                            + " JOIN unnest(c.conkey) AS k(attnum) ON true"
                            + " JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum"
                            + " WHERE c.contype = 'f' AND t.relname = ? AND a.attname = ?",
                    String.class, partes[0], partes[1]);

            assertThat(real)
                    .as("%s debería borrar con %s", columna, accion)
                    .isEqualTo(accion);
        });
    }
}
