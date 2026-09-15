package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentDeliveryService {

    /** Edad a partir de la cual un atleta viaja sin permiso de sus padres. */
    static final int MAYORIA_DE_EDAD = 18;

    private final DocumentDeliveryRepository deliveryRepository;
    private final ClubService clubService;
    private final SeasonService seasonService;
    private final UserService userService;

    /**
     * Registra la entrega de un papel. Recibe el atleta ya resuelto y no su id,
     * por lo mismo que {@code MedicalCertificateService.register}: buscarlo aqui
     * crearia una dependencia hacia {@code AthleteService} que en algun momento
     * vuelve.
     *
     * @param registrar quien del club lo anota. Hoy siempre es quien hace la
     *                  peticion: anotarlo <em>es</em> dar el papel por visto.
     */
    public DocumentDelivery register(Athlete athlete, DocumentDeliveryRequest request, String registrar) {
        Season season = validar(athlete, request);

        DocumentDelivery delivery = new DocumentDelivery();
        delivery.setClub(clubService.getById(TenantContext.require()));
        delivery.setAthlete(athlete);
        delivery.setType(request.getType());
        delivery.setSeason(season);
        delivery.setValidFrom(request.getValidFrom());
        delivery.setValidUntil(request.getValidUntil());
        delivery.setDeliveredOn(request.getDeliveredOn());
        delivery.setRegisteredBy(userService.findEntityByUsername(registrar));
        delivery.setRegisteredAt(LocalDateTime.now());

        return deliveryRepository.save(delivery);
    }

    /**
     * Cada tipo lleva sus campos y ningun otro. Se rechaza en vez de ignorar lo
     * que sobra: aceptar una licencia con fechas y no hacer nada con ellas dejaria
     * creer que esas fechas cuentan.
     *
     * @return la temporada, en la licencia; {@code null} en los demas
     */
    private Season validar(Athlete athlete, DocumentDeliveryRequest request) {
        return switch (request.getType()) {
            case LICENSE_APPLICATION -> {
                if (request.getSeasonId() == null) {
                    throw new IllegalArgumentException(
                            "La solicitud de licencia vale por temporada: falta la temporada");
                }
                if (request.getValidFrom() != null || request.getValidUntil() != null) {
                    throw new IllegalArgumentException(
                            "La solicitud de licencia vale para su temporada y no lleva fechas propias");
                }
                // Por el servicio: una temporada de otro club sale como no encontrada.
                yield seasonService.findOrThrow(request.getSeasonId());
            }
            case IDENTITY_DOCUMENT -> {
                if (request.getValidUntil() == null) {
                    throw new IllegalArgumentException(
                            "Falta la fecha de caducidad del documento de identidad");
                }
                if (request.getSeasonId() != null || request.getValidFrom() != null) {
                    throw new IllegalArgumentException(
                            "El documento de identidad solo lleva su fecha de caducidad");
                }
                yield null;
            }
            case TRAVEL_PERMIT -> {
                if (request.getValidFrom() == null || request.getValidUntil() == null) {
                    throw new IllegalArgumentException(
                            "El permiso de viaje necesita el día de salida y el de vuelta");
                }
                if (request.getSeasonId() != null) {
                    throw new IllegalArgumentException(
                            "El permiso de viaje va por fechas, no por temporada");
                }
                if (request.getValidUntil().isBefore(request.getValidFrom())) {
                    throw new IllegalArgumentException(
                            "El día de vuelta no puede ser anterior al de salida");
                }
                // Minimizacion: el permiso de un adulto no hace falta para nada, y
                // guardarlo seria guardar sus fechas de viaje sin motivo.
                if (!esMenorEn(athlete.getBirthDate(), request.getValidFrom())) {
                    throw new IllegalArgumentException(
                            "El atleta es mayor de edad el día de salida: no necesita permiso de viaje");
                }
                yield null;
            }
        };
    }

    /** Historial completo, con fechas y quien lo anoto. */
    @Transactional(readOnly = true)
    public List<DocumentDeliveryResponse> historyForAthlete(UUID athleteId) {
        LocalDate hoy = LocalDate.now();
        return deliveryRepository.findByAthleteIdOrderByDeliveredOnDesc(athleteId).stream()
                .map(delivery -> toResponse(delivery, hoy))
                .toList();
    }

    /**
     * Si el atleta tiene hoy en regla lo que se le pide siempre: la licencia de la
     * temporada activa y el documento de identidad.
     *
     * <p>El permiso de viaje no esta, y no es un olvido: no se le pide a nadie
     * hasta que hay un viaje. Esa pregunta es {@link #travelPermitCoverage}.
     */
    @Transactional(readOnly = true)
    public Map<DocumentDeliveryType, DocumentDeliveryStatus> statusForAthlete(Athlete athlete) {
        return estadoSegun(athlete,
                deliveryRepository.findByAthleteIdOrderByDeliveredOnDesc(athlete.getId()),
                seasonService.findActiveSeason(), LocalDate.now());
    }

    /**
     * El estado de muchos atletas de una vez, con una sola consulta de entregas.
     * Lo usa el informe de documentacion pendiente (bloque 3c).
     *
     * <p><b>Pasa por el mismo {@link #estadoSegun} que la pregunta de uno en
     * uno</b>: si fueran dos criterios, el aviso de pendientes y la ficha del atleta
     * acabarian diciendo cosas distintas.
     *
     * <p>Recibe las fichas y no los ids porque el documento de identidad depende de
     * si la ficha tiene numero.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Map<DocumentDeliveryType, DocumentDeliveryStatus>> statusesForAthletes(
            Collection<Athlete> athletes) {
        if (athletes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = athletes.stream().map(Athlete::getId).toList();
        Map<UUID, List<DocumentDelivery>> porAtleta = deliveryRepository.findByAthleteIdIn(ids).stream()
                .collect(Collectors.groupingBy(entrega -> entrega.getAthlete().getId()));

        Optional<Season> activa = seasonService.findActiveSeason();
        LocalDate hoy = LocalDate.now();

        Map<UUID, Map<DocumentDeliveryType, DocumentDeliveryStatus>> estados = new HashMap<>();
        for (Athlete athlete : athletes) {
            estados.put(athlete.getId(),
                    estadoSegun(athlete, porAtleta.getOrDefault(athlete.getId(), List.of()), activa, hoy));
        }
        return estados;
    }

    /**
     * Si el atleta necesita permiso para viajar entre esas fechas, y si lo tiene.
     *
     * <p>Se mira la edad <b>el dia de salida</b>: quien cumple 18 a mitad del viaje
     * sale siendo menor, y es a la salida cuando se lo piden.
     */
    @Transactional(readOnly = true)
    public TravelPermitCoverageResponse travelPermitCoverage(Athlete athlete, LocalDate salida, LocalDate vuelta) {
        if (vuelta.isBefore(salida)) {
            throw new IllegalArgumentException("El día de vuelta no puede ser anterior al de salida");
        }
        boolean requerido = esMenorEn(athlete.getBirthDate(), salida);
        boolean cubierto = deliveryRepository
                .existsByAthleteIdAndTypeAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(
                        athlete.getId(), DocumentDeliveryType.TRAVEL_PERMIT, salida, vuelta);
        return new TravelPermitCoverageResponse(salida, vuelta, requerido, cubierto);
    }

    // ----------------------------------------------------------------
    //  Reglas
    // ----------------------------------------------------------------

    private static Map<DocumentDeliveryType, DocumentDeliveryStatus> estadoSegun(
            Athlete athlete, List<DocumentDelivery> entregas, Optional<Season> activa, LocalDate hoy) {
        Map<DocumentDeliveryType, DocumentDeliveryStatus> estado = new EnumMap<>(DocumentDeliveryType.class);
        estado.put(DocumentDeliveryType.LICENSE_APPLICATION,
                licenseStatus(delTipo(entregas, DocumentDeliveryType.LICENSE_APPLICATION), activa, hoy));
        estado.put(DocumentDeliveryType.IDENTITY_DOCUMENT,
                identityStatus(athlete, delTipo(entregas, DocumentDeliveryType.IDENTITY_DOCUMENT), hoy));
        return estado;
    }

    private static List<DocumentDelivery> delTipo(List<DocumentDelivery> entregas, DocumentDeliveryType tipo) {
        return entregas.stream().filter(entrega -> entrega.getType() == tipo).toList();
    }

    /**
     * La licencia que manda es la de la <b>temporada activa</b>, nunca la ultima
     * registrada: anotar en agosto la del curso que viene no puede dar por buena
     * la de este.
     *
     * <p>Si no la tiene pero trajo alguna antes, {@code EXPIRED}; si nunca trajo
     * ninguna, {@code MISSING}. La diferencia le sirve al club en septiembre: a
     * unos hay que pedirles que renueven y a otros que la traigan por primera vez.
     */
    private static DocumentDeliveryStatus licenseStatus(List<DocumentDelivery> licencias,
                                                        Optional<Season> activa, LocalDate hoy) {
        if (licencias.isEmpty()) {
            return DocumentDeliveryStatus.MISSING;
        }
        if (activa.isEmpty()) {
            // Sin temporada activa no hay "la de este curso" contra la que medir:
            // se cuenta la que mas lejos llega, como con el documento de identidad.
            return statusOf(masLejana(licencias), hoy);
        }

        UUID temporadaActiva = activa.get().getId();
        return licencias.stream()
                .filter(licencia -> licencia.getSeason().getId().equals(temporadaActiva))
                .findFirst()
                .map(licencia -> statusOf(licencia.lastValidDay(), hoy))
                .orElse(DocumentDeliveryStatus.EXPIRED);
    }

    /**
     * Manda el que mas lejos caduca, no el ultimo registrado: teclear hoy la
     * fotocopia del DNI anterior no puede empeorar el estado.
     *
     * <p>Si la ficha no tiene numero de documento, no se exige.
     */
    private static DocumentDeliveryStatus identityStatus(Athlete athlete, List<DocumentDelivery> documentos,
                                                         LocalDate hoy) {
        if (athlete.getDni() == null || athlete.getDni().isBlank()) {
            return DocumentDeliveryStatus.NOT_REQUIRED;
        }
        return documentos.isEmpty()
                ? DocumentDeliveryStatus.MISSING
                : statusOf(masLejana(documentos), hoy);
    }

    private static LocalDate masLejana(List<DocumentDelivery> entregas) {
        return entregas.stream()
                .map(DocumentDelivery::lastValidDay)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    /**
     * Estado de un papel segun su ultimo dia de validez, <b>que va incluido</b>:
     * el dia que caduca todavia vale. Mismos extremos que el certificado medico.
     */
    static DocumentDeliveryStatus statusOf(LocalDate ultimoDiaValido, LocalDate hoy) {
        if (ultimoDiaValido.isBefore(hoy)) {
            return DocumentDeliveryStatus.EXPIRED;
        }
        if (ultimoDiaValido.isBefore(hoy.plusDays(DocumentDelivery.EXPIRY_WARNING_DAYS))) {
            return DocumentDeliveryStatus.EXPIRING_SOON;
        }
        return DocumentDeliveryStatus.VALID;
    }

    /** Menor de edad en esa fecha. El dia en que cumple 18 ya no lo es. */
    static boolean esMenorEn(LocalDate nacimiento, LocalDate fecha) {
        return nacimiento.plusYears(MAYORIA_DE_EDAD).isAfter(fecha);
    }

    private DocumentDeliveryResponse toResponse(DocumentDelivery delivery, LocalDate hoy) {
        DocumentDeliveryResponse response = new DocumentDeliveryResponse();
        response.setId(delivery.getId());
        response.setAthleteId(delivery.getAthlete().getId());
        response.setType(delivery.getType());
        if (delivery.getSeason() != null) {
            response.setSeasonId(delivery.getSeason().getId());
            response.setSeasonName(delivery.getSeason().getName());
        }
        response.setValidFrom(delivery.getValidFrom());
        response.setValidUntil(delivery.getValidUntil());
        response.setDeliveredOn(delivery.getDeliveredOn());
        response.setStatus(statusOf(delivery.lastValidDay(), hoy));
        response.setRegisteredBy(delivery.getRegisteredBy().getUsername());
        response.setRegisteredAt(delivery.getRegisteredAt());
        return response;
    }

    /** Para el alta: la respuesta de lo que se acaba de registrar. */
    @Transactional(readOnly = true)
    public DocumentDeliveryResponse toResponse(DocumentDelivery delivery) {
        return toResponse(delivery, LocalDate.now());
    }
}
