package es.jaes.cn_servicies.athlete_document;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.athlete_link.UserAthleteRepository;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AthleteDocumentService {

    private final AthleteDocumentRepository documentRepository;
    private final AthleteService athleteService;
    private final UserRepository userRepository;
    private final UserAthleteRepository userAthleteRepository;
    private final DocumentStorageService documentStorageService;

    public AthleteDocumentResponse upload(UUID athleteId, String title, AthleteDocumentType type,
                                          MultipartFile file, String username) {
        Athlete athlete = athleteService.findOrThrow(athleteId);
        User uploadedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        String filename;
        try {
            filename = documentStorageService.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el documento", e);
        }

        AthleteDocument document = new AthleteDocument();
        document.setTitle(title);
        document.setType(type);
        document.setFilename(filename);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setAthlete(athlete);
        document.setUploadedBy(uploadedBy);

        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<AthleteDocumentResponse> findByAthlete(UUID athleteId) {
        return documentRepository.findByAthleteId(athleteId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AthleteDocumentResponse> findByCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        List<UUID> athleteIds = userAthleteRepository.findByUserId(user.getId()).stream()
                .map(link -> link.getAthlete().getId())
                .toList();
        return documentRepository.findByAthleteIdIn(athleteIds).stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(UUID documentId) {
        AthleteDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));
        documentStorageService.delete(document.getFilename());
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public Path getFilePath(UUID documentId) {
        AthleteDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));
        return documentStorageService.resolve(document.getFilename());
    }

    private AthleteDocumentResponse toResponse(AthleteDocument doc) {
        AthleteDocumentResponse response = new AthleteDocumentResponse();
        response.setId(doc.getId());
        response.setTitle(doc.getTitle());
        response.setType(doc.getType());
        response.setOriginalFilename(doc.getOriginalFilename());
        response.setAthleteId(doc.getAthlete().getId());
        response.setAthleteFullName(doc.getAthlete().getFirstName() + " " + doc.getAthlete().getLastName());
        response.setUploadedById(doc.getUploadedBy().getId());
        response.setUploadedByUsername(doc.getUploadedBy().getUsername());
        response.setCreatedAt(doc.getCreatedAt());
        return response;
    }
}