package com.onclick;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountStoreApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void managesProfilePasswordAndStoreClosingTimes() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String accountId = "profile-" + suffix;
        String updatedAccountId = "updated-" + suffix;
        String email = accountId + "@example.com";
        String updatedEmail = updatedAccountId + "@example.com";
        String password = "password123!";

        MvcResult signUpResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "name": "프로필 사용자",
                                  "email": "%s",
                                  "storeName": "첫 매장",
                                  "timeZone": "Asia/Seoul",
                                  "closingTime": "21:15"
                                }
                                """.formatted(accountId, password, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("프로필 사용자"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.closingTime").value("21:15"))
                .andReturn();
        long userId = body(signUpResult).required("userId").asLong();

        String authorization = bearer(login(accountId, password));
        mockMvc.perform(get("/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.name").value("프로필 사용자"))
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(patch("/me")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "accountId": " %s ",
                                  "name": " 수정된 사용자 ",
                                  "email": "%s"
                                }
                                """.formatted(password, updatedAccountId, updatedEmail.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(updatedAccountId))
                .andExpect(jsonPath("$.name").value("수정된 사용자"))
                .andExpect(jsonPath("$.email").value(updatedEmail));

        mockMvc.perform(get("/stores").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closingTime").value("21:15"));

        MvcResult storeResult = mockMvc.perform(post("/stores")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "두 번째 매장",
                                  "timeZone": "Asia/Seoul"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closingTime").value("22:00"))
                .andReturn();
        long storeId = body(storeResult).required("id").asLong();

        mockMvc.perform(patch("/stores/{storeId}", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closingTime": "23:45"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingTime").value("23:45"));

        mockMvc.perform(patch("/stores/{storeId}", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closingTime": "24:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/me/password")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "new-password123!"
                                }
                                """.formatted(password)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s"
                                }
                                """.formatted(updatedAccountId, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        login(updatedAccountId, "new-password123!");
    }

    private String login(String accountId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s"
                                }
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
