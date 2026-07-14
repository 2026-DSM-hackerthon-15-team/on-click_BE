package com.onclick;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InstagramAccountApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerStoresReadsAndUpdatesPlaintextCredentials() throws Exception {
        Session session = signUpAndLogin("instagram-owner");

        mockMvc.perform(put("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": " cafe.owner ",
                                  "password": " password with spaces "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("cafe.owner"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accountId").value("cafe.owner"))
                .andExpect(jsonPath("$.password").value(" password with spaces "));

        assertThat(jdbc.queryForObject(
                "SELECT password_plaintext FROM instagram_accounts WHERE store_id = ?",
                String.class,
                session.storeId()
        )).isEqualTo(" password with spaces ");

        mockMvc.perform(put("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "updated.owner",
                                  "password": "updated-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("updated.owner"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM instagram_accounts WHERE store_id = ?",
                Integer.class,
                session.storeId()
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT password_plaintext FROM instagram_accounts WHERE store_id = ?",
                String.class,
                session.storeId()
        )).isEqualTo("updated-password");
    }

    @Test
    void accountApiRequiresAuthenticationAndStoreOwnership() throws Exception {
        Session owner = signUpAndLogin("instagram-store-owner");
        Session other = signUpAndLogin("instagram-other-owner");

        mockMvc.perform(put("/stores/{storeId}/instagram-account", owner.storeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCredentials()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mockMvc.perform(put("/stores/{storeId}/instagram-account", owner.storeId())
                        .header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCredentials()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STORE_ACCESS_DENIED"));

        mockMvc.perform(get("/stores/{storeId}/instagram-account", owner.storeId())
                        .header(HttpHeaders.AUTHORIZATION, other.authorization()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void rejectsInvalidCredentialsAndReportsMissingAccount() throws Exception {
        Session session = signUpAndLogin("instagram-validation");

        mockMvc.perform(get("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSTAGRAM_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(put("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":" ","password":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void databaseEnforcesOneAccountPerStoreAndCascadesStoreDeletion() throws Exception {
        Session session = signUpAndLogin("instagram-schema");
        mockMvc.perform(put("/stores/{storeId}/instagram-account", session.storeId())
                        .header(HttpHeaders.AUTHORIZATION, session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCredentials()))
                .andExpect(status().isOk());

        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO instagram_accounts
                    (store_id, account_id, password_plaintext, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                session.storeId(),
                "duplicate",
                "duplicate-password",
                now,
                now
        )).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM stores WHERE id = ?", session.storeId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM instagram_accounts WHERE store_id = ?",
                Integer.class,
                session.storeId()
        )).isZero();
    }

    private Session signUpAndLogin(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String accountId = prefix + "-" + suffix;
        MvcResult signup = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"%s",
                                  "password":"password123!",
                                  "name":"Instagram 사용자",
                                  "email":"%s@example.com",
                                  "storeName":"Instagram 매장"
                                }
                                """.formatted(accountId, accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();

        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","password":"password123!"}
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andReturn();
        String authorization = "Bearer " + body(login).required("accessToken").asText();
        return new Session(storeId, authorization);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String validCredentials() {
        return """
                {"accountId":"owner.id","password":"plain-password"}
                """;
    }

    private record Session(long storeId, String authorization) {
    }
}
