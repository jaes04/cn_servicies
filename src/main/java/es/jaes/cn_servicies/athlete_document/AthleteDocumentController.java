package es.jaes.cn_servicies.athlete_document;

import es.jaes.cn_servicies.access.AccessGuard;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Documentos subidos a la ficha de un atleta.
 *
 * <p><b>Apagado por defecto</b> desde el bloque 3a
 * ({@code app.documents.upload.enabled=false}). Para el despliegue inicial el
 * club no sube papeles: registra que se entregaron y hasta cuando valen, en
 * {@code document_delivery}. Asi el sistema no guarda archivos que pueden
 * contener datos de salud de menores, ni el titulo y el nombre de archivo
 * libres que los acompañan.
 *
 * <p><b>No se ha borrado, se ha apagado</b>, para que no sea la unica solucion:
 * si un club necesita subir archivos, se enciende. Pero antes hay que resolver lo
 * que hoy le falta y que apagado no importa —cifrado en reposo, directorio
 * separado de las imagenes publicas y registro de accesos—; ver
 * {@code docs/rgpd.md} §1.
 *
 * <p>Apagado, <b>las cinco rutas contestan 404 a todo el mundo</b>, administrador
 * incluido, antes de mirar ningun permiso ni ningun id: no es que no se pueda, es
 * que la funcionalidad no esta.
 */
@RestController
@RequestMapping("/api/athlete-documents")
@RequiredArgsConstructor
public class AthleteDocumentController {

    private final AthleteDocumentService athleteDocumentService;
    private final AccessGuard accessGuard;

    @Value("${app.documents.upload.enabled:false}")
    private boolean uploadsEnabled;

    @PostMapping(value = "/athlete/{athleteId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AthleteDocumentResponse> upload(
            @PathVariable UUID athleteId,
            @RequestParam String title,
            @RequestParam AthleteDocumentType type,
            @RequestParam MultipartFile file,
            Principal principal) {
        requireUploadsEnabled();
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(athleteDocumentService.upload(athleteId, title, type, file, principal.getName()));
    }

    @GetMapping("/athlete/{athleteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_STAFF')")
    public ResponseEntity<List<AthleteDocumentResponse>> byAthlete(@PathVariable UUID athleteId) {
        requireUploadsEnabled();
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(athleteDocumentService.findByAthlete(athleteId));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AthleteDocumentResponse>> myDocuments(Principal principal) {
        requireUploadsEnabled();
        return ResponseEntity.ok(athleteDocumentService.findByCurrentUser(principal.getName()));
    }

    /**
     * Descarga del archivo.
     *
     * <p>El id viaja suelto en la URL y {@code athlete_documents} es tabla hija:
     * sin {@code club_id} ni policy, aqui no hay nada por debajo que tape una
     * fila ajena. La comprobacion de abajo es lo unico que separa un id de un
     * documento de un menor —incluido uno {@code MEDICAL}—, asi que no se toca
     * sin leer {@code docs/rgpd.md} §1.
     */
    @GetMapping("/{documentId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getFile(@PathVariable UUID documentId) throws IOException {
        requireUploadsEnabled();
        accessGuard.requireDocumentAccess(documentId);
        Path path = athleteDocumentService.getFilePath(documentId);
        Resource resource = new UrlResource(path.toUri());
        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    /**
     * El borrado es fisico y se lleva el archivo del disco. Con el id suelto y
     * la tabla sin policy, un id de otro club borraba de verdad: el servicio
     * solo mira {@code filename}, que no pasa por el atleta ni por RLS.
     */
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        requireUploadsEnabled();
        accessGuard.requireDocumentAccess(documentId);
        athleteDocumentService.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mismo mensaje que cualquier otro 404: apagado, un id valido y uno inventado
     * tienen que contestar igual.
     */
    private void requireUploadsEnabled() {
        if (!uploadsEnabled) {
            throw new EntityNotFoundException("Recurso no encontrado");
        }
    }
}
