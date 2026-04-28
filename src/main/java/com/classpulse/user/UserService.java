package com.classpulse.user;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.NotFoundException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;

    private final UserRepository userRepository;
    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String minioBucket;

    // T027
    public UserDto getMe(UUID userId) {
        User user = findById(userId);
        return UserDto.from(user, computeStats(user));
    }

    // T028
    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findById(userId);
        user.setName(request.getName());
        if (request.getAvatarColor() != null) {
            user.setAvatarColor(request.getAvatarColor());
        }
        userRepository.save(user);
        log.info("Updated profile for user: {}", userId);
        return UserDto.from(user, computeStats(user));
    }

    // T029
    @Transactional
    public String uploadAvatar(UUID userId, MultipartFile file) {
        validateAvatarFile(file);

        User user = findById(userId);
        String ext = contentTypeToExt(file.getContentType());
        String objectKey = "avatars/" + userId + "." + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectKey)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("Avatar upload failed for user {}: {}", userId, e.getMessage());
            throw new BusinessException("AVATAR_UPLOAD_FAILED", "Failed to upload avatar");
        }

        String avatarUrl = minioEndpoint + "/" + minioBucket + "/" + objectKey;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        log.info("Uploaded avatar for user: {}", userId);
        return avatarUrl;
    }

    // admin: list all users with optional role + search filter
    public Page<UserDto> listUsers(int page, int limit, Role role, String search) {
        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        return userRepository.findFiltered(role, search, pageable).map(UserDto::from);
    }

    // admin: update role / ban-unban
    @Transactional
    public UserDto adminUpdateUser(UUID userId, AdminUpdateUserRequest request) {
        User user = findById(userId);
        if (request.getIsActive() != null) {
            user.setActive(request.getIsActive());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        userRepository.save(user);
        log.info("Admin updated user {}: isActive={}, role={}", userId, request.getIsActive(), request.getRole());
        return UserDto.from(user);
    }

    private User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Avatar must be smaller than 5 MB");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new BusinessException("FILE_TYPE_INVALID", "Avatar must be JPEG, PNG, or WebP");
        }
    }

    private String contentTypeToExt(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private UserDto.Stats computeStats(User user) {
        // Populated in M04 (classrooms), M09 (sessions), M10 (questions)
        return UserDto.Stats.builder()
                .classroomsCount(0)
                .sessionsCount(0)
                .questionsAsked(0)
                .studentsReached(0)
                .build();
    }
}
