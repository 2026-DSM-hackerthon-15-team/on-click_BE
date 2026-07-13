package com.onclick.domain.media.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.onclick.domain.media.dto.MediaUploadResponse;
import com.onclick.domain.media.entity.MediaFile;
import com.onclick.domain.media.repository.MediaFileRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.config.properties.MediaProperties;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    private final MediaFileRepository mediaFileRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final MediaProperties properties;
    private final Clock clock;
    private final Path storageRoot;

    public MediaStorageService(
            MediaFileRepository mediaFileRepository,
            StoreAccessValidator storeAccessValidator,
            MediaProperties properties,
            Clock clock
    ) {
        this.mediaFileRepository = mediaFileRepository;
        this.storeAccessValidator = storeAccessValidator;
        this.properties = properties;
        this.clock = clock;
        this.storageRoot = Path.of(properties.storageDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize media storage", exception);
        }
    }

    @Transactional
    public MediaUploadResponse upload(Jwt jwt, Long storeId, MultipartFile file) {
        storeAccessValidator.validate(jwt, storeId);
        validate(file);

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = "image/png".equals(file.getContentType()) ? ".png" : ".jpg";
        String storageName = UUID.randomUUID() + extension;
        Path destination = resolveStoragePath(storageName);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.MEDIA_STORAGE_ERROR, "이미지를 저장할 수 없습니다.", exception);
        }
        deleteOnRollback(destination);

        MediaFile mediaFile;
        try {
            mediaFile = mediaFileRepository.save(new MediaFile(
                    storeId,
                    originalName,
                    storageName,
                    file.getContentType(),
                    file.getSize()
            ));
        } catch (RuntimeException exception) {
            deletePhysicalFile(destination);
            throw exception;
        }
        return MediaUploadResponse.from(mediaFile, publicUrl(mediaFile));
    }

    @Transactional(readOnly = true)
    public StoredMedia loadPublic(String publicId) {
        MediaFile mediaFile = mediaFileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        Path path = resolveStoragePath(mediaFile.getStorageName());
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(ErrorCode.MEDIA_NOT_FOUND);
            }
            return new StoredMedia(resource, mediaFile.getContentType(), mediaFile.getOriginalName());
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.MEDIA_NOT_FOUND, "이미지 파일을 읽을 수 없습니다.", exception);
        }
    }

    @Transactional
    public void delete(Jwt jwt, Long storeId, Long mediaId) {
        storeAccessValidator.validate(jwt, storeId);
        MediaFile mediaFile = findOwned(storeId, mediaId);
        if (mediaFileRepository.countMarketingReferences(mediaId) > 0) {
            throw new ApiException(ErrorCode.MEDIA_IN_USE);
        }
        mediaFileRepository.delete(mediaFile);
        deleteAfterCommit(resolveStoragePath(mediaFile.getStorageName()));
    }

    @Transactional(readOnly = true)
    public List<MediaFile> requireOwned(Long storeId, List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        if (mediaIds.size() > 10 || new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지는 중복 없이 최대 10개까지 선택할 수 있습니다.");
        }
        List<MediaFile> mediaFiles = mediaFileRepository.findAllByIdInAndStoreId(mediaIds, storeId);
        if (mediaFiles.size() != mediaIds.size()) {
            throw new ApiException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return mediaIds.stream()
                .map(id -> mediaFiles.stream().filter(media -> media.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    public String publicUrl(MediaFile mediaFile) {
        String baseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/public/media/" + mediaFile.getPublicId();
    }

    @Scheduled(fixedDelayString = "${app.media.cleanup-interval:PT1H}")
    @Transactional
    public void deleteExpiredOrphans() {
        Instant cutoff = clock.instant().minus(properties.orphanRetention());
        for (MediaFile mediaFile : mediaFileRepository.findOrphansCreatedBefore(cutoff)) {
            mediaFileRepository.delete(mediaFile);
            deleteAfterCommit(resolveStoragePath(mediaFile.getStorageName()));
        }
        deleteUntrackedPhysicalFiles(cutoff);
    }

    private MediaFile findOwned(Long storeId, Long mediaId) {
        return mediaFileRepository.findByIdAndStoreId(mediaId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "업로드할 이미지가 비어 있습니다.");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지 크기가 허용 한도를 초과했습니다.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "JPEG 또는 PNG 이미지만 업로드할 수 있습니다.");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(8);
            if (!hasValidSignature(file.getContentType(), signature)) {
                throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지 파일 내용이 확장자와 일치하지 않습니다.");
            }
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지 파일을 읽을 수 없습니다.", exception);
        }
        validateDecodableImage(file);
    }

    private void validateDecodableImage(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지 파일을 해석할 수 없습니다.");
            }
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "손상된 이미지 파일입니다.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                boolean expectedFormat = "image/png".equals(file.getContentType())
                        ? "png".equals(format)
                        : "jpeg".equals(format) || "jpg".equals(format);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!expectedFormat
                        || width <= 0
                        || height <= 0
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "이미지 형식 또는 해상도가 허용 범위를 벗어났습니다.");
                }
                var readParam = reader.getDefaultReadParam();
                readParam.setSourceSubsampling(Math.max(1, width), Math.max(1, height), 0, 0);
                reader.read(0, readParam);
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE, "손상된 이미지 파일입니다.", exception);
        }
    }

    private boolean hasValidSignature(String contentType, byte[] signature) {
        if ("image/jpeg".equals(contentType)) {
            return signature.length >= 3
                    && (signature[0] & 0xff) == 0xff
                    && (signature[1] & 0xff) == 0xd8
                    && (signature[2] & 0xff) == 0xff;
        }
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        return java.util.Arrays.equals(signature, png);
    }

    private String sanitizeOriginalName(String originalName) {
        String name = originalName == null ? "image" : Path.of(originalName).getFileName().toString().trim();
        return name.isEmpty() ? "image" : name.substring(0, Math.min(name.length(), 255));
    }

    private Path resolveStoragePath(String storageName) {
        Path path = storageRoot.resolve(storageName).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new ApiException(ErrorCode.INVALID_MEDIA_FILE);
        }
        return path;
    }

    private void deletePhysicalFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Metadata remains authoritative; a later operational cleanup can remove leftovers.
        }
    }

    private void deleteOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deletePhysicalFile(path);
                }
            }
        });
    }

    private void deleteAfterCommit(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePhysicalFile(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePhysicalFile(path);
            }
        });
    }

    private void deleteUntrackedPhysicalFiles(Instant cutoff) {
        Set<String> tracked = new HashSet<>(mediaFileRepository.findAllStorageNames());
        try (var paths = Files.list(storageRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !tracked.contains(path.getFileName().toString()))
                    .filter(path -> isOlderThan(path, cutoff))
                    .forEach(this::deleteAfterCommit);
        } catch (IOException ignored) {
            // A later cleanup cycle retries filesystem enumeration.
        }
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException ignored) {
            return false;
        }
    }

    public record StoredMedia(Resource resource, String contentType, String originalName) {
    }
}
