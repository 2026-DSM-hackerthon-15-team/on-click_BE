package com.onclick.domain.instagram.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.onclick.domain.instagram.dto.InstagramConnectResponse;
import com.onclick.domain.instagram.dto.InstagramStatusResponse;
import com.onclick.domain.instagram.entity.InstagramIntegration;
import com.onclick.domain.instagram.entity.InstagramOAuthState;
import com.onclick.domain.instagram.repository.InstagramIntegrationRepository;
import com.onclick.domain.instagram.repository.InstagramOAuthStateRepository;
import com.onclick.domain.instagram.service.InstagramOAuthStateConsumer.ConsumedState;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.config.properties.InstagramProperties;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstagramIntegrationService {

    private final InstagramIntegrationRepository integrationRepository;
    private final InstagramOAuthStateRepository stateRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final JwtUserIdResolver userIdResolver;
    private final InstagramOAuthStateConsumer stateConsumer;
    private final InstagramProvider provider;
    private final InstagramTokenCipher tokenCipher;
    private final InstagramProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public InstagramIntegrationService(
            InstagramIntegrationRepository integrationRepository,
            InstagramOAuthStateRepository stateRepository,
            StoreAccessValidator storeAccessValidator,
            JwtUserIdResolver userIdResolver,
            InstagramOAuthStateConsumer stateConsumer,
            InstagramProvider provider,
            InstagramTokenCipher tokenCipher,
            InstagramProperties properties,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.stateRepository = stateRepository;
        this.storeAccessValidator = storeAccessValidator;
        this.userIdResolver = userIdResolver;
        this.stateConsumer = stateConsumer;
        this.provider = provider;
        this.tokenCipher = tokenCipher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public InstagramConnectResponse connect(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        if (integrationRepository.findByStoreId(storeId).isPresent()) {
            throw new ApiException(ErrorCode.INSTAGRAM_ALREADY_CONNECTED);
        }
        String state = newState();
        Instant expiresAt = clock.instant().plus(properties.stateTtl());
        stateRepository.save(new InstagramOAuthState(
                hash(state),
                storeId,
                userIdResolver.resolve(jwt),
                expiresAt
        ));
        return new InstagramConnectResponse(provider.authorizationUrl(state), expiresAt);
    }

    public Long completeCallback(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INSTAGRAM_OAUTH_STATE);
        }
        Instant now = clock.instant();
        ConsumedState oauthState = stateConsumer.consume(hash(state));
        storeAccessValidator.validate(oauthState.userId(), oauthState.storeId());

        InstagramProvider.ConnectedAccount account;
        try {
            account = provider.exchangeCode(code);
        } catch (InstagramProviderException exception) {
            throw new ApiException(ErrorCode.INSTAGRAM_PROVIDER_ERROR, exception.getMessage(), exception);
        }
        String encryptedToken = tokenCipher.encrypt(account.accessToken());
        InstagramIntegration integration = integrationRepository.findByStoreId(oauthState.storeId())
                .orElseGet(() -> new InstagramIntegration(
                        oauthState.storeId(),
                        account.instagramUserId(),
                        account.username(),
                        encryptedToken,
                        account.expiresAt(),
                        now
                ));
        if (integration.getId() != null) {
            integration.updateCredentials(
                    account.instagramUserId(),
                    account.username(),
                    encryptedToken,
                    account.expiresAt(),
                    now
            );
        }
        integrationRepository.save(integration);
        return oauthState.storeId();
    }

    @Transactional(readOnly = true)
    public InstagramStatusResponse status(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        return integrationRepository.findByStoreId(storeId)
                .map(InstagramStatusResponse::from)
                .orElseGet(InstagramStatusResponse::disconnected);
    }

    @Transactional
    public void disconnect(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        InstagramIntegration integration = integrationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTAGRAM_INTEGRATION_NOT_FOUND));
        try {
            provider.disconnect(tokenCipher.decrypt(integration.getAccessTokenEncrypted()));
        } catch (InstagramProviderException ignored) {
            // Local disconnect remains authoritative even if provider revocation is unavailable.
        }
        integrationRepository.delete(integration);
    }

    @Transactional(readOnly = true)
    public PublishingCredentials requirePublishingCredentials(Long storeId) {
        InstagramIntegration integration = integrationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTAGRAM_INTEGRATION_NOT_FOUND));
        return new PublishingCredentials(
                integration.getInstagramUserId(),
                tokenCipher.decrypt(integration.getAccessTokenEncrypted())
        );
    }

    @Scheduled(cron = "${app.instagram.refresh-cron:0 0 3 * * *}")
    @Transactional
    public void refreshExpiringTokens() {
        Instant cutoff = clock.instant().plus(Duration.ofDays(7));
        for (InstagramIntegration integration : integrationRepository.findAllByTokenExpiresAtBefore(cutoff)) {
            try {
                InstagramProvider.RefreshedToken refreshed = provider.refresh(
                        tokenCipher.decrypt(integration.getAccessTokenEncrypted())
                );
                integration.refreshToken(tokenCipher.encrypt(refreshed.accessToken()), refreshed.expiresAt());
            } catch (InstagramProviderException ignored) {
                // Keep the current token and retry on the next scheduled run.
            }
        }
    }

    @Scheduled(cron = "${app.instagram.state-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanupExpiredStates() {
        stateRepository.deleteByExpiresAtBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    public String frontendRedirectUrl(boolean connected, Long storeId) {
        String separator = properties.frontendRedirectUrl().contains("?") ? "&" : "?";
        return properties.frontendRedirectUrl()
                + separator + "instagram=" + (connected ? "connected" : "error")
                + (storeId == null ? "" : "&storeId=" + storeId);
    }

    private String newState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record PublishingCredentials(String instagramUserId, String accessToken) {
    }
}
