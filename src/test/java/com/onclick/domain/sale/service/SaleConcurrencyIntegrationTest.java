package com.onclick.domain.sale.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.repository.SaleTransactionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SaleConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleTransactionRepository saleTransactionRepository;

    @Test
    void concurrentDuplicateCreateAndCancelReturnOneStableTransaction() throws Exception {
        Fixture fixture = fixture();
        String clientTransactionId = "concurrent-" + UUID.randomUUID();
        LocalDateTime soldAt = LocalDateTime.parse("2026-07-13T12:15:00.123456789");
        SaleTransactionCreateRequest request = new SaleTransactionCreateRequest(
                clientTransactionId,
                soldAt,
                List.of(new SaleItemRequest(1, fixture.productId(), 1, 4_500))
        );

        List<SaleTransactionResult> createResults = runConcurrently(() ->
                saleService.createTransaction(fixture.jwt(), fixture.storeId(), request)
        );

        assertThat(createResults).extracting(SaleTransactionResult::created)
                .containsExactlyInAnyOrder(true, false);
        assertThat(createResults).extracting(result -> result.transaction().saleId())
                .containsOnly(createResults.getFirst().transaction().saleId());
        Long saleId = createResults.getFirst().transaction().saleId();
        assertThat(saleTransactionRepository.findByIdAndStoreId(saleId, fixture.storeId()))
                .isPresent()
                .get()
                .satisfies(transaction -> assertThat(transaction.getSoldAt())
                        .isEqualTo(soldAt.truncatedTo(ChronoUnit.MICROS)));

        List<SaleTransactionResponse> cancelResults = runConcurrently(() ->
                saleService.cancelTransaction(fixture.jwt(), fixture.storeId(), saleId)
        );

        assertThat(cancelResults).extracting(SaleTransactionResponse::cancelledAt)
                .doesNotContainNull()
                .containsOnly(cancelResults.getFirst().cancelledAt());
        assertThat(saleTransactionRepository.findByIdAndStoreId(saleId, fixture.storeId()))
                .isPresent()
                .get()
                .satisfies(transaction -> assertThat(transaction.getCancelledAt())
                        .isEqualTo(cancelResults.getFirst().cancelledAt()));
    }

    private Fixture fixture() throws Exception {
        String accountId = "concurrent-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "password123!";
        MvcResult signUp = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "password": "%s",
                                  "name": "동시성 테스트 사용자",
                                  "email": "%s@example.com",
                                  "storeName": "동시성 테스트 매장"
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
        String accessToken = body(login).required("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(accessToken);

        MvcResult product = mockMvc.perform(post("/stores/{storeId}/products", storeId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "동시성 테스트 상품",
                                  "price": 4500
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return new Fixture(storeId, body(product).required("id").asLong(), jwt);
    }

    private <T> List<T> runConcurrently(ThrowingSupplier<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var guardedTask = (java.util.concurrent.Callable<T>) () -> {
                ready.countDown();
                start.await();
                return task.get();
            };
            Future<T> first = executor.submit(guardedTask);
            Future<T> second = executor.submit(guardedTask);
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Fixture(long storeId, long productId, Jwt jwt) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
