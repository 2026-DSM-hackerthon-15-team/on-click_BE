package com.onclick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onclick.domain.marketing.entity.MarketingStatus;
import com.onclick.domain.marketing.repository.MarketingContentRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MarketingInstagramApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MarketingContentRepository marketingRepository;

    @Test
    void uploadsGeneratesConnectsApprovesAndPublishesMarketingContent() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcResult signup = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"marketing-%s",
                                  "password":"password123!",
                                  "name":"마케팅 사용자",
                                  "email":"marketing-%s@example.com",
                                  "storeName":"마케팅 매장",
                                  "timeZone":"Asia/Seoul",
                                  "closingTime":"22:00"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();
        String token = login("marketing-" + suffix, "password123!");

        MockMultipartFile image = new MockMultipartFile(
                "file",
                "menu.png",
                "image/png",
                Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
                )
        );
        MvcResult upload = mockMvc.perform(multipart("/stores/{storeId}/media", storeId)
                        .file(image)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicUrl").value(org.hamcrest.Matchers.containsString("/public/media/")))
                .andReturn();
        long mediaId = body(upload).required("mediaId").asLong();

        MvcResult generated = mockMvc.perform(post("/stores/{storeId}/marketings", storeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName":"딸기 라떼",
                                  "description":"국내산 딸기와 수제 크림",
                                  "tone":"FRIENDLY",
                                  "mediaIds":[%d]
                                }
                                """.formatted(mediaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long marketingId = body(generated).required("marketingId").asLong();

        MvcResult connect = mockMvc.perform(post("/stores/{storeId}/integrations/instagram/connect", storeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String authorizationUrl = body(connect).required("authorizationUrl").asText();
        var params = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams();
        mockMvc.perform(get("/integrations/instagram/callback")
                        .param("code", params.getFirst("code"))
                        .param("state", params.getFirst("state")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "http://localhost:5173/settings/integrations/instagram?instagram=connected&storeId=" + storeId
                ));

        mockMvc.perform(post("/stores/{storeId}/marketings/{marketingId}/approve", storeId, marketingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        MarketingStatus status = waitForPublished(marketingId);
        assertThat(status).isEqualTo(MarketingStatus.PUBLISHED);
        mockMvc.perform(get("/stores/{storeId}/marketings/{marketingId}", storeId, marketingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.externalPostId").isNotEmpty());
    }

    @Test
    void consumesOAuthStateOnlyOnceUnderConcurrentCallbacks() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcResult signup = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"oauth-%s",
                                  "password":"password123!",
                                  "name":"OAuth 사용자",
                                  "email":"oauth-%s@example.com",
                                  "storeName":"OAuth 매장"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();
        String token = login("oauth-" + suffix, "password123!");
        MvcResult connect = mockMvc.perform(post("/stores/{storeId}/integrations/instagram/connect", storeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        var params = UriComponentsBuilder
                .fromUriString(body(connect).required("authorizationUrl").asText())
                .build()
                .getQueryParams();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var callback = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(get("/integrations/instagram/callback")
                                .param("code", params.getFirst("code"))
                                .param("state", params.getFirst("state")))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = executor.submit(callback);
            var second = executor.submit(callback);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(302, 400);
        }
    }

    private MarketingStatus waitForPublished(long marketingId) throws InterruptedException {
        MarketingStatus current = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            current = marketingRepository.findById(marketingId).orElseThrow().getStatus();
            if (current == MarketingStatus.PUBLISHED || current == MarketingStatus.FAILED) {
                return current;
            }
            Thread.sleep(50);
        }
        return current;
    }

    private String login(String accountId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","password":"%s"}
                                """.formatted(accountId, password)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).required("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
