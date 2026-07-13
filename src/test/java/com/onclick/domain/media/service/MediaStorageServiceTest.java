package com.onclick.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import com.onclick.domain.media.entity.MediaFile;
import com.onclick.domain.media.repository.MediaFileRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.config.properties.MediaProperties;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    MediaFileRepository repository;

    @Mock
    StoreAccessValidator storeAccessValidator;

    @Mock
    Jwt jwt;

    MediaStorageService service;

    @BeforeEach
    void setUp() {
        service = new MediaStorageService(
                repository,
                storeAccessValidator,
                new MediaProperties(tempDir.toString(), "https://api.example.com/", 1024L, Duration.ofHours(24)),
                Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void storesOwnedImageUsingGeneratedStorageName() {
        given(repository.save(any(MediaFile.class))).willAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 7L);
            ReflectionTestUtils.setField(media, "createdAt", Instant.parse("2026-07-14T00:00:00Z"));
            return media;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../menu.png",
                "image/png",
                validPng()
        );

        var response = service.upload(jwt, 3L, file);

        assertThat(response.mediaId()).isEqualTo(7L);
        assertThat(response.originalName()).isEqualTo("menu.png");
        assertThat(response.publicUrl()).startsWith("https://api.example.com/public/media/");
        assertThat(tempDir.toFile().listFiles()).hasSize(1);
    }

    @Test
    void rejectsUnsupportedOrOversizedFiles() {
        MockMultipartFile unsupported = new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{1});
        assertThatThrownBy(() -> service.upload(jwt, 3L, unsupported))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile oversized = new MockMultipartFile("file", "x.png", "image/png", new byte[1025]);
        assertThatThrownBy(() -> service.upload(jwt, 3L, oversized))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile corrupt = new MockMultipartFile(
                "file",
                "x.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3}
        );
        assertThatThrownBy(() -> service.upload(jwt, 3L, corrupt))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    void removesUploadedFileWhenDatabaseTransactionRollsBack() {
        given(repository.save(any(MediaFile.class))).willAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 7L);
            ReflectionTestUtils.setField(media, "createdAt", Instant.parse("2026-07-14T00:00:00Z"));
            return media;
        });
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(jwt, 3L, new MockMultipartFile(
                    "file", "menu.png", "image/png", validPng()
            ));
            assertThat(tempDir.toFile().listFiles()).hasSize(1);

            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertThat(tempDir.toFile().listFiles()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void removesPhysicalFileOnlyAfterDatabaseCommit() throws Exception {
        MediaFile media = new MediaFile(3L, "menu.png", "stored.png", "image/png", validPng().length);
        ReflectionTestUtils.setField(media, "id", 7L);
        given(repository.findByIdAndStoreId(7L, 3L)).willReturn(Optional.of(media));
        given(repository.countMarketingReferences(7L)).willReturn(0L);
        Path stored = tempDir.resolve("stored.png");
        Files.write(stored, validPng());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(jwt, 3L, 7L);
            assertThat(stored).exists();

            TransactionSynchronizationUtils.triggerAfterCommit();
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            assertThat(stored).doesNotExist();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private byte[] validPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
    }
}
