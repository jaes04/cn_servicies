package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.post.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService imageStorageService;

    @Value("${app.base-url}")
    private String baseUrl;

    public UserResponse create(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("El username ya está en uso");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El email ya está en uso");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        Set<RoleName> roleNames = (request.getRoles() != null && !request.getRoles().isEmpty())
                ? request.getRoles()
                : Set.of(RoleName.ROLE_USER);

        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + name)))
                .collect(Collectors.toSet());

        user.setRoles(roles);

        return toResponse(userRepository.saveAndFlush(user));
    }

    public UserResponse findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserResponse> findAll(
            String q, String role, Boolean blocked,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<User> spec =
                org.springframework.data.jpa.domain.Specification.where(null);
        if (q != null && !q.isBlank()) spec = spec.and(UserSpecification.usernameOrEmailContains(q));
        if (role != null && !role.isBlank()) spec = spec.and(UserSpecification.hasRole(role));
        if (blocked != null) spec = spec.and(UserSpecification.isBlocked(blocked));
        return userRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> roles = request.getRoles().stream()
                    .map(name -> roleRepository.findByName(name)
                            .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + name)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        return toResponse(userRepository.save(user));
    }

    public UserResponse uploadProfilePhoto(UUID id, MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        if (user.getProfilePhoto() != null) {
            imageStorageService.delete(user.getProfilePhoto());
        }

        String filename = imageStorageService.save(file);
        user.setProfilePhoto(filename);
        return toResponse(userRepository.save(user));
    }

    public UserResponse changeRole(UUID id, ChangeRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRole()));

        user.setRoles(new java.util.HashSet<>(Set.of(role)));
        return toResponse(userRepository.save(user));
    }

    public UserResponse blockUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        user.setBlocked(true);
        return toResponse(userRepository.save(user));
    }

    public UserResponse unblockUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        user.setBlocked(false);
        return toResponse(userRepository.save(user));
    }

    public void softDelete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setBlocked(user.isBlocked());
        response.setRoles(user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet()));
        response.setCreatedAt(user.getCreatedAt());
        response.setProfilePhoto(user.getProfilePhoto() != null
                ? "/api/images/" + user.getProfilePhoto()
                : null);
        return response;
    }
}