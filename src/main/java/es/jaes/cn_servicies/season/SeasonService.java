package es.jaes.cn_servicies.season;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SeasonService {

    /**
     * Mes en que arranca la temporada por defecto al sembrarla. Septiembre es lo
     * habitual en un club de natacion español, pero es solo el valor de partida:
     * las fechas se editan despues como cualquier otro campo.
     */
    private static final Month SEASON_START_MONTH = Month.SEPTEMBER;

    /**
     * Las temporadas del club van una detras de otra —de septiembre a agosto— y
     * no se solapan nunca. Con eso, la pregunta "¿en que temporada estamos?"
     * tiene una sola respuesta posible por fecha.
     *
     * <p>Aun asi, <b>la temporada en curso se resuelve por la bandera
     * {@code active}, nunca deduciendola de la fecha de hoy.</b> La validacion
     * evita el error de tecleo; que la busqueda no dependa de las fechas es lo
     * que hace que no haya ambiguedad que resolver.
     */
    private static final String SOLAPE =
            "Las fechas se solapan con otra temporada del club";

    private final SeasonRepository seasonRepository;
    private final ClubService clubService;

    public SeasonResponse create(SeasonRequest request) {
        return toResponse(create(clubService.getById(TenantContext.require()), request, false));
    }

    /**
     * Alta con el club explicito, para quien no viene de una peticion — hoy la
     * siembra del primer arranque. Misma solucion que en la 0.8: el club se pasa
     * en vez de sacarlo del contexto, que ahi no existe.
     */
    public Season create(Club club, SeasonRequest request, boolean activate) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException(
                    "La fecha de fin tiene que ser posterior a la de inicio");
        }
        if (seasonRepository.existsByClubAndName(club, request.getName())) {
            throw new IllegalArgumentException("Ya existe una temporada con ese nombre");
        }
        if (seasonRepository.existsByClubAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                club, request.getEndDate(), request.getStartDate())) {
            throw new IllegalArgumentException(SOLAPE);
        }

        Season season = new Season();
        season.setClub(club);
        season.setName(request.getName());
        season.setStartDate(request.getStartDate());
        season.setEndDate(request.getEndDate());
        season.setActive(false);

        Season guardada = seasonRepository.save(season);
        return activate ? activate(club, guardada) : guardada;
    }

    /**
     * Marca esta como la temporada en curso y apaga la que lo estuviera.
     *
     * <p>El orden importa y no es intercambiable: primero se apagan todas y
     * despues se enciende esta. Al reves, el indice unico parcial rechazaria la
     * segunda activa antes de que la primera se hubiera apagado.
     */
    public SeasonResponse activate(UUID id) {
        Season season = findOrThrow(id);
        return toResponse(activate(season.getClub(), season));
    }

    private Season activate(Club club, Season season) {
        seasonRepository.deactivateAll(club);
        season.setActive(true);
        return seasonRepository.save(season);
    }

    /**
     * Asegura que el club tiene una temporada, y solo si no tiene ninguna. Es
     * idempotente a proposito: se ejecuta en cada arranque y no puede ir
     * creando una temporada al año por su cuenta.
     */
    public void ensureCurrentSeason(Club club) {
        if (seasonRepository.countByClub(club) > 0) {
            return;
        }

        LocalDate hoy = LocalDate.now();
        // De septiembre en adelante la temporada es la que empieza este año; de
        // enero a agosto seguimos dentro de la que empezo el año pasado.
        int inicio = hoy.getMonthValue() >= SEASON_START_MONTH.getValue()
                ? hoy.getYear()
                : hoy.getYear() - 1;

        SeasonRequest request = new SeasonRequest();
        request.setName(inicio + "/" + (inicio + 1));
        request.setStartDate(LocalDate.of(inicio, SEASON_START_MONTH, 1));
        request.setEndDate(LocalDate.of(inicio + 1, Month.AUGUST, 31));

        create(club, request, true);
        log.info("Temporada '{}' sembrada para el club {}.", request.getName(), club.getId());
    }

    @Transactional(readOnly = true)
    public List<SeasonResponse> findAll() {
        return seasonRepository.findAllByOrderByStartDateDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SeasonResponse findById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /** La temporada en curso del club. Vacia si el club todavia no ha activado ninguna. */
    @Transactional(readOnly = true)
    public SeasonResponse findActive() {
        return toResponse(seasonRepository.findByActiveTrue()
                .orElseThrow(() -> new EntityNotFoundException("No hay ninguna temporada activa")));
    }

    /**
     * La temporada en curso, o vacio si el club no tiene ninguna activa.
     *
     * <p>Lo mismo que {@link #findActive()} pero sin excepcion: para el job de
     * generacion de sesiones, que recorre todos los clubes y se encuentra
     * clubes recien creados sin temporada. Ahi no tener temporada activa no es
     * un error que reportar, es un club por el que hoy no hay que pasar.
     */
    @Transactional(readOnly = true)
    public Optional<Season> findActiveSeason() {
        return seasonRepository.findByActiveTrue();
    }

    public SeasonResponse update(UUID id, SeasonRequest request) {
        Season season = findOrThrow(id);

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException(
                    "La fecha de fin tiene que ser posterior a la de inicio");
        }
        if (!season.getName().equals(request.getName())
                && seasonRepository.existsByClubAndName(season.getClub(), request.getName())) {
            throw new IllegalArgumentException("Ya existe una temporada con ese nombre");
        }
        // Sin contarse a si misma: al editar unas fechas, la temporada siempre
        // se solapa con la que ya era.
        if (seasonRepository.existsByClubAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                season.getClub(), season.getId(), request.getEndDate(), request.getStartDate())) {
            throw new IllegalArgumentException(SOLAPE);
        }

        season.setName(request.getName());
        season.setStartDate(request.getStartDate());
        season.setEndDate(request.getEndDate());
        return toResponse(seasonRepository.save(season));
    }

    public Season findOrThrow(UUID id) {
        return seasonRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Temporada no encontrada"));
    }

    private SeasonResponse toResponse(Season season) {
        SeasonResponse response = new SeasonResponse();
        response.setId(season.getId());
        response.setName(season.getName());
        response.setStartDate(season.getStartDate());
        response.setEndDate(season.getEndDate());
        response.setActive(season.isActive());
        return response;
    }
}
