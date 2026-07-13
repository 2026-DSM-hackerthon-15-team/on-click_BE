package com.onclick;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;

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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
    private UserStoreMembershipRepository membershipRepository;

    @Test
    void applicationContextStartsAfterFlywayMigrationAndJpaSchemaValidation() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class
        );

        assertThat(successfulMigrations).isEqualTo(1);
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
                                  "storeName": "통합 테스트 매장",
                                  "timeZone": "Asia/Seoul"
                                }
                                """.formatted(accountId, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.storeName").value("통합 테스트 매장"))
                .andReturn();

        JsonNode signUpBody = readBody(signUpResult);
        long userId = signUpBody.required("userId").asLong();
        long storeId = signUpBody.required("storeId").asLong();

        assertThat(membershipRepository.findByUserIdAndStoreId(userId, storeId))
                .isPresent()
                .get()
                .extracting(membership -> membership.getRole())
                .isEqualTo(StoreRole.OWNER);

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
    void posSaleAndVisitorsDriveDashboardAndCancellationRemovesTheOrder() throws Exception {
        String accountId = "dashboard-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "password123!";

        MvcResult signUpResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "storeName": "대시보드 통합 테스트 매장",
                                  "timeZone": "Asia/Seoul"
                                }
                                """.formatted(accountId, password)))
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

        ZoneId storeZone = ZoneId.of("Asia/Seoul");
        ZonedDateTime storeNow = ZonedDateTime.now(clock.withZone(storeZone));
        LocalDate businessDate = storeNow.toLocalDate();
        int businessHour = storeNow.getHour();
        String soldAt = businessDate.atTime(businessHour, 0)
                .atZone(storeZone)
                .toInstant()
                .toString();
        String transactionId = "tx-" + UUID.randomUUID();

        mockMvc.perform(post("/stores/{storeId}/sales/transactions", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "%s",
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
                                """.formatted(transactionId, soldAt, productId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.totalPaidAmount").value(15000))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(put("/stores/{storeId}/visitors/hourly", storeId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessDate": "%s",
                                  "hour": %d,
                                  "visitorCount": 17
                                }
                                """.formatted(businessDate, businessHour)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(businessDate.toString()))
                .andExpect(jsonPath("$.hour").value(businessHour))
                .andExpect(jsonPath("$.visitorCount").value(17));

        mockMvc.perform(get("/stores/{storeId}/dashboard/summary", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(businessDate.toString()))
                .andExpect(jsonPath("$.totalSalesAmount").value(15000))
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.totalVisitors").value(17));

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
                .andExpect(jsonPath("$.totalVisitors").value(17))
                .andExpect(jsonPath("$.hourly.length()").value(24))
                .andExpect(jsonPath("$.hourly[%d].hour".formatted(businessHour)).value(businessHour))
                .andExpect(jsonPath("$.hourly[%d].visitorCount".formatted(businessHour)).value(17));

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
                        "/stores/{storeId}/sales/transactions/{transactionId}/cancel",
                        storeId,
                        transactionId
                )
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/stores/{storeId}/dashboard/summary", storeId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.totalVisitors").value(17));
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
