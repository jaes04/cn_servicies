package es.jaes.cn_servicies.training_session;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    /** Lo registrado en una sesion. Es lo que el roster pinta junto a cada atleta. */
    @Query("SELECT a FROM Attendance a WHERE a.session.id = :sessionId")
    List<Attendance> findBySession(UUID sessionId);

    /**
     * Guarda el estado de un atleta en una sesion, creandolo o pisando el que
     * hubiera. <b>Atomico</b>.
     *
     * <p><b>Por que SQL nativo y no leer-y-guardar.</b> Dos entrenadores pasando
     * lista a la vez es el caso que hay que aguantar: si cada uno consulta lo que
     * hay y luego inserta, los dos ven la fila vacia y los dos insertan, y el
     * segundo se estrella contra el indice unico. Con {@code ON CONFLICT} lo
     * resuelve Postgres en una sola sentencia — gana el ultimo en escribir y
     * ninguno falla, que es exactamente lo que se quiere cuando dos personas
     * miran la misma piscina.
     *
     * <p>La alternativa en JPA seria capturar la violacion y reintentar, que es
     * mas codigo para un resultado peor.
     *
     * <p>Nativa pero <b>no</b> de las que preocupan a la regla 3: no es un SELECT
     * que pueda cruzar clubes. El {@code sessionId} viene de haber cargado la
     * sesion por el servicio, que si esta bajo el filtro y bajo RLS.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO attendance (id, session_id, athlete_id, status, registered_by_id, registered_at)
            VALUES (gen_random_uuid(), :sessionId, :athleteId, :status, :registeredBy, :registeredAt)
            ON CONFLICT (session_id, athlete_id) DO UPDATE
               SET status = EXCLUDED.status,
                   registered_by_id = EXCLUDED.registered_by_id,
                   registered_at = EXCLUDED.registered_at
            """, nativeQuery = true)
    void upsert(@Param("sessionId") UUID sessionId,
                @Param("athleteId") UUID athleteId,
                @Param("status") String status,
                @Param("registeredBy") UUID registeredBy,
                @Param("registeredAt") LocalDateTime registeredAt);
}
