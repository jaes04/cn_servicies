package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.GroupScheduleRequest;
import es.jaes.cn_servicies.training_group.GroupScheduleResponse;
import es.jaes.cn_servicies.training_group.GroupScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cambiar un horario y rehacer su calendario, <b>en una sola transaccion</b>
 * (tarea 2.2.b).
 *
 * <p><b>Por que existe esta clase.</b> El horario vive en {@code training_group}
 * y las sesiones en {@code training_session}, que ya depende del primero: si
 * {@code GroupScheduleService} llamara a la regeneracion, los dos modulos se
 * llamarian en circulo y Spring no arrancaria. Poner las dos llamadas seguidas
 * en el controlador tampoco vale, porque un controlador no es transaccional: el
 * cambio del horario haria commit por su cuenta y, si la regeneracion fallara
 * despues, el calendario se quedaria describiendo un horario que ya no existe.
 * Nadie lo veria hasta que un entrenador se presentara el dia equivocado.
 *
 * <p>Asi que la coordinacion vive aqui, del lado que ya conoce a los dos, y con
 * {@code @Transactional} propio: los servicios que se llaman debajo se unen a
 * esta transaccion, de modo que <b>o cambian las dos cosas o no cambia
 * ninguna</b>.
 *
 * <p>Lo que esto <b>no</b> da, y conviene saberlo: la regeneracion va pegada a
 * <i>esta llamada</i>, no al cambio del horario. Quien edite un horario yendo
 * directo a {@code GroupScheduleService} se la salta. Hoy no hay ningun camino
 * asi —el controlador entra por aqui— pero es la razon por la que existe un
 * test que lo comprueba.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleChangeService {

    private final GroupScheduleService scheduleService;
    private final TrainingSessionService sessionService;

    /**
     * Edita el horario y rehace sus sesiones futuras con los datos nuevos.
     *
     * <p>Las pasadas no se tocan: son el registro de lo que se entreno de
     * verdad.
     */
    public GroupScheduleResponse updateAndRegenerate(UUID groupId, UUID scheduleId,
                                                     GroupScheduleRequest request) {
        GroupScheduleResponse actualizado = scheduleService.update(groupId, scheduleId, request);
        int rehechas = sessionService.regenerateForSchedule(groupId, scheduleId);

        log.debug("Horario {} del grupo {} cambiado: {} sesión(es) futura(s) rehecha(s).",
                scheduleId, groupId, rehechas);
        return actualizado;
    }

    /**
     * Borra el horario y se lleva sus sesiones futuras, sin regenerarlas.
     *
     * <p>Cierra lo que la 2.1 dejo abierto: un horario borrado no puede seguir
     * poniendo entrenamientos en el calendario de las proximas semanas.
     */
    public void deleteAndDiscard(UUID groupId, UUID scheduleId) {
        scheduleService.softDelete(groupId, scheduleId);
        int descartadas = sessionService.discardFutureForSchedule(scheduleId);

        log.debug("Horario {} del grupo {} borrado: {} sesión(es) futura(s) descartada(s).",
                scheduleId, groupId, descartadas);
    }
}
