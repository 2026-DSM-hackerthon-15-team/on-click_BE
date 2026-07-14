package com.onclick;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onclick.domain.store.repository.StoreRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Clock clock;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void applicationContextStartsAfterFlywayMigrationAndJpaSchemaValidation() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8') AND success = TRUE",
                Integer.class
        );

        assertThat(successfulMigrations).isEqualTo(8);

        Integer membershipTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_name) = 'user_store_memberships'",
                Integer.class
        );
        assertThat(membershipTableCount).isZero();

        Integer removedVisitorTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_name) = 'hourly_visitor_counts'",
                Integer.class
        );
        assertThat(removedVisitorTableCount).isZero();
    }

    @Test
    void signsUpOwnerLogsInAndCreatesProductWithIssuedBearerToken() throws Exception {
        String accountId = "api-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "password123!";

        MvcResult signUpResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "name": "통합 테스트 사용자",
                                  "email": "%s@example.com",
                                  "storeName": "통합 테스트 매장",
                                  "closingTime": "22:30"
                                }
                                """.formatted(accountId, password, accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.name").value("통합 테스트 사용자"))
                .andExpect(jsonPath("$.email").value(accountId + "@example.com"))
                .andExpect(jsonPath("$.storeName").value("통합 테스트 매장"))
                .andExpect(jsonPath("$.closingTime").value("22:30"))
                .andReturn();

        JsonNode signUpBody = readBody(signUpResult);
        long userId = signUpBody.required("userId").asLong();
        long storeId = signUpBody.required("storeId").asLong();

        assertThat(storeRepository.findByIdAndOwnerId(storeId, userId))
                .isPresent()
                .get()
                .extracting(store -> store.getOwner().getId())
                .isEqualTo(userId);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s"
                                }
                                """.formatted(accountId, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String accessToken = readBody(loginResult).required("accessToken").asText();
        Jwt decodedToken = jwtDecoder.decode(accessToken);

        assertThat(decodedToken.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(Duration.between(decodedToken.getIssuedAt(), decodedToken.getExpiresAt()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(decodedToken.hasClaim("storeId")).isFalse();

        mockMvc.perform(post("/stores/{storeId}/products", storeId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "아메리카노",
                                  "price": 4500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.storeId").value(storeId))
                .andExpect(jsonPath("$.name").value("아메리카노"))
                .andExpect(jsonPath("$.price").value(4500))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsProtectedProductApiWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/stores/{storeId}/products", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "인증 없는 상품",
                                  "price": 1000
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void posSaleDrivesOrdersAndVisitorsAndCancellationRemovesBoth() throws Exception {
        String accountId = "dashboard-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "password123!";

        MvcResult signUpResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "name": "대시보드 테스트 사용자",
                                  "email": "%s@example.com",
                                  "storeName": "대시보드 통합 테스트 매장"
                                }
                                """.formatted(accountId, password, accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = readBody(signUpResult).required("storeId").asLong();

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s"
                                }
                                """.formatted(accountId, password)))
                .andExpect(status().isOk())
                .andReturn();
        String authorization = "Bearer "
                + readBody(loginResult).required("accessToken").asText();

        MvcResult productResult = mockMvc.perform(post("/stores/{storeId}/products", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "카페라테",
                                  "price": 5000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = readBody(productResult).required("id").asLong();

        LocalDateTime storeNow = LocalDateTime.now(clock);
        LocalDate businessDate = storeNow.toLocalDate();
        int businessHour = storeNow.getHour();
        String soldAt = businessDate.atTime(businessHour, 0).toString();
        String clientTransactionId = "tx-" + UUID.randomUUID();

        MvcResult saleResult = mockMvc.perform(post("/stores/{storeId}/sales/transactions", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientTransactionId": "%s",
                                  "soldAt": "%s",
                                  "items": [
                                    {
                                      "lineNo": 1,
                                      "productId": %d,
                                      "quantity": 2,
                                      "paidAmount": 10000
                                    },
                                    {
                                      "lineNo": 2,
                                      "productId": %d,
                                      "quantity": 1,
                                      "paidAmount": 5000
                                    }
                                  ]
                                }
                                """.formatted(clientTransactionId, soldAt, productId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleId").isNumber())
                .andExpect(jsonPath("$.clientTransactionId").value(clientTransactionId))
                .andExpect(jsonPath("$.totalPaidAmount").value(15000))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        long saleId = readBody(saleResult).required("saleId").asLong();

        mockMvc.perform(get("/stores/{storeId}/dashboard/summary", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(businessDate.toString()))
                .andExpect(jsonPath("$.totalSalesAmount").value(15000))
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.totalVisitors").value(1));

        mockMvc.perform(get("/stores/{storeId}/dashboard/hourly-sales", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(15000))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.hourly.length()").value(24))
                .andExpect(jsonPath("$.hourly[%d].hour".formatted(businessHour)).value(businessHour))
                .andExpect(jsonPath("$.hourly[%d].salesAmount".formatted(businessHour)).value(15000))
                .andExpect(jsonPath("$.hourly[%d].quantity".formatted(businessHour)).value(3))
                .andExpect(jsonPath("$.hourly[%d].orderCount".formatted(businessHour)).value(1));

        mockMvc.perform(get("/stores/{storeId}/dashboard/hourly-visitors", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVisitors").value(1))
                .andExpect(jsonPath("$.hourly.length()").value(24))
                .andExpect(jsonPath("$.hourly[%d].hour".formatted(businessHour)).value(businessHour))
                .andExpect(jsonPath("$.hourly[%d].visitorCount".formatted(businessHour)).value(1));

        mockMvc.perform(get("/stores/{storeId}/dashboard/closing-sales-forecast", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(businessDate.toString()))
                .andExpect(jsonPath("$.observedSalesAmount").value(15000))
                .andExpect(jsonPath("$.forecastClosingSalesAmount").value(500000))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.mock").doesNotExist());

        mockMvc.perform(get("/stores/{storeId}/dashboard/tomorrow-visitors-forecast", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value(businessDate.plusDays(1).toString()))
                .andExpect(jsonPath("$.expectedVisitors").value(120))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.mock").doesNotExist());

        mockMvc.perform(post(
                        "/stores/{storeId}/sales/transactions/{saleId}/cancel",
                        storeId,
                        saleId
                )
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/stores/{storeId}/dashboard/summary", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.totalVisitors").value(0));
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
