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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendsMessageAndPollsUpdatedAssistantByTheSameCursor() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MvcResult signup = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId":"chat-%s",
                                  "password":"password123!",
                                  "name":"채팅 사용자",
                                  "email":"chat-%s@example.com",
                                  "storeName":"채팅 매장"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signup).required("storeId").asLong();
        String authorization = bearer(login("chat-" + suffix, "password123!"));

        MvcResult room = mockMvc.perform(post("/stores/{storeId}/chat-rooms", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"AI 문의\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long chatRoomId = body(room).required("id").asLong();

        MvcResult send = mockMvc.perform(post(
                                "/stores/{storeId}/chat-rooms/{chatRoomId}/messages",
                                storeId,
                                chatRoomId
                        )
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientMessageId":"client-message-1",
                                  "content":"오늘 매출을 분석해줘"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.userMessage.status").value("COMPLETED"))
                .andExpect(jsonPath("$.assistantMessage.status").value("PENDING"))
                .andExpect(jsonPath("$.assistantMessage.content").doesNotExist())
                .andReturn();
        long assistantMessageId = body(send).required("assistantMessage").required("id").asLong();

        JsonNode completed = waitForAssistant(
                authorization,
                storeId,
                chatRoomId,
                assistantMessageId
        );
        assertThat(completed.required("id").asLong()).isEqualTo(assistantMessageId);
        assertThat(completed.required("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.required("content").asText())
                .isEqualTo("AI 답변: 오늘 매출을 분석해줘");
    }

    private JsonNode waitForAssistant(
            String authorization,
            long storeId,
            long chatRoomId,
            long assistantMessageId
    ) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult polling = mockMvc.perform(get(
                                    "/stores/{storeId}/chat-rooms/{chatRoomId}/messages",
                                    storeId,
                                    chatRoomId
                            )
                            .param("afterId", Long.toString(assistantMessageId))
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode messages = body(polling);
            if (!messages.isEmpty()) {
                latest = messages.get(0);
                if (!"PENDING".equals(latest.required("status").asText())) {
                    return latest;
                }
            }
            Thread.sleep(50);
        }
        return latest;
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
