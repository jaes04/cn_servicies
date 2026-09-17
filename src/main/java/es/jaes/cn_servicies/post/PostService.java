package es.jaes.cn_servicies.post;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final ClubService clubService;

    @Value("${app.base-url}")
    private String baseUrl;

    public PostResponse create(PostRequest request, List<MultipartFile> images, String username) {
        if (images != null && images.size() > 10) {
            throw new IllegalArgumentException("Un post no puede tener más de 10 imágenes");
        }

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Post post = new Post();
        // El post pertenece al club de quien lo escribe. Aqui no hace falta el
        // club por defecto: el autor ya trae el suyo.
        post.setClub(author.getClub());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setSlug(generateSlug(request.getTitle()));
        post.setStatus(PostStatus.DRAFT);
        post.setAuthor(author);

        Post saved = postRepository.saveAndFlush(post);

        if (images != null) {
            images.forEach(file -> {
                String filename = imageStorageService.save(file);
                PostImage image = new PostImage();
                image.setFilename(filename);
                image.setOriginalFilename(file.getOriginalFilename());
                image.setPost(saved);
                postImageRepository.save(image);
            });
        }

        return toResponse(postRepository.findById(saved.getId()).orElseThrow());
    }

    public PostResponse update(UUID id, PostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post no encontrado"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setSlug(generateSlug(request.getTitle()));

        if (request.getStatus() != null) {
            post.setStatus(request.getStatus());
            if (request.getStatus() == PostStatus.PUBLISHED && post.getPublishedAt() == null) {
                post.setPublishedAt(LocalDateTime.now());
            }
        }

        return toResponse(postRepository.save(post));
    }

    public void softDelete(UUID id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post no encontrado"));
        post.setStatus(PostStatus.DELETED);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    /**
     * Blog publico de un club. Lo sirve un endpoint anonimo, asi que no hay
     * filtro de Hibernate y las policies estan en modo {@code public}: sin la
     * condicion del club saldrian las noticias de todos.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> listPublished(String clubSlug, String q, String author, Pageable pageable) {
        Club club = clubService.getActiveBySlug(clubSlug);
        Specification<Post> spec = Specification.where(PostSpecification.belongsToClub(club.getId()))
                .and(PostSpecification.hasStatus(PostStatus.PUBLISHED));
        if (q != null && !q.isBlank()) spec = spec.and(PostSpecification.titleOrContentContains(q));
        if (author != null && !author.isBlank()) spec = spec.and(PostSpecification.hasAuthor(author));
        return postRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> listAll(String q, String author, PostStatus status, Pageable pageable) {
        Specification<Post> spec = Specification.where(null);
        if (q != null && !q.isBlank()) spec = spec.and(PostSpecification.titleOrContentContains(q));
        if (author != null && !author.isBlank()) spec = spec.and(PostSpecification.hasAuthor(author));
        if (status != null) spec = spec.and(PostSpecification.hasStatus(status));
        return postRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * Resolucion publica del post, por id. Antes era por slug, que dejo de ser
     * identificador al poder repetirse entre clubes.
     *
     * <p>Exige que este PUBLISHED: este metodo lo sirve un endpoint anonimo y
     * un borrador no puede salir por ahi. Para ver uno sin publicar esta
     * {@link #findById}, detras de autenticacion.
     *
     * <p>Exige tambien que sea del club del slug: con el id de una noticia de
     * otro club responde 404, igual que si no existiera.
     */
    @Transactional(readOnly = true)
    public PostResponse findPublishedById(String clubSlug, UUID id) {
        Club club = clubService.getActiveBySlug(clubSlug);
        Post post = postRepository.findByIdAndStatusAndClubId(id, PostStatus.PUBLISHED, club.getId())
                .orElseThrow(() -> new EntityNotFoundException("Post no encontrado"));
        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public PostResponse findById(UUID id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post no encontrado"));
        return toResponse(post);
    }

    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u")
                .replaceAll("ñ", "n")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .strip();
    }

    private PostResponse toResponse(Post post) {
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setSlug(post.getSlug());
        response.setStatus(post.getStatus());
        response.setAuthorUsername(post.getAuthor().getUsername());
        response.setImageUrls(postImageRepository.findByPostIdOrderByCreatedAtAsc(post.getId())
                .stream()
                .map(img -> "/api/images/" + img.getFilename())
                .toList());
        response.setPublishedAt(post.getPublishedAt());
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());
        return response;
    }
}
