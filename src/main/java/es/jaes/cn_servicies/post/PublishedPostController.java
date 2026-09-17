package es.jaes.cn_servicies.post;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Blog publico, sin autenticacion. Va aparte de {@link PostController} porque
 * cuelga del club y no de {@code /api/posts}.
 *
 * <p><b>El club llega en la ruta, por su slug.</b> Cada frontend lleva el de su
 * club configurado. Una peticion anonima no tiene club en el
 * {@code TenantContext}, y sin el las policies dejan ver las noticias de todos:
 * el slug es lo que las acota. Solo se usa para eso; nunca fija el club de la
 * peticion.
 *
 * <p>Un slug que no existe, o de un club de baja, responde 404.
 */
@RestController
@RequestMapping("/api/clubs/{clubSlug}/posts/published")
@RequiredArgsConstructor
public class PublishedPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<Page<PostResponse>> listPublished(
            @PathVariable String clubSlug,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String author,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(postService.listPublished(clubSlug, q, author, pageable));
    }

    /**
     * Detalle publico de una noticia, por id. El slug del post no la identifica:
     * puede repetirse entre clubes.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPublishedById(@PathVariable String clubSlug, @PathVariable UUID id) {
        return ResponseEntity.ok(postService.findPublishedById(clubSlug, id));
    }
}
