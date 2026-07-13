package com.onclick.domain.instagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import com.onclick.domain.instagram.entity.InstagramIntegration;
import com.onclick.domain.instagram.entity.InstagramOAuthState;
import com.onclick.domain.instagram.repository.InstagramIntegrationRepository;
import com.onclick.domain.instagram.repository.InstagramOAuthStateRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.config.properties.InstagramProperties;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class InstagramIntegrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Mock InstagramIntegrationRepository integrationRepository;
    @Mock InstagramOAuthStateRepository stateRepository;
    @Mock StoreAccessValidator storeAccessValidator;
    @Mock JwtUserIdResolver userIdResolver;
    @Mock InstagramProvider provider;
    @Mock Jwt jwt;

    InstagramProperties properties;
    InstagramTokenCipher cipher;
    InstagramIntegrationService service;

    @BeforeEach
    void setUp() {
        properties = new InstagramProperties(
                "mock",
                "client",
                "secret",
                "https://api.example.com/integrations/instagram/callback",
                "https://front.example.com/settings/instagram",
                "https://instagram.example.com/oauth",
                "https://instagram.example.com/token",
                "https://instagram.example.com/graph",
                "basic,publish",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                Duration.ofMinutes(10),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        cipher = new InstagramTokenCipher(properties);
        InstagramOAuthStateConsumer stateConsumer = new InstagramOAuthStateConsumer(
                stateRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service = new InstagramIntegrationService(
                integrationRepository,
                stateRepository,
                storeAccessValidator,
                userIdResolver,
                stateConsumer,
                provider,
                cipher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsOneTimeHashedOAuthState() {
        given(userIdResolver.resolve(jwt)).willReturn(9L);
        given(integrationRepository.findByStoreId(3L)).willReturn(Optional.empty());
        given(provider.authorizationUrl(any())).willAnswer(invocation -> "https://oauth.example/?state=" + invocation.getArgument(0));

        var response = service.connect(jwt, 3L);

        ArgumentCaptor<InstagramOAuthState> captor = ArgumentCaptor.forClass(InstagramOAuthState.class);
        verify(stateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateHash()).hasSize(64);
        assertThat(response.authorizationUrl()).startsWith("https://oauth.example/?state=");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void callbackStoresEncryptedTokenAndRejectsStateReplay() throws Exception {
        String rawState = "state-value";
        InstagramOAuthState state = new InstagramOAuthState(hash(rawState), 3L, 9L, NOW.plusSeconds(60));
        given(stateRepository.findByStateHash(hash(rawState))).willReturn(Optional.of(state));
        given(provider.exchangeCode("code")).willReturn(new InstagramProvider.ConnectedAccount(
                "ig-user",
                "owner",
                "plain-access-token",
                NOW.plus(Duration.ofDays(60))
        ));
        given(integrationRepository.findByStoreId(3L)).willReturn(Optional.empty());

        assertThat(service.completeCallback("code", rawState)).isEqualTo(3L);

        ArgumentCaptor<InstagramIntegration> captor = ArgumentCaptor.forClass(InstagramIntegration.class);
        verify(integrationRepository).save(captor.capture());
        assertThat(captor.getValue().getAccessTokenEncrypted()).doesNotContain("plain-access-token");
        assertThat(cipher.decrypt(captor.getValue().getAccessTokenEncrypted())).isEqualTo("plain-access-token");

        assertThatThrownBy(() -> service.completeCallback("code", rawState))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INSTAGRAM_OAUTH_STATE);
    }

    private String hash(String state) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8))
        );
    }
}
