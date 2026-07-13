package com.onclick.domain.instagram.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.onclick.global.config.properties.InstagramProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "app.instagram", name = "provider", havingValue = "http")
public class HttpInstagramProvider implements InstagramProvider {

    private final InstagramProperties properties;
    private final Clock clock;
    private final RestClient restClient;

    public HttpInstagramProvider(InstagramProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.oauthCallbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.scopes())
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    @Override
    public ConnectedAccount exchangeCode(String code) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            form.add("grant_type", "authorization_code");
            form.add("redirect_uri", properties.oauthCallbackUrl());
            form.add("code", code);
            JsonNode shortToken = restClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            requireNode(shortToken, "access_token");

            String longTokenUrl = UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                    .path("/access_token")
                    .queryParam("grant_type", "ig_exchange_token")
                    .queryParam("client_secret", properties.clientSecret())
                    .queryParam("access_token", shortToken.get("access_token").asText())
                    .build(true)
                    .toUriString();
            JsonNode longToken = restClient.get().uri(longTokenUrl).retrieve().body(JsonNode.class);
            requireNode(longToken, "access_token");

            String accessToken = longToken.get("access_token").asText();
            long expiresIn = longToken.path("expires_in").asLong(5_184_000L);
            JsonNode profile = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                            .path("/me")
                            .queryParam("fields", "user_id,username")
                            .queryParam("access_token", accessToken)
                            .build(true)
                            .toUriString())
                    .retrieve()
                    .body(JsonNode.class);
            String userId = profile != null && profile.hasNonNull("user_id")
                    ? profile.get("user_id").asText()
                    : requireNode(profile, "id").asText();
            String username = requireNode(profile, "username").asText();
            return new ConnectedAccount(userId, username, accessToken, clock.instant().plusSeconds(expiresIn));
        } catch (RestClientResponseException exception) {
            throw providerFailure("Instagram OAuth token exchange failed", exception);
        } catch (ResourceAccessException exception) {
            throw new InstagramProviderException("Instagram OAuth provider is unavailable", true, exception);
        }
    }

    @Override
    public RefreshedToken refresh(String accessToken) {
        try {
            JsonNode response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                            .path("/refresh_access_token")
                            .queryParam("grant_type", "ig_refresh_token")
                            .queryParam("access_token", accessToken)
                            .build(true)
                            .toUriString())
                    .retrieve()
                    .body(JsonNode.class);
            String refreshed = requireNode(response, "access_token").asText();
            long expiresIn = response.path("expires_in").asLong(5_184_000L);
            return new RefreshedToken(refreshed, clock.instant().plusSeconds(expiresIn));
        } catch (RestClientResponseException exception) {
            throw providerFailure("Instagram token refresh failed", exception);
        } catch (ResourceAccessException exception) {
            throw new InstagramProviderException("Instagram provider is unavailable", true, exception);
        }
    }

    @Override
    public PublishedPost publish(PublishRequest request) {
        boolean publishRequested = false;
        try {
            List<String> imageUrls = request.imageUrls();
            if (imageUrls == null || imageUrls.isEmpty()) {
                throw new InstagramProviderException("At least one image is required", false);
            }
            if (imageUrls.stream().anyMatch(url -> url == null || !url.startsWith("https://"))) {
                throw new InstagramProviderException("Instagram images must use public HTTPS URLs", false);
            }
            String containerId = imageUrls.size() == 1
                    ? createImageContainer(request, imageUrls.getFirst(), false)
                    : createCarouselContainer(request, imageUrls);

            MultiValueMap<String, String> publishForm = form(
                    "creation_id", containerId,
                    "access_token", request.accessToken()
            );
            publishRequested = true;
            JsonNode published = postForm(
                    properties.graphBaseUrl() + "/" + request.instagramUserId() + "/media_publish",
                    publishForm
            );
            String postId = requireNode(published, "id").asText();
            JsonNode post = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                            .path("/" + postId)
                            .queryParam("fields", "permalink")
                            .queryParam("access_token", request.accessToken())
                            .build(true)
                            .toUriString())
                    .retrieve()
                    .body(JsonNode.class);
            String permalink = post == null || !post.hasNonNull("permalink") ? null : post.get("permalink").asText();
            return new PublishedPost(postId, permalink, clock.instant());
        } catch (RestClientResponseException exception) {
            InstagramProviderException failure = providerFailure("Instagram content publishing failed", exception);
            if (publishRequested) {
                throw new InstagramProviderException(
                        failure.getMessage(),
                        false,
                        true,
                        exception
                );
            }
            throw failure;
        } catch (ResourceAccessException exception) {
            throw new InstagramProviderException(
                    "Instagram provider is unavailable",
                    !publishRequested,
                    publishRequested,
                    exception
            );
        }
    }

    @Override
    public void disconnect(String accessToken) {
        try {
            restClient.delete()
                    .uri(UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                            .path("/me/permissions")
                            .queryParam("access_token", accessToken)
                            .build(true)
                            .toUriString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw providerFailure("Instagram token revocation failed", exception);
        } catch (ResourceAccessException exception) {
            throw new InstagramProviderException("Instagram provider is unavailable", true, exception);
        }
    }

    private String createImageContainer(PublishRequest request, String imageUrl, boolean carouselItem) {
        MultiValueMap<String, String> form = form(
                "image_url", imageUrl,
                "access_token", request.accessToken()
        );
        if (carouselItem) {
            form.add("is_carousel_item", "true");
        } else {
            form.add("caption", request.caption());
        }
        JsonNode response = postForm(properties.graphBaseUrl() + "/" + request.instagramUserId() + "/media", form);
        return requireNode(response, "id").asText();
    }

    private String createCarouselContainer(PublishRequest request, List<String> imageUrls) {
        List<String> childIds = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            childIds.add(createImageContainer(request, imageUrl, true));
        }
        MultiValueMap<String, String> form = form(
                "media_type", "CAROUSEL",
                "children", String.join(",", childIds),
                "caption", request.caption(),
                "access_token", request.accessToken()
        );
        JsonNode response = postForm(properties.graphBaseUrl() + "/" + request.instagramUserId() + "/media", form);
        return requireNode(response, "id").asText();
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    private MultiValueMap<String, String> form(String... values) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int index = 0; index < values.length; index += 2) {
            form.add(values[index], values[index + 1]);
        }
        return form;
    }

    private JsonNode requireNode(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            throw new InstagramProviderException("Instagram response is missing " + field, false);
        }
        return node.get(field);
    }

    private InstagramProviderException providerFailure(String message, RestClientResponseException exception) {
        boolean retryable = exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429;
        return new InstagramProviderException(message, retryable, exception);
    }
}
