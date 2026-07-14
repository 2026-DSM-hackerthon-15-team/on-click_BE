package com.onclick;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onclick.domain.consulting.entity.ConsultingStatus;
import com.onclick.domain.consulting.repository.ConsultingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConsultingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConsultingRepository consultingRepository;

    @Test
    void enqueuesGeneratesAndIdempotentlyReturnsDailyConsulting() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcResult signup = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"consulting-%s",
                                  "password":"password123!",
                                  "name":"컨설팅 사용자",
                                  "email":"consulting-%s@example.com",
                                  "storeName":"컨설팅 매장",
                                  "closingTime":"00:00"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();
        String token = login("consulting-" + suffix, "password123!");
        LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String requestBody = """
                {"targetDate":"%s"}
                """.formatted(targetDate);

        MvcResult first = mockMvc.perform(post("/stores/{storeId}/consultings", storeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long consultingId = body(first).required("consultingId").asLong();
        assertThat(first.getResponse().getHeader("Location"))
                .isEqualTo("/stores/%d/consultings/%d".formatted(storeId, consultingId));

        mockMvc.perform(post("/stores/{storeId}/consultings", storeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Location",
                        "/stores/%d/consultings/%d".formatted(storeId, consultingId)
                ))
                .andExpect(jsonPath("$.consultingId").value(consultingId));

        assertThat(waitForTerminalStatus(consultingId)).isEqualTo(ConsultingStatus.COMPLETED);
        mockMvc.perform(get("/stores/{storeId}/consultings/{consultingId}", storeId, consultingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    private ConsultingStatus waitForTerminalStatus(long consultingId) throws InterruptedException {
        ConsultingStatus current = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            current = consultingRepository.findById(consultingId).orElseThrow().getStatus();
            if (current == ConsultingStatus.COMPLETED || current == ConsultingStatus.FAILED) {
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
