package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.TrainingGroupResponse;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Mantiene generadas las proximas semanas de entrenamientos, sin que nadie
 * tenga que acordarse (tarea 2.2.b).
 *
 * <p>Se apoya entero en que la generacion es idempotente: pasa todas las noches
 * por el mismo rango, que se solapa casi por completo con el de ayer, y lo que
 * ya existe no se toca. Sin esa garantia, este job seria una maquina de duplicar
 * sesiones.
 *
 * <p><b>Fija el club a mano en cada vuelta.</b> Aqui no hay peticion HTTP ni
 * JWT, asi que no hay nadie que haya puesto el {@code TenantContext}: lo pone
 * este bucle, y {@code ClubFilterAspect} se encarga del resto al entrar en cada
 * metodo transaccional. El {@code clear()} va en un {@code finally} porque el
 * contexto es un {@code ThreadLocal} y los hilos del planificador se reutilizan:
 * un club que se quedara pegado se lo llevaria el siguiente en ejecutarse.
 *
 * <p><b>Un club que falle no puede tumbar a los demas.</b> Por eso el try/catch
 * por club: si el de Sierra Oeste tiene un horario imposible, el resto de
 * clientes tiene que amanecer con su calendario hecho igualmente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.sessions.generation.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SessionGenerationJob {

    private final ClubService clubService;
    private final SeasonService seasonService;
    private final TrainingGroupService groupService;
    private final TrainingSessionService sessionService;

    @Value("${app.sessions.generation.weeks-ahead:6}")
    private int semanas;

    /**
     * De madrugada, cuando nadie esta pasando lista. La hora exacta da igual
     * mientras no coincida con el uso normal: generar toca muchas filas.
     */
    @Scheduled(cron = "${app.sessions.generation.cron:0 30 3 * * *}")
    public void generarProximasSemanas() {
        List<Club> clubes = clubService.findAllActive();
        log.info("Generación de sesiones: empezando con {} club(es).", clubes.size());

        int total = 0;
        for (Club club : clubes) {
            try {
                total += generarPara(club);
            } catch (RuntimeException e) {
                // Tragado a proposito, y con el club en el mensaje: lo que no
                // puede pasar es que un club roto deje sin calendario al resto.
                log.error("Generación de sesiones fallida para el club {} ({}): {}",
                        club.getSlug(), club.getId(), e.getMessage(), e);
            }
        }

        log.info("Generación de sesiones: {} sesión(es) nueva(s).", total);
    }

    /**
     * Genera el horizonte de un club. Publico para poder dispararlo desde un
     * test sin esperar al cron —un test que dependiera del reloj no seria un
     * test— y para el dia en que haya un endpoint de mantenimiento.
     */
    public int generarPara(Club club) {
        TenantContext.set(club.getId());
        try {
            Optional<Season> temporada = seasonService.findActiveSeason();
            if (temporada.isEmpty()) {
                // No es un error: un club recien creado todavia no tiene
                // temporada, y no hay nada que generarle.
                log.debug("El club {} no tiene temporada activa; nada que generar.",
                        club.getSlug());
                return 0;
            }

            LocalDate desde = LocalDate.now();
            LocalDate hasta = desde.plusWeeks(semanas);

            int creadas = 0;
            for (TrainingGroupResponse grupo : groupService.findAll(temporada.get().getId())) {
                creadas += sessionService.generate(grupo.getId(), desde, hasta).getCreated();
            }
            return creadas;

        } finally {
            TenantContext.clear();
        }
    }
}
