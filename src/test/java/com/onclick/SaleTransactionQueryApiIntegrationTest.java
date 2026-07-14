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
class SaleTransactionQueryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void paginatesAllPosRecordsWithStableWhitelistedSorting() throws Exception {
        Session session = createSession();
        long productId = createProduct(session);
        long firstSaleId = createSale(
                session,
                productId,
                "2026-07-14T09:00:00",
                true
        );
        long secondSaleId = createSale(
                session,
                productId,
                "2026-07-14T11:00:00",
                false
        );
        long thirdSaleId = createSale(
                session,
                productId,
                "2026-07-14T11:00:00",
                false
        );
        assertThat(firstSaleId).isLessThan(secondSaleId);
        assertThat(secondSaleId).isLessThan(thirdSaleId);

        mockMvc.perform(post(
                        "/stores/{storeId}/sales/transactions/{saleId}/cancel",
                        session.storeId(),
                        secondSaleId
                )
                        .header("Authorization", session.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].saleId").value(thirdSaleId))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].saleId").value(secondSaleId))
                .andExpect(jsonPath("$.content[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.sortBy").value("soldAt"))
                .andExpect(jsonPath("$.sortDirection").value("desc"));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].saleId").value(firstSaleId))
                .andExpect(jsonPath("$.content[0].items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("page", "2")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("page", "0")
                        .queryParam("size", "3")
                        .queryParam("sortBy", "saleId")
                        .queryParam("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].saleId").value(firstSaleId))
                .andExpect(jsonPath("$.content[1].saleId").value(secondSaleId))
                .andExpect(jsonPath("$.content[2].saleId").value(thirdSaleId))
                .andExpect(jsonPath("$.sortBy").value("saleId"))
                .andExpect(jsonPath("$.sortDirection").value("asc"));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("sortBy", "totalPaidAmount"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId())
                        .header("Authorization", session.authorization())
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/stores/{storeId}/sales/transactions", session.storeId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private Session createSession() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String accountId = "sale-page-" + suffix;
        String password = "password123!";

        MvcResult signUp = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "name": "POS 조회 테스트",
                                  "email": "%s@example.com",
                                  "storeName": "POS 조회 테스트 매장"
                                }
                                """.formatted(accountId, password, accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = body(signUp).required("storeId").asLong();

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s"
                                }
                                """.formatted(accountId, password)))
                .andExpect(status().isOk())
                .andReturn();
        String authorization = "Bearer " + body(login).required("accessToken").asText();
        return new Session(storeId, authorization);
    }

    private long createProduct(Session session) throws Exception {
        MvcResult result = mockMvc.perform(post("/stores/{storeId}/products", session.storeId())
                        .header("Authorization", session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "페이지 테스트 상품",
                                  "price": 4500
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).required("id").asLong();
    }

    private long createSale(
            Session session,
            long productId,
            String soldAt,
            boolean multipleItems
    ) throws Exception {
        String items = multipleItems
                ? """
                        [
                          {"lineNo": 1, "productId": %d, "quantity": 1, "paidAmount": 4500},
                          {"lineNo": 2, "productId": %d, "quantity": 1, "paidAmount": 4500}
                        ]
                        """.formatted(productId, productId)
                : """
                        [
                          {"lineNo": 1, "productId": %d, "quantity": 1, "paidAmount": 4500}
                        ]
                        """.formatted(productId);
        MvcResult result = mockMvc.perform(post(
                        "/stores/{storeId}/sales/transactions",
                        session.storeId()
                )
                        .header("Authorization", session.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientTransactionId": "%s",
                                  "soldAt": "%s",
                                  "items": %s
                                }
                                """.formatted(UUID.randomUUID(), soldAt, items)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).required("saleId").asLong();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Session(long storeId, String authorization) {
    }
}
