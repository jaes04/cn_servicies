package es.jaes.cn_servicies.athlete_document;

import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/athlete-documents")
@RequiredArgsConstructor
public class AthleteDocumentController {

    private final AthleteDocumentService athleteDocumentService;

    @PostMapping(value = "/athlete/{athleteId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AthleteDocumentResponse> upload(
            @PathVariable UUID athleteId,
            @RequestParam String title,
            @RequestParam AthleteDocumentType type,
            @RequestParam MultipartFile file,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(athleteDocumentService.upload(athleteId, title, type, file, principal.getName()));
    }

    @GetMapping("/athlete/{athleteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_STAFF')")
    public ResponseEntity<List<AthleteDocumentResponse>> byAthlete(@PathVariable UUID athleteId) {
        return ResponseEntity.ok(athleteDocumentService.findByAthlete(athleteId));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AthleteDocumentResponse>> myDocuments(Principal principal) {
        return ResponseEntity.ok(athleteDocumentService.findByCurrentUser(principal.getName()));
    }

    @GetMapping("/{documentId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getFile(@PathVariable UUID documentId) throws IOException {
        Path path = athleteDocumentService.getFilePath(documentId);
        Resource resource = new UrlResource(path.toUri());
        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        athleteDocumentService.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}