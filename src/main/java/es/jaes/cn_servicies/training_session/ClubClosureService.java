package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendario de excepciones: los dias en que no se entrena (tarea 2.2.b).
 *
 * <p>Un cierre hace dos cosas, y conviene no confundirlas: <b>hacia el futuro</b>
 * el generador materializa esos dias como sesiones ya canceladas, y <b>sobre lo
 * ya generado</b> el alta del cierre cancela lo que encuentra. Sin la segunda,
 * declarar un festivo despues de que el job hubiera pasado por esa semana no
 * serviria de nada.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClubClosureService {

    /**
     * Mismo tope que el de la generacion. Un cierre de tres años no es un caso
     * real, es un cero de mas en una fecha, y aqui el precio de no verlo es
     * cancelar el curso entero.
     */
    private static final int MAXIMO_DIAS = 400;

    private final ClubClosureRepository closureRepository;
    private final TrainingSessionService sessionService;
    private final ClubService clubService;

    /**
     * Declara un cierre y cancela lo que ya estaba generado dentro de el.
     *
     * <p>Devuelve cuantas sesiones ha tumbado: quien se equivoca de fechas tiene
     * que enterarse en ese momento, no en marzo. Y si se equivoco, la
     * reactivacion de cada sesion es la vuelta atras.
     */
    public ClubClosureResponse create(ClubClosureRequest request) {
        Club club = clubService.getById(TenantContext.require());
        validar(request);

        ClubClosure cierre = new ClubClosure();
        cierre.setClub(club);
        cierre.setStartDate(request.getStartDate());
        cierre.setEndDate(request.getEndDate());
        cierre.setReason(request.getReason());
        cierre.setModality(request.getModality());

        ClubClosure guardado = closureRepository.save(cierre);
        int canceladas = sessionService.cancelByClosure(guardado);

        ClubClosureResponse response = toResponse(guardado);
        response.setCancelledSessions(canceladas);
        return response;
    }

    /**
     * Borra el cierre. <b>No reactiva ninguna sesion</b>, y no es un olvido: no
     * hay forma de distinguir las que cayeron por este cierre de las que alguien
     * cancelo a mano ese mismo dia por otro motivo. Reactivar es una decision
     * sesion a sesion, y tiene su propio endpoint.
     *
     * <p>Lo que si consigue es que el generador deje de tumbar esos dias de aqui
     * en adelante.
     */
    public void delete(UUID id) {
        closureRepository.delete(findOrThrow(id));
    }

    /** Los cierres que tocan el rango. */
    @Transactional(readOnly = true)
    public List<ClubClosureResponse> findAll(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Hacen falta las dos fechas del rango");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("El fin del rango es anterior al inicio");
        }
        return closureRepository.findOverlapping(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    private ClubClosure findOrThrow(UUID id) {
        return closureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cierre no encontrado"));
    }

    private void validar(ClubClosureRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(
                    "El fin del cierre no puede ser anterior al inicio");
        }
        if (request.getStartDate().plusDays(MAXIMO_DIAS).isBefore(request.getEndDate())) {
            throw new IllegalArgumentException(
                    "El cierre no puede durar más de " + MAXIMO_DIAS + " días");
        }
    }

    private ClubClosureResponse toResponse(ClubClosure cierre) {
        ClubClosureResponse response = new ClubClosureResponse();
        response.setId(cierre.getId());
        response.setStartDate(cierre.getStartDate());
        response.setEndDate(cierre.getEndDate());
        response.setReason(cierre.getReason());
        response.setModality(cierre.getModality());
        return response;
    }
}
