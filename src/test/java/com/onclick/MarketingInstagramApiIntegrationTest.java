package com.onclick;

import java.util.Base64;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MarketingInstagramApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void generatesAndApprovesMarketingContentWithoutOAuthPublishing() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcResult signup = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"marketing-%s",
                                  "password":"password123!",
                                  "name":"마케팅 사용자",
                                  "email":"marketing-%s@example.com",
                                  "storeName":"마케팅 매장"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();
        String authorization = "Bearer " + login("marketing-" + suffix, "password123!");

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
                        .header("Authorization", authorization))
                .andExpect(status().isCreated())
                .andReturn();
        long mediaId = body(upload).required("mediaId").asLong();

        MvcResult generated = mockMvc.perform(post("/stores/{storeId}/marketings", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName":"딸기 라떼",
                                  "description":"국내산 딸기와 수제 크림",
                                  "mediaIds":[%d]
                                }
                                """.formatted(mediaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long marketingId = body(generated).required("marketingId").asLong();

        mockMvc.perform(post("/stores/{storeId}/marketings/{marketingId}/approve", storeId, marketingId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/stores/{storeId}/marketings/{marketingId}", storeId, marketingId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.externalPostId").isEmpty())
                .andExpect(jsonPath("$.publishedUrl").isEmpty());
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
}
